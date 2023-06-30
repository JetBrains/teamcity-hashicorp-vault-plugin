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
import org.springframework.http.HttpStatus
import java.util.concurrent.ConcurrentHashMap

class VaultBuildFeature(
    dispatcher: EventDispatcher<AgentLifeCycleListener>,
    private val myVaultParametersResolver: VaultParametersResolver,
    private val sslTrustStoreProvider: SSLTrustStoreProvider,
    private val sessionManagerBuilder: SessionManagerBuilder
) : AgentLifeCycleAdapter(), PositionAware {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultBuildFeature::class.java.name)
    }

    init {
        dispatcher.addListener(this)
        LOG.info("HashiCorp Vault integration enabled")
    }

    private val sessions = ConcurrentHashMap<Long, LifecycleAwareSessionManager>()
    private val objectMapper by lazy {
        jacksonObjectMapper()
    }

    override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
        agent.configuration.addConfigurationParameter(VaultConstants.FEATURE_SUPPORTED_AGENT_PARAMETER, "true")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        updateBuildParameters(runningBuild)
    }

    private fun updateBuildParameters(build: AgentRunningBuild) {
        val allParameters = build.sharedConfigParameters + build.sharedBuildParameters.allParameters

        val vaultNamespacesAndParameters = extractVaultParameters(build, allParameters)
        val vaultLegacyReferencesNamespaces = extractLegacyVaultNamespaces(build)

        val allNamespaces = vaultNamespacesAndParameters.keys + vaultLegacyReferencesNamespaces
        val settingsAndTokens = allNamespaces.mapNotNull { namespace ->
            val settings = getVaultFeatureSettings(namespace, build) ?: return@mapNotNull null
            val token = resolveToken(allParameters, settings, build) ?: return@mapNotNull null
            namespace to VaultFeatureSettingsAndToken(settings, token)
        }

        settingsAndTokens.forEach { (namespace, settingsAndToken) ->
            build.buildLogger.activity("HashiCorp Vault" + if (namespace != "") " (namespace '$namespace')" else "",
                    VaultConstants.FeatureSettings.FEATURE_TYPE) {
                val parameters = vaultNamespacesAndParameters[namespace]
                if (!parameters.isNullOrEmpty()) {
                    myVaultParametersResolver.resolveParameters(build, settingsAndToken.settings, parameters, settingsAndToken.token)
                }

                if (vaultLegacyReferencesNamespaces.contains(namespace)) {
                    myVaultParametersResolver.resolveLegacyReferences(build, settingsAndToken.settings, settingsAndToken.token)
                }
            }
        }
    }


    private fun extractVaultParameters(build: AgentRunningBuild, allAccessibleParameters: Map<String, String>): Map<String, List<VaultParameter>> {
        build as AgentRunningBuildEx // required for AgentRunningBuildEx#getParameterControlDescription
        val paramKeyToControlDescription = allAccessibleParameters.keys
                .mapNotNull { parameterKey ->
                    val controlDescription = build.getParameterControlDescription(parameterKey)
                    controlDescription?.let { parameterKey to it }
                }
                .filter { (_, controlDescription) ->
                    controlDescription.parameterTypeArguments["remoteType"] == VaultConstants.PARAMETER_TYPE
                }.toList()

        return paramKeyToControlDescription.mapNotNull { (parameterKey, controlDescription) ->
            try {
                val parameterTypeArguments = controlDescription.parameterTypeArguments
                val parameterSettings = VaultParameterSettings(parameterTypeArguments)
                VaultParameter(parameterKey, parameterSettings)
            } catch (e: Throwable) {
                val errorMessage = "Failed to parse Vault parameter settings for parameter '$parameterKey'"
                LOG.warnAndDebugDetails(errorMessage, e)
                build.buildLogger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, errorMessage, e)
                build.stopBuild(errorMessage)
                null
            }
        }.groupBy { vaultParameter -> vaultParameter.vaultParameterSettings.namespace }
    }

    private fun extractLegacyVaultNamespaces(build: AgentRunningBuild): Set<String> {
        return build.sharedConfigParameters.keys
                .filter { isLegacyReferencesUsedParameter(it) }
                .mapNotNull {
                    it.removePrefix(VaultConstants.PARAMETER_PREFIX)
                            .removeSuffix(VaultConstants.LEGACY_REFERENCES_USED_SUFFIX)
                            .removePrefix(".")
                }.toSet()
    }


    private fun getVaultFeatureSettings(namespace: String, build: AgentRunningBuild): VaultFeatureSettings? {
        val logger = build.buildLogger
        val errorPrefix = "Failed to get HashiCorp Vault wrapped token from TeamCity server for parameter namespace '$namespace':"

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
                requestBuilder.withProxyHost(URIBuilder(configuration.serverProxyHost).setPort(configuration.serverProxyPort).build())

                val serverProxyCredentials = configuration.serverProxyCredentials
                if (serverProxyCredentials != null) {
                    requestBuilder.withProxyCredentials(serverProxyCredentials)
                }
            }

            val response = DelegatingRequestHandler().doSyncRequest(requestBuilder.build())
            if (response.statusCode != HttpStatus.OK.value()) {
                val errorMessage = "$errorPrefix ${response.bodyAsString}"
                LOG.error(errorMessage)
                logger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${namespace}_A", "VaultConnection", errorMessage))
                build.stopBuild(errorMessage)
                return null
            }

            val contentStream = response.contentStream
            if (contentStream == null) {
                val errorMessage = "$errorPrefix empty response from server"
                LOG.error(errorMessage)
                logger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${namespace}_A", "VaultConnection", errorMessage))
                build.stopBuild(errorMessage)
                return null
            }

            val featureSettingsParams = objectMapper.readValue<Map<String, String>>(contentStream)

            VaultFeatureSettings.getAgentFeatureFromProperties(featureSettingsParams)
        } catch (e: Throwable) {
            val errorMessage = "$errorPrefix internal error"
            LOG.error(errorMessage, e)
            logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, errorMessage, e)
            build.stopBuild(errorMessage)
            null
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
        val token: String
        try {
            val sessionManager = sessionManagerBuilder.buildWithImprovedLogging(settings, logger)
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

    private data class VaultFeatureSettingsAndToken(val settings: VaultFeatureSettings, val token: String)
}
