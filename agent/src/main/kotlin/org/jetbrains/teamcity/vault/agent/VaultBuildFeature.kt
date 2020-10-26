/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
package org.jetbrains.teamcity.vault.agent

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.LifecycleAwareSessionManager
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler
import org.springframework.vault.authentication.*
import org.springframework.vault.support.VaultToken
import org.springframework.web.client.RestTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VaultBuildFeature(dispatcher: EventDispatcher<AgentLifeCycleListener>,
                        private val trustStoreProvider: SSLTrustStoreProvider,
                        private val myVaultParametersResolver: VaultParametersResolver) : AgentLifeCycleAdapter() {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultBuildFeature::class.java.name)!!
    }

    init {
        if (isJava8OrNewer()) {
            dispatcher.addListener(this)
            LOG.info("HashiCorp Vault intergration enabled")
        } else {
            dispatcher.addListener(FailBuildListener())
            LOG.warn("HashiCorp Vault integration disabled: agent should be running under Java 1.8 or newer")
        }
    }

    private val sessions = ConcurrentHashMap<Long, LifecycleAwareSessionManager>()
    private val scheduler: TaskScheduler = ConcurrentTaskScheduler()

    override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
        agent.configuration.addConfigurationParameter(VaultConstants.FEATURE_SUPPORTED_AGENT_PARAMETER, "true")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        val parameters = runningBuild.sharedConfigParameters
        val namespaces = parameters.keys
                .filter { isUrlParameter(it) }
                .mapNotNull {
                    it
                            .removePrefix(VaultConstants.PARAMETER_PREFIX)
                            .removeSuffix(VaultConstants.URL_PROPERTY_SUFFIX)
                            .removePrefix(".")
                }.toSet()

        namespaces.forEach { namespace ->
            // namespace is either empty string or something like 'id'
            val settings = VaultFeatureSettings.fromSharedParameters(parameters, namespace)
            val url = parameters[getVaultParameterName(namespace, VaultConstants.URL_PROPERTY_SUFFIX)]

            if (settings.url.isBlank()) {
                return@forEach
            }
            val logger = runningBuild.buildLogger
            logger.activity("HashiCorp Vault" + if (isDefault(namespace)) "" else " ('$namespace' namespace)", VaultConstants.FeatureSettings.FEATURE_TYPE) {
                val token: String
                val timeout = (parameters[getVaultParameterName(namespace, VaultConstants.TOKEN_REFRESH_TIMEOUT_PROPERTY_SUFFIX)])?.toLongOrNull()
                        ?: 15
                try {
                    val template = createRestTemplate(settings, trustStoreProvider)
                    val authentication: ClientAuthentication = when (settings.auth.method) {
                        AuthMethod.APPROLE -> {
                            val wrapped = (settings.auth as Auth.AppRoleAuthAgent).wrappedToken
                            if (wrapped.isEmpty()) {
                                logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped HashiCorp Vault token for url $url not found", null)
                                return@activity
                            }
                            if (VaultConstants.SPECIAL_VALUES.contains(wrapped)) {
                                logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped HashiCorp Vault token value for url $url is incorrect, seems there was error fetching token on TeamCity server side", null)
                                return@activity
                            }
                            createAppRoleAuthentication(wrapped, template)
                        }
                        AuthMethod.AWS_IAM -> {
                            createAwsIamAuthentication(template)
                        }
                    }
                    val sessionManager = LifecycleAwareSessionManager(authentication, scheduler, template,
                            LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(timeout, TimeUnit.SECONDS), logger)
                    sessions[runningBuild.buildId] = sessionManager
                    token = sessionManager.sessionToken.token
                } catch (e: Exception) {
                    val errorPrefix = when (settings.auth.method) {
                        AuthMethod.APPROLE -> "Failed to unwrap HashiCorp Vault token: "
                        AuthMethod.AWS_IAM -> "Failed to get HashiCorp Vault token using AWS IAM auth: "
                    }
                    logger.error(errorPrefix + e.message)
                    logger.exception(e)
                    return@activity
                }

                logger.message("HashiCorp Vault token successfully fetched")

                runningBuild.passwordReplacer.addPassword(token)

                if (isShouldSetEnvParameters(parameters, namespace)) {
                    val envPrefix = getEnvPrefix(namespace)

                    val tokenParameter = envPrefix + VaultConstants.AgentEnvironment.VAULT_TOKEN
                    val addrParameter = envPrefix + VaultConstants.AgentEnvironment.VAULT_ADDR

                    runningBuild.addSharedEnvironmentVariable(tokenParameter, token)
                    runningBuild.addSharedEnvironmentVariable(addrParameter, settings.url)

                    logger.message("$addrParameter and $tokenParameter environment variables were added")
                }

                myVaultParametersResolver.resolve(runningBuild, settings, token)
            }
        }
    }

    private fun createAwsIamAuthentication(restTemplate: RestTemplate): AwsIamAuthentication {
        val options = AwsIamAuthenticationOptions.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.getInstance()).build()

        return AwsIamAuthentication(options, restTemplate)
    }

    private fun createAppRoleAuthentication(wrapped: String, restTemplate: RestTemplate): CubbyholeAuthentication {
        val options = CubbyholeAuthenticationOptions.builder()
                .wrapped()
                .initialToken(VaultToken.of(wrapped))
                .build()
        return CubbyholeAuthentication(options, restTemplate)
    }

    override fun beforeBuildFinish(build: AgentRunningBuild, buildStatus: BuildFinishedStatus) {
        // Stop renewing token, revoke token
        val manager = sessions[build.buildId] ?: return
        manager.destroy()
    }

    override fun buildFinished(build: AgentRunningBuild, buildStatus: BuildFinishedStatus) {
        sessions.remove(build.buildId)
    }
}
