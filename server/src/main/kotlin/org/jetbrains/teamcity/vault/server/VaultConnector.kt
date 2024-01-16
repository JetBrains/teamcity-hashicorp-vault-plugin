
package org.jetbrains.teamcity.vault.server

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.*
import org.jetbrains.teamcity.vault.retrier.Retrier
import org.jetbrains.teamcity.vault.retrier.SpringHttpErrorCodeListener
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.VaultResponses
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.http.HttpStatus
import org.springframework.vault.VaultException
import org.springframework.vault.authentication.*
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.util.concurrent.TimeUnit

class VaultConnector(private val trustStoreProvider: SSLTrustStoreProvider) {
    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultConnector::class.java.name)
        private val retrier = Retrier<VaultResponse>(listOf(SpringHttpErrorCodeListener()))

        /**
         * @return true if operation succeed
         */
        @JvmStatic
        fun revoke(info: LeasedWrappedTokenInfo, trustStoreProvider: SSLTrustStoreProvider, catch: Boolean = true): Boolean {
            val settings = info.connection
            when (settings.auth.method) {
                AuthMethod.APPROLE,
                AuthMethod.LDAP -> {
                    try {
                        val template = createRestTemplate(settings, trustStoreProvider)
                        // Login and retrieve server token
                        val (token, _) = performLogin(template, settings, extractTokenAndAccessor)

                        template.withVaultToken(token)
                        try {
                            // Revoke agent token
                            revokeAccessor(template, info.accessor, info.connection)
                        } finally {
                            // Revoke server token we just obtained
                            revokeSelf(template)
                        }
                        return true
                    } catch (e: Exception) {
                        LOG.warnAndDebugDetails("Failed to revoke token", e)
                        if (!catch) throw e
                    }
                    return false
                }
            }
        }

        /**
         * @return true if operation succeed
         */
        @JvmStatic
        fun revoke(info: LeasedTokenInfo, trustStoreProvider: SSLTrustStoreProvider): Boolean {
            val settings = info.connection
            try {
                val template = createRestTemplate(settings, trustStoreProvider)
                template.withVaultToken(info.token)
                // Revoke token
                return revokeSelf(template)
            } catch (e: Exception) {
                LOG.warnAndDebugDetails("Failed to revoke token", e)
            }
            return false
        }

        /**
         * @return true if operation succeed or it doesn't makes sense to try again later
         */
        private fun revokeAccessor(template: RestTemplate, accessor: String, settings: VaultFeatureSettings): Boolean {
            template.errorHandler = object : DefaultResponseErrorHandler() {
                override fun hasError(statusCode: HttpStatus?): Boolean {
                    if (statusCode == HttpStatus.FORBIDDEN || statusCode == HttpStatus.BAD_REQUEST) return false
                    return super.hasError(statusCode)
                }
            }
            val entity = template.postForEntity("/auth/token/revoke-accessor", mapOf("accessor" to accessor), ObjectNode::class.java)
            if (entity.statusCode == HttpStatus.NO_CONTENT) {
                // OK
                return true
            }
            val error = VaultResponses.getError(entity.body)
            val suffix = error?.replace('\n', ' ')?.let { ". Error message: $it" } ?: ""
            if (entity.statusCode == HttpStatus.FORBIDDEN) {
                when (settings.auth.method) {
                    AuthMethod.APPROLE -> LOG.warn("Failed to revoke token via accessor '$accessor': access denied, give approle '${(settings.auth as Auth.AppRoleAuthServer).roleId}' 'update' access to '/auth/token/revoke-accessor'$suffix")
                    AuthMethod.LDAP -> LOG.warn("Failed to revoke token via accessor '$accessor': access denied, give LDAP role access to '/auth/token/revoke-accessor'$suffix")
                }
                return true
            }
            if (entity.statusCode == HttpStatus.BAD_REQUEST) {
                val message = "Failed to revoke token via accessor '$accessor': server returned 400, most probably token was already revoked$suffix"
                if (error?.contains("invalid accessor") == true) {
                    LOG.info(message)
                } else {
                    LOG.warn(message)
                }
                return true
            }
            LOG.warn("Unexpected response from Hashicorp Vault during token accessor revocation: ${entity.statusCodeValue}")
            return false
        }

        /**
         * @return true if operation succeed
         */
        private fun revokeSelf(template: RestTemplate): Boolean {
            val backoffs = intArrayOf(1, 3, 6, 0) // last is not used
            var e: Exception? = null
            for (backoff in backoffs) {
                try {
                    template.postForObject("auth/token/revoke-self", null, ObjectNode::class.java)
                    return true
                } catch (re: RuntimeException) {
                    e = re
                    try {
                        TimeUnit.SECONDS.sleep(backoff.toLong())
                    } catch (ignored: InterruptedException) {
                    }
                }
            }
            var message: String? = "Cannot revoke HashiCorp Vault token: "
            if (e is HttpStatusCodeException) {
                message += VaultResponses.getError((e as HttpStatusCodeException?)!!)
            } else {
                message += e?.message
            }
            LOG.warn(message, e)
            return false
        }

        private fun getAppRoleLogin(appRoleAuth: Auth.AppRoleAuthServer): Map<String, String> {
            val login = HashMap<String, String>(2)
            login["role_id"] = appRoleAuth.roleId
            appRoleAuth.secretId.nullIfEmpty()?.let {
                login["secret_id"] = it
            }
            return login
        }

        private fun getReadableException(cause: HttpStatusCodeException, method: AuthMethod, replacer: ((String) -> String)? = null): ConnectionException {
            val err = VaultResponses.getError(cause)
            val prefix = "Cannot log in to HashiCorp Vault using ${method.name} method"
            val message: String = setOf("failed to validate credentials: ", "failed to validate SecretID: ")
                .find { err.startsWith(it) }
                ?.let {
                    val suberror = err.removePrefix(it)
                    if (suberror.contains("invalid secret_id")) {
                        return@let "$prefix, SecretID is incorrect or expired"
                    } else if (suberror.contains("failed to find secondary index for role_id")) {
                        return@let "$prefix, RoleID is incorrect or there's no such role"
                    }
                    return@let null
                } ?: "$prefix: $err"
            return ConnectionException(if (replacer != null) replacer(message) else message, cause)
        }

        @JvmStatic
        fun doRequestWrappedToken(settings: VaultFeatureSettings, trustStoreProvider: SSLTrustStoreProvider): Pair<String, String> {
            val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
            val factory = createClientHttpRequestFactory(trustStoreProvider)

            val template = VaultTemplate(endpoint, settings.vaultNamespace, factory, null)
            template.wrapResponses(TeamCityProperties.getProperty("teamcity.vault.xVaultWrapTTL", "10m"))

            return performLogin(template.defaultTemplate, settings, extractWrappedTokenAndAccessor)
        }

        @JvmStatic
        fun doRequestToken(settings: VaultFeatureSettings, trustStoreProvider: SSLTrustStoreProvider): Pair<String, String> {
            val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
            val factory = createClientHttpRequestFactory(trustStoreProvider)

            val template = VaultTemplate(endpoint, settings.vaultNamespace, factory, null)

            return performLogin(template.defaultTemplate, settings, extractTokenAndAccessor)
        }

        private fun performLogin(template: RestTemplate, settings: VaultFeatureSettings, extractor: (VaultResponse) -> Pair<String, String>): Pair<String, String> {
            when (val auth = settings.auth) {
                is Auth.AppRoleAuthServer -> {
                    val options = AppRoleAuthenticationOptions.builder()
                        .path(auth.getNormalizedEndpoint())
                        .roleId(auth.roleId)
                        .secretId(auth.secretId)
                        .build()

                    val body = getAppRoleLogin(auth)
                    val path = "auth/${options.path}/login"

                    return performLoginRequest(template, auth.method, path, body, auth.secretId, extractor)
                }

                is Auth.LdapServer -> {
                    val options = LdapAuthenticationOptions.builder()
                        .username(auth.username)
                        .password(auth.password)
                        .path(auth.path)
                        .build()

                    val path = "auth/${options.path}/login/${options.username}"
                    val body = mapOf("password" to auth.password)

                    return performLoginRequest(template, auth.method, path, body, auth.password, extractor)
                }

                else -> error("Unsupported auth method: ${settings.auth.method}, class: ${settings.auth::class.qualifiedName}")
            }
        }

        private val extractTokenAndAccessor: (VaultResponse) -> Pair<String, String> = { response: VaultResponse ->
            val auth = response.auth

            val token = auth["client_token"] as? String
                ?: throw VaultException("HashiCorp Vault hasn't returned token")
            val accessor = auth["accessor"] as? String
                ?: throw VaultException("HashiCorp Vault hasn't returned token accessor")
            token to accessor
        }

        private val extractWrappedTokenAndAccessor: (VaultResponse) -> Pair<String, String> = { response: VaultResponse ->
            val wrap = response.wrapInfo
                ?: throw VaultException("HashiCorp Vault hasn't returned 'wrap_info'")

            val token = wrap["token"]
                ?: throw VaultException("HashiCorp Vault hasn't returned wrapped token")
            val accessor = wrap["wrapped_accessor"]
                ?: throw VaultException("HashiCorp Vault hasn't returned wrapped token accessor")

            token to accessor
        }

        fun performLoginRequest(
            template: RestTemplate,
            method: AuthMethod,
            path: String,
            body: Map<String, String>,
            maskingValue: String,
            extractor: (VaultResponse) -> Pair<String, String>
        ): Pair<String, String> =
            try {
                val errorMessage = "HashiCorp Vault hasn't returned anything from POST to '$path'"
                val vaultResponse = retrier.run {
                    template.write(path, body)
                        ?: throw VaultException(errorMessage)
                } ?: throw VaultException(errorMessage)
                extractor(vaultResponse)
            } catch (e: VaultException) {
                val cause = e.cause
                if (cause is HttpStatusCodeException) {
                    throw getReadableException(cause, method) { it.replace(maskingValue, "*******") }
                }
                throw e
            }
    }


    @Suppress("UnstableApiUsage")
    fun requestWrappedToken(settings: VaultFeatureSettings): String {
        try {
            val (token, _) = doRequestWrappedToken(settings, trustStoreProvider)
            return token
        } catch (e: Exception) {
            throw e
        }
    }

    fun tryRequestToken(settings: VaultFeatureSettings): LeasedTokenInfo {
        return when (settings.auth.method) {
            AuthMethod.APPROLE,
            AuthMethod.LDAP -> {
                val (token, accessor) = doRequestToken(settings, trustStoreProvider)
                LeasedTokenInfo(token, accessor, settings)
            }
        }
    }

    class ConnectionException(message: String, cause: Throwable) : Exception(message, cause)
}

data class LeasedWrappedTokenInfo(val wrapped: String, val accessor: String, val connection: VaultFeatureSettings) {
    companion object {
        val FAILED_TO_FETCH = LeasedWrappedTokenInfo(VaultConstants.SPECIAL_FAILED_TO_FETCH, "", VaultFeatureSettings(mapOf()))
    }
}

data class LeasedTokenInfo(val token: String, val accessor: String, val connection: VaultFeatureSettings)