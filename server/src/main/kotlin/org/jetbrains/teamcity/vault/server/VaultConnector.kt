/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.server

import com.google.common.util.concurrent.Striped
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ConcurrentHashSet
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.RetryRestTemplate
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.vault.VaultException
import org.springframework.vault.authentication.AppRoleAuthenticationOptions
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultResponse
import org.springframework.web.client.HttpStatusCodeException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

class VaultConnector(dispatcher: EventDispatcher<BuildServerListener>, private val trustStoreProvider: SSLTrustStoreProvider) {
    init {
        dispatcher.addListener(object : BuildServerAdapter() {
            override fun buildFinished(build: SRunningBuild) {
                val infos = myBuildsTokens.remove(build.buildId) ?: return
                infos.values.forEach { info ->
                    if (info == LeasedWrappedTokenInfo.FAILED_TO_FETCH) return
                    myPendingRemoval.add(info)
                    if (revoke(info, trustStoreProvider)) {
                        myPendingRemoval.remove(info)
                    }
                }
            }
        })
    }

    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultConnector::class.java.name)!!

        /**
         * @return true if operation succeed
         */
        @JvmStatic fun revoke(info: LeasedWrappedTokenInfo, trustStoreProvider: SSLTrustStoreProvider): Boolean {
            val settings = info.connection
            try {
                val template = createRetryRestTemplate(settings, trustStoreProvider)
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

        /**
         * @return true if operation succeed
         */
        @JvmStatic
        fun revoke(info: LeasedTokenInfo, trustStoreProvider: SSLTrustStoreProvider): Boolean {
            val settings = info.connection
            try {
                val template = createRetryRestTemplate(settings, trustStoreProvider)
                // Login and retrieve server token
                template.withVaultToken(info.token)
                // Revoke token by accessor
                revokeAccessor(template, info.accessor)
                return true
            } catch (e: Exception) {
                LOG.warnAndDebugDetails("Failed to revoke token", e)
            }
            return false
        }

        private fun getRealToken(template: RetryRestTemplate, settings: VaultFeatureSettings): Pair<String, String> {
            val options = AppRoleAuthenticationOptions.builder()
                    .path(settings.getNormalizedEndpoint())
                    .roleId(settings.roleId)
                    .secretId(settings.secretId)
                    .build()

            val login = getAppRoleLogin(settings)
            try {
                val uri = template.uriTemplateHandler.expand("auth/{mount}/login", options.path)
                val response = template.postForObject(uri, login, VaultResponse::class.java)
                        ?: throw VaultException("HashiCorp Vault hasn't returned anything for '$uri'")
                val auth = response.auth
                val token = auth["client_token"] as? String ?: throw VaultException("HashiCorp Vault hasn't returned token")
                val accessor = auth["accessor"] as? String ?: throw VaultException("HashiCorp Vault hasn't returned token accessor")
                return token to accessor
            } catch (e: HttpStatusCodeException) {
                throw getReadableException(e, settings)
            }
        }

        /**
         * @return true if operation succeed
         */
        private fun revokeAccessor(template: RetryRestTemplate, accessor: String): Boolean {
            val entity = template.postForEntity("/auth/token/revoke-accessor", mapOf("accessor" to accessor), VaultResponse::class.java)
            return entity.statusCode == HttpStatus.NO_CONTENT
        }

        private fun getAppRoleLogin(settings: VaultFeatureSettings): Map<String, String> {
            val login = HashMap<String, String>(2)
            login["role_id"] = settings.roleId
            settings.secretId.nullIfEmpty()?.let {
                login["secret_id"] = it
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

        private fun getReadableException(cause: HttpStatusCodeException, settings: VaultFeatureSettings): ConnectionException {
            val err = getError(cause)
            val prefix = "Cannot log in to HashiCorp Vault using AppRole credentials"
            val message: String
            message = setOf("failed to validate credentials: ", "failed to validate SecretID: ")
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
            return ConnectionException(message.replace(settings.secretId, "*******"), cause)
        }

        @JvmStatic fun doRequestWrappedToken(settings: VaultFeatureSettings, trustStoreProvider: SSLTrustStoreProvider): Pair<String, String> {
            val options = AppRoleAuthenticationOptions.builder()
                    .path(settings.getNormalizedEndpoint())
                    .roleId(settings.roleId)
                    .secretId(settings.secretId)
                    .build()
            val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
            val factory = createClientHttpRequestFactory(trustStoreProvider)

            val template = VaultTemplate(endpoint, factory, DummySessionManager()).withWrappedResponses("10m")

            val login = getAppRoleLogin(settings)

            try {
                val path = "auth/${options.path}/login"
                val vaultResponse = template.write(path, login)
                        ?: throw VaultException("HashiCorp Vault hasn't returned anything from POST to '$path'")

                val wrap = vaultResponse.wrapInfo ?: throw VaultException("HashiCorp Vault hasn't returned 'wrap_info' for POST to '$path'")

                val token = wrap["token"] ?: throw VaultException("HashiCorp Vault hasn't returned wrapped token")
                val accessor = wrap["wrapped_accessor"] ?: throw VaultException("HashiCorp Vault hasn't returned wrapped token accessor")

                return token to accessor
            } catch (e: VaultException) {
                val cause = e.cause
                if (cause is HttpStatusCodeException) {
                    throw getReadableException(cause, settings)
                }
                throw e
            }
        }

        @JvmStatic
        fun doRequestToken(settings: VaultFeatureSettings, trustStoreProvider: SSLTrustStoreProvider): Pair<String, String> {
            val options = AppRoleAuthenticationOptions.builder()
                    .path(settings.getNormalizedEndpoint())
                    .roleId(settings.roleId)
                    .secretId(settings.secretId)
                    .build()
            val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
            val factory = createClientHttpRequestFactory(trustStoreProvider)

            val template = VaultTemplate(endpoint, factory, DummySessionManager())

            val login = getAppRoleLogin(settings)

            try {
                val vaultResponse = template.write("auth/${options.path}/login", login)
                val auth = vaultResponse.auth

                val token = auth["client_token"] as? String ?: throw VaultException("HashiCorp Vault hasn't returned token")
                val accessor = auth["accessor"] as? String ?: throw VaultException("HashiCorp Vault hasn't returned token accessor")
                return token to accessor
            } catch (e: VaultException) {
                val cause = e.cause
                if (cause is HttpStatusCodeException) {
                    throw getReadableException(cause, settings)
                }
                throw e
            }
        }
    }

    // TODO: Support server restart
    private val myBuildsTokens: MutableMap<Long, MutableMap<String,LeasedWrappedTokenInfo>> = ConcurrentHashMap()
    private val myPendingRemoval: MutableSet<LeasedWrappedTokenInfo> = ConcurrentHashSet()
    private val myLocks = Striped.lazyWeakLock(64)

    fun requestWrappedToken(build: SBuild, settings: VaultFeatureSettings): String {
        val infos = myBuildsTokens.getOrDefault(build.buildId,ConcurrentHashMap())
        val info = infos[settings.namespace]
        if(info != null) return info.wrapped

        myLocks.get(build.buildId).withLock {
            @Suppress("NAME_SHADOWING")
            val infos = myBuildsTokens.getOrDefault(build.buildId,ConcurrentHashMap())
            @Suppress("NAME_SHADOWING")
            val info = infos[settings.namespace]
            if(info != null) return info.wrapped
            try {
                val (token, accessor) = doRequestWrappedToken(settings, trustStoreProvider)
                infos[settings.namespace] = LeasedWrappedTokenInfo(token, accessor, settings);
                myBuildsTokens[build.buildId] = infos;
                return token
            } catch (e: Exception) {
                infos[settings.namespace] = LeasedWrappedTokenInfo.FAILED_TO_FETCH
                myBuildsTokens[build.buildId] = infos;
                throw e
            }
        }
    }

    fun tryRequestToken(settings: VaultFeatureSettings): LeasedTokenInfo {
        val (token, accessor) = doRequestToken(settings, trustStoreProvider)
        return LeasedTokenInfo(token, accessor, settings)
    }

    class ConnectionException(message: String, cause: Throwable) : Exception(message, cause)
}

data class LeasedWrappedTokenInfo(val wrapped: String, val accessor: String, val connection: VaultFeatureSettings) {
    companion object {
        val FAILED_TO_FETCH = LeasedWrappedTokenInfo(VaultConstants.SPECIAL_FAILED_TO_FETCH, "", VaultFeatureSettings(mapOf()))
    }
}

data class LeasedTokenInfo(val token: String, val accessor: String, val connection: VaultFeatureSettings)
