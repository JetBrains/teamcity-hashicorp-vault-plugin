package org.jetbrains.teamcity.vault.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.StringUtil
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.createRestTemplate
import org.jetbrains.teamcity.vault.support.VaultResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.vault.VaultException
import org.springframework.vault.authentication.AppRoleAuthenticationOptions
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.config.ClientHttpRequestFactoryFactory
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.ClientOptions
import org.springframework.vault.support.SslConfiguration
import org.springframework.vault.support.VaultToken
import org.springframework.web.client.HttpStatusCodeException
import java.net.URI

class VaultConnector(dispatcher: EventDispatcher<BuildServerListener>) {
    init {
        dispatcher.addListener(object : BuildServerAdapter() {
            override fun buildFinished(build: SRunningBuild) {
                val info = myBuildsTokens.remove(build.buildId) ?: return
                if (info == LeasedWrappedTokenInfo.FAILED_TO_FETCH) return
                myPendingRemoval.add(info)
                revoke(info)
            }
        })
    }

    private fun revoke(info: LeasedWrappedTokenInfo) {
        val settings = info.connection

        val endpoint = VaultEndpoint.from(URI.create(settings.url))
        val factory = ClientHttpRequestFactoryFactory.create(ClientOptions(), SslConfiguration.NONE)
        try {
            // TODO: What token to use here?
            VaultTemplate(endpoint, factory, SimpleSessionManager({ VaultToken.of("") })).write("/auth/token/revoke-accessor", mapOf("accessor" to info.accessor))
            myPendingRemoval.remove(info)
        } catch(e: Exception) {
        }
    }

    // TODO: Support server restart
    private val myBuildsTokens: MutableMap<Long, LeasedWrappedTokenInfo> = HashMap()
    private val myPendingRemoval: MutableSet<LeasedWrappedTokenInfo> = HashSet()

    fun requestWrappedToken(build: SBuild, settings: VaultFeatureSettings): String {
        val info = myBuildsTokens[build.buildId]
        if (info != null) return info.wrapped

        val options = AppRoleAuthenticationOptions.builder()
                .path("approle")
                .roleId(settings.roleId)
                .secretId(settings.secretId)
                .build()
        val endpoint = VaultEndpoint.from(URI.create(settings.url))
        val factory = ClientHttpRequestFactoryFactory.create(ClientOptions(), SslConfiguration.NONE) // HttpComponents.usingHttpComponents(options, sslConfiguration)
        val template = createRestTemplate(endpoint, factory)

        val login = getAppRoleLogin(options.roleId, options.secretId.nullIfEmpty())

        try {
            val headers = HttpHeaders()
            headers["X-Vault-Wrap-TTL"] = "10m"
            val uri = template.uriTemplateHandler.expand("auth/{mount}/login", options.path)
            val request = RequestEntity(login, headers, HttpMethod.POST, uri)

            val response = template.exchange(request, VaultResponse::class.java)

            val vaultResponse = response.body

            val wrap = vaultResponse.wrapInfo

            val token = wrap["token"] ?: throw VaultException("Vault hasn't returned wrapped token")
            val accessor = wrap["wrapped_accessor"] ?: throw VaultException("Vault hasn't returned wrapped token accessor")

            myBuildsTokens[build.buildId] = LeasedWrappedTokenInfo(token, accessor, settings)

            return token
        } catch (e: HttpStatusCodeException) {
            val err = getError(e)
            var message: String? = null
            if (err.startsWith("failed to validate SecretID: ")) {
                val suberror = err.removePrefix("failed to validate SecretID: ")
                if (suberror.contains("invalid secret_id")) {
                    message = "Cannot login using AppRole, seems SecretID is incorrect or expired."
                } else if (suberror.contains("failed to find secondary index for role_id")) {
                    message = "Cannot login using AppRole, seems RoleID is incorrect or role was deleted."
                }
            }
            if (message == null) {
                message = "Cannot login using AppRole: $err"
            }
//            if (true) {
//                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection", message))
//            } else {
            myBuildsTokens[build.buildId] = LeasedWrappedTokenInfo.FAILED_TO_FETCH;
            throw ConnectionException(message)
//            }
        }
    }

    class ConnectionException(message: String) : Exception(message)


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
}

private fun String?.nullIfEmpty(): String? {
    return StringUtil.nullIfEmpty(this)
}

data class LeasedWrappedTokenInfo(val wrapped: String, val accessor: String, val connection: VaultFeatureSettings) {
    companion object {
        val FAILED_TO_FETCH = LeasedWrappedTokenInfo(VaultConstants.SPECIAL_FAILED_TO_FETCH, "", VaultFeatureSettings(mapOf()))
    }
}
