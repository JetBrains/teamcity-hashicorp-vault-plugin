
package org.jetbrains.teamcity.vault.agent

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.log.Loggers
import org.jetbrains.teamcity.vault.retrier.VaultRetrier
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint
import jetbrains.buildServer.util.retry.Retrier
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.LifecycleAwareSessionManager
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

class VaultBuildFeature(
    dispatcher: EventDispatcher<AgentLifeCycleListener>,
    private val myVaultParametersResolver: VaultParametersResolver,
    private val sessionManagerBuilder: SessionManagerBuilder,
    private val vaultFeatureSettingsFetcher: VaultFeatureSettingsFetcher
) : AgentLifeCycleAdapter(), PositionAware {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultBuildFeature::class.java.name)
    }

    private val retrier: Retrier

    init {
        dispatcher.addListener(this)
        LOG.info("HashiCorp Vault integration enabled")
        retrier = VaultRetrier.getRetrier()
    }

    private val sessions = ConcurrentHashMap<Long, LifecycleAwareSessionManager>()

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
        val nonFetchedNamespaces = allNamespaces.filter {
            allParameters[getParametersFetchedForNamespaceParameter(it)] != "true"
        }

        val settingsAndTokens = nonFetchedNamespaces.mapNotNull { namespace ->
            val settings = vaultFeatureSettingsFetcher.getVaultFeatureSettings(namespace, build) ?: return@mapNotNull null
            val token = resolveToken(allParameters, settings, build, namespace) ?: return@mapNotNull null
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
                    myVaultParametersResolver.resolveLegacyReferences(build, settingsAndToken.settings, settingsAndToken.token, namespace)
                }

                build.addSharedConfigParameter(getParametersFetchedForNamespaceParameter(namespace), "true")
            }
        }
    }

    private fun getParametersFetchedForNamespaceParameter(namespace:String) = if (namespace == VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE){
        "${VaultConstants.PARAMETER_PREFIX}.${VaultConstants.VAULT_PARAMETERS_FETCHED_SUFFIX}"

    } else {
        "${VaultConstants.PARAMETER_PREFIX}.$namespace.${VaultConstants.VAULT_PARAMETERS_FETCHED_SUFFIX}"
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
                // If coming from custom build dialog
                val paramValue = allAccessibleParameters[parameterKey]
                if (!StringUtil.isEmpty(paramValue) && paramValue?.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX) == true){
                    VaultParameter(
                        parameterKey,
                        parameterSettings.copy(
                            vaultQuery = VaultReferencesUtil.getPath(paramValue, VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE))
                    )
                } else {
                    VaultParameter(parameterKey, parameterSettings)
                }
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

    private fun resolveToken(
        parameters: Map<String, String>,
        settings: VaultFeatureSettings,
        runningBuild: AgentRunningBuild,
        namespace: String
    ): String? {
        if (settings.url.isBlank()) {
            return null
        }
        val logger = runningBuild.buildLogger
        val token: String
        try {
            val sessionManager = sessionManagerBuilder.buildWithImprovedLogging(settings, logger)
            sessions[runningBuild.buildId] = sessionManager
            val sessionToken = retrier.execute(
                Callable {
                    sessionManager.sessionToken.token
                }
            )

            if (sessionToken == null){
                throw IllegalStateException("Failed to get session token")
            }
            token = sessionToken
        } catch (e: Exception) {
            val errorPrefix = when (settings.auth.method) {
                AuthMethod.APPROLE -> "Failed to unwrap HashiCorp Vault token"
                AuthMethod.LDAP -> "Failed to get HashiCorp Vault token using LDAP"
                AuthMethod.GCP_IAM -> "Failed to get HashiCorp Vault token using GCP IAM"
            }
            if (settings.failOnError) {
                logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, errorPrefix + ": " + e.message, e)
                logger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${runningBuild.buildTypeId}_${settings.id}_A", "VaultConnection", errorPrefix))
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