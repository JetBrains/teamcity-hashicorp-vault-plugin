/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.agent

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.http.SimpleCredentials
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.HTTPRequestBuilder
import jetbrains.buildServer.util.HTTPRequestBuilder.DelegatingRequestHandler
import jetbrains.buildServer.util.http.HttpMethod
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.LifecycleAwareSessionManager
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler
import org.springframework.vault.authentication.*
import org.springframework.vault.support.VaultToken
import org.springframework.web.client.RestTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VaultBuildFeature(
    dispatcher: EventDispatcher<AgentLifeCycleListener>,
    private val trustStoreProvider: SSLTrustStoreProvider,
    private val myVaultParametersResolver: VaultParametersResolver,
    private val sslTrustStoreProvider: SSLTrustStoreProvider
) : AgentLifeCycleAdapter(), PositionAware {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultBuildFeature::class.java.name)
    }

    init {
        if (isJava8OrNewer()) {
            dispatcher.addListener(this)
            LOG.info("HashiCorp Vault integration enabled")
        } else {
            dispatcher.addListener(FailBuildListener())
            LOG.warn("HashiCorp Vault integration disabled: agent should be running under Java 1.8 or newer")
        }
    }

    private val sessions = ConcurrentHashMap<Long, LifecycleAwareSessionManager>()
    private val scheduler: TaskScheduler = ConcurrentTaskScheduler()
    private val objectMapper by lazy {
        jacksonObjectMapper()
    }

    override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
        agent.configuration.addConfigurationParameter(VaultConstants.FEATURE_SUPPORTED_AGENT_PARAMETER, "true")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        runningBuild.buildLogger.activity("HashiCorp Vault", VaultConstants.FeatureSettings.FEATURE_TYPE) {
            fetchLegacyParameters(runningBuild)
            fetchParameters(runningBuild)
        }
    }

    private fun fetchParameters(runningBuild: AgentRunningBuild) {
        val runningBuildImpl = runningBuild as AgentRunningBuildEx
        val parameters = runningBuild.sharedConfigParameters + runningBuild.sharedBuildParameters.allParameters
        parameters
            .map {
                it.key to runningBuildImpl.getParameterControlDescription(it.key)
            }
            .filter { (_, controlDescription) ->
                controlDescription?.parameterTypeArguments?.get("remoteType") == VaultConstants.PARAMETER_TYPE
            }
            .mapNotNull { (parameterKey, controlDescription) ->
                try {
                    VaultParameter(parameterKey, VaultParameterSettings(controlDescription!!.parameterTypeArguments))
                } catch (e: Throwable) {
                    LOG.warnAndDebugDetails("Failed to parse parameter ${parameterKey} secret object", e)
                    null
                }
            }
            // Group parameters by their namespace, as those will have the same associated connection
            .groupBy {
                it.vaultParameterSettings.getNamespace()
            }.mapNotNull {
                val vaultFeatureSettings = resolveParam(it.key, runningBuild)
                if (vaultFeatureSettings == null) {
                    null
                } else {
                    vaultFeatureSettings to it.value
                }
            }.forEach { (vaultFeatureSettings, vaultParameters) ->
                runningBuild.buildLogger.message("Resolving parameters $vaultParameters" + if (isDefault(vaultFeatureSettings.namespace)) "" else "for namespace ${vaultFeatureSettings.namespace}")
                val token = resolveToken(parameters, vaultFeatureSettings, runningBuild)

                if (token != null) {
                    myVaultParametersResolver.resolve(runningBuild, vaultFeatureSettings, vaultParameters, token)
                }
            }
    }

    private fun resolveParam(namespace: String, build: AgentRunningBuildEx): VaultFeatureSettings? {
        return try {
            val configuration = build.agentConfiguration as BuildAgentConfigurationEx
            val requestBuilder = HTTPRequestBuilder("${configuration.serverUrl}/app/${VaultConstants.ControllerSettings.URL}/${VaultConstants.ControllerSettings.WRAP_TOKEN_PATH}")
                .withMethod(HttpMethod.GET)
                .addParameters(
                    Pair("buildId", build.buildId.toString()),
                    Pair("namespace", namespace)
                )
                .withCredentials(SimpleCredentials(build.accessUser, build.accessCode))
                .withTimeout(configuration.serverConnectionTimeout * 1000)
                .allowNonSecureConnection(true)
                .withTrustStore(sslTrustStoreProvider.trustStore)

            if (configuration.serverProxyHost != null) {
                requestBuilder
                    .withProxyHost(URIBuilder(configuration.getServerProxyHost()).setPort(configuration.getServerProxyPort()).build());
                if (configuration.serverProxyCredentials != null) {
                    requestBuilder
                        .withProxyCredentials(configuration.serverProxyCredentials!!);
                }
            }

            val response = DelegatingRequestHandler()
                .doSyncRequest(requestBuilder.build())
            val contentStream = response.contentStream

            if (response.statusCode != 200) {
                val errorMessage =
                    "Got a ${response.statusCode} status code while fetching the token for hashicorp vault's namespace $namespace. With the error message: ${response.bodyAsString}"
                LOG.error(errorMessage)
                failBuild(build, namespace, errorMessage)
                return null
            }

            if (contentStream == null) {
                val errorMessage = "Got an empty response while fetching the token for hashicorp vault's namespace $namespace"
                LOG.error(errorMessage)
                failBuild(build, namespace, errorMessage)
                return null
            }

            val featureSettingsParams = objectMapper.readValue<Map<String, String>>(contentStream)

            VaultFeatureSettings.getAgentFeatureFromProperties(featureSettingsParams)
        } catch (e: Throwable) {
            val errorMessage = "Failed to fetch token for hashicorp vault's namesapce $namespace"
            LOG.error(errorMessage, e)
            failBuild(build, namespace, errorMessage)
            null
        }
    }

    private fun failBuild(build: AgentRunningBuildEx, namespace: String, errorMessage: String) {
        build.buildLogger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${namespace}_A", "VaultConnection", errorMessage))
    }

    private fun fetchLegacyParameters(runningBuild: AgentRunningBuild) {
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
            val settings = VaultFeatureSettings.getAgentFeatureFromSharedParameters(parameters, namespace)
            val token = resolveToken(parameters, settings, runningBuild)

            if (token != null) {
                myVaultParametersResolver.resolve(runningBuild, settings, token)
            }
        }
    }

    private fun resolveToken(
        parameters: Map<String, String>,
        settings: VaultFeatureSettings,
        runningBuild: AgentRunningBuild
    ): String? {
        if (settings.url.isBlank()) {
            return null
        }
        val namespace = settings.namespace
        val logger = runningBuild.buildLogger
        val url = settings.url
        val token: String
        val timeoutSeconds = (parameters[getVaultParameterName(namespace, VaultConstants.TOKEN_REFRESH_TIMEOUT_PROPERTY_SUFFIX)])?.toLongOrNull()
            ?: 15
        try {
            val template = createRestTemplate(settings, trustStoreProvider)
            val authentication: ClientAuthentication = when (settings.auth.method) {
                AuthMethod.APPROLE,
                AuthMethod.LDAP -> {
                    val wrapped = when (val auth = settings.auth) {
                        is Auth.AppRoleAuthAgent -> auth.wrappedToken
                        is Auth.LdapAgent -> auth.wrappedToken
                        else -> error("Unsupported auth method: ${settings.auth.method}, class: ${settings.auth::class.qualifiedName}")
                    }
                    if (wrapped.isBlank()) {
                        logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped HashiCorp Vault token for url $url not found", null)
                        return null
                    }
                    if (VaultConstants.SPECIAL_VALUES.contains(wrapped)) {
                        logger.internalError(
                            VaultConstants.FeatureSettings.FEATURE_TYPE,
                            "Wrapped HashiCorp Vault token value for url $url is incorrect, seems there was error fetching token on TeamCity server side",
                            null
                        )
                        return null
                    }
                    createCubbyholeAuthentication(wrapped, template)
                }

                AuthMethod.AWS_IAM -> {
                    createAwsIamAuthentication(template)
                }
            }
            val sessionManager = LifecycleAwareSessionManager(
                authentication, scheduler, template,
                LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(timeoutSeconds, TimeUnit.SECONDS), logger
            )
            sessions[runningBuild.buildId] = sessionManager
            token = sessionManager.sessionToken.token
        } catch (e: Exception) {
            val errorPrefix = when (settings.auth.method) {
                AuthMethod.APPROLE -> "Failed to unwrap HashiCorp Vault token"
                AuthMethod.AWS_IAM -> "Failed to get HashiCorp Vault token using AWS IAM auth"
                AuthMethod.LDAP -> "Failed to get HashiCorp Vault token using LDAP"
            }
            if (settings.failOnError) {
                logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, errorPrefix + ": " + e.message, e)
                logger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${runningBuild.buildTypeId}_${settings.namespace}_A", "VaultConnection", errorPrefix))
                runningBuild.stopBuild(errorPrefix)
            } else {
                logger.error(errorPrefix + ": " + e.message)
                logger.exception(e)
            }
            return null
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

        return token
    }

    private fun createAwsIamAuthentication(restTemplate: RestTemplate): AwsIamAuthentication {
        val options = AwsIamAuthenticationOptions.builder()
            .credentialsProvider(InstanceProfileCredentialsProvider.getInstance()).build()

        return AwsIamAuthentication(options, restTemplate)
    }

    private fun createCubbyholeAuthentication(wrapped: String, restTemplate: RestTemplate): CubbyholeAuthentication {
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

    override fun getOrderId() = "HashiCorpVaultPluginParamsResolvedBuildFeature"

    override fun getConstraint() = PositionConstraint.first()
}
