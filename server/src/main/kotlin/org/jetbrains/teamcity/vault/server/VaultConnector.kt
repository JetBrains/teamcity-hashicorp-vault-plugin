package org.jetbrains.teamcity.vault.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ConcurrentHashSet
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.vault.VaultException
import org.springframework.vault.authentication.AppRoleAuthenticationOptions
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.config.ClientHttpRequestFactoryFactory
import org.springframework.vault.support.ClientOptions
import org.springframework.vault.support.SslConfiguration
import org.springframework.vault.support.VaultResponse
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class VaultConnector(dispatcher: EventDispatcher<BuildServerListener>) {
    init {
        dispatcher.addListener(object : BuildServerAdapter() {
            override fun buildFinished(build: SRunningBuild) {
                val info = myBuildsTokens.remove(build.buildId) ?: return
                if (info == LeasedWrappedTokenInfo.FAILED_TO_FETCH) return
                myPendingRemoval.add(info)
                if (revoke(info)) {
                    myPendingRemoval.remove(info)
                }
            }
        })
    }

    companion object {
        val LOG = Logger.getInstance(VaultConnector::class.java.name)!!

        /**
         * @return true if operation succeed
         */
        @JvmStatic fun revoke(info: LeasedWrappedTokenInfo): Boolean {
            val settings = info.connection
            try {
                val template = createRestTemplate(settings)
                // Login and retrieve server token
                val (token, accessor) = getRealToken(template, settings)

                template.withVaultToken(token)
                // Revoke agent token
                revokeAccessor(template, info.accessor)
                // Revoke server token
                revokeAccessor(template, accessor)
                return true
            } catch(e: Exception) {
                LOG.warnAndDebugDetails("Failed to revoke token", e)
            }
            return false
        }

        private fun getRealToken(template: RestTemplate, settings: VaultFeatureSettings): Pair<String, String> {
            val options = AppRoleAuthenticationOptions.builder()
                    .path("approle")
                    .roleId(settings.roleId)
                    .secretId(settings.secretId)
                    .build()

            val login = getAppRoleLogin(options.roleId, options.secretId.nullIfEmpty())
            try {
                val uri = template.uriTemplateHandler.expand("auth/{mount}/login", options.path)
                val response = template.postForObject(uri, login, VaultResponse::class.java)
                val auth = response.auth
                val token = auth["client_token"] as? String ?: throw VaultException("HashiCorp Vault hasn't returned token")
                val accessor = auth["accessor"] as? String ?: throw VaultException("HashiCorp Vault hasn't returned token accessor")
                return token to accessor
            } catch (e: HttpStatusCodeException) {
                throw ConnectionException("Cannot login using AppRole: ${getError(e)}", e)
            }
        }

        /**
         * @return true if operation succeed
         */
        private fun revokeAccessor(template: RestTemplate, accessor: String): Boolean {
            val entity = template.postForEntity("/auth/token/revoke-accessor", mapOf("accessor" to accessor), VaultResponse::class.java)
            return entity.statusCode == HttpStatus.NO_CONTENT
        }

        private fun getAppRoleLogin(roleId: String, secretId: String?): Map<String, String> {
            val login = HashMap<String, String>()
            login.put("role_id", roleId)
            if (secretId != null) {
                login.put("secret_id", secretId)
            }
            return login
        }

        private fun getError(e: HttpStatusCodeException): String {
            val contentType: MediaType?
            val body = e.responseBodyAsString
            try {
                contentType = e.responseHeaders?.contentType
            } catch(_: Exception) {
                return body
            }
            if (MediaType.APPLICATION_JSON.includes(contentType)) {
                val json = body
                try {
                    val map = Gson().fromJson(json, JsonObject::class.java)
                    if (map.has("errors")) {
                        return map.getAsJsonArray("errors").joinToString { it.asString }
                    }
                } catch (e: JsonParseException) {
                }
            }
            return body
        }

        @JvmStatic fun doRequestWrappedToken(settings: VaultFeatureSettings): Pair<String, String> {
            val options = AppRoleAuthenticationOptions.builder()
                    .path("approle")
                    .roleId(settings.roleId)
                    .secretId(settings.secretId)
                    .build()
            val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
            val factory = ClientHttpRequestFactoryFactory.create(ClientOptions(), SslConfiguration.NONE)!!

            val template = VaultTemplate(endpoint, factory, DummySessionManager()).withWrappedResponses("10m")

            val login = getAppRoleLogin(options.roleId, options.secretId.nullIfEmpty())

            try {
                val vaultResponse = template.write("auth/${options.path}/login", login)

                val wrap = vaultResponse.wrapInfo

                val token = wrap["token"] ?: throw VaultException("HashiCorp Vault hasn't returned wrapped token")
                val accessor = wrap["wrapped_accessor"] ?: throw VaultException("HashiCorp Vault hasn't returned wrapped token accessor")

                return token to accessor
            } catch (e: VaultException) {
                val cause = e.cause
                if (cause is HttpStatusCodeException) {
                    val err = getError(cause)
                    val prefix = "Cannot log in to HashiCorp Vault using AppRole credentials"
                    var message: String? = null
                    if (err.startsWith("failed to validate SecretID: ")) {
                        val suberror = err.removePrefix("failed to validate SecretID: ")
                        if (suberror.contains("invalid secret_id")) {
                            message = "$prefix, SecretID is incorrect or expired"
                        } else if (suberror.contains("failed to find secondary index for role_id")) {
                            message = "$prefix, RoleID is incorrect or there's no such role"
                        }
                    }
                    if (message == null) {
                        message = "$prefix: $err"
                    }
                    throw ConnectionException(message, cause)
                }
                throw e
            }
        }

    }

    // TODO: Support server restart
    private val myBuildsTokens: MutableMap<Long, LeasedWrappedTokenInfo> = ConcurrentHashMap()
    private val myPendingRemoval: MutableSet<LeasedWrappedTokenInfo> = ConcurrentHashSet()

    fun requestWrappedToken(build: SBuild, settings: VaultFeatureSettings): String {
        val info = myBuildsTokens[build.buildId]
        if (info != null) return info.wrapped

        try {
            val (token, accessor) = doRequestWrappedToken(settings)
            myBuildsTokens[build.buildId] = LeasedWrappedTokenInfo(token, accessor, settings)
            return token
        } catch(e: Exception) {
            myBuildsTokens[build.buildId] = LeasedWrappedTokenInfo.FAILED_TO_FETCH
            throw e
        }
    }

    class ConnectionException(message: String, cause: Throwable) : Exception(message, cause)
}

data class LeasedWrappedTokenInfo(val wrapped: String, val accessor: String, val connection: VaultFeatureSettings) {
    companion object {
        val FAILED_TO_FETCH = LeasedWrappedTokenInfo(VaultConstants.SPECIAL_FAILED_TO_FETCH, "", VaultFeatureSettings(mapOf()))
    }
}
