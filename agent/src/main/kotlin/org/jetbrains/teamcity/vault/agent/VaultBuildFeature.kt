package org.jetbrains.teamcity.vault.agent

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.teamcity.vault.*

class VaultBuildFeature constructor(dispatcher: EventDispatcher<AgentLifeCycleListener>,
                                    private val myVaultParametersResolver: VaultParametersResolver) : AgentLifeCycleAdapter() {
    companion object {
        val LOG = Logger.getInstance(VaultBuildFeature::class.java.name)!!
    }
    init {
        if (isJava8OrNewer()) {
            dispatcher.addListener(this)
            LOG.info("Vault intergration enabled")
        } else {
            LOG.warn("Vault integration disabled: agent should be running under Java 1.8 or newer")
        }
    }

    override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
        agent.configuration.addConfigurationParameter(VaultConstants.FEATURE_SUPPORTED_AGENT_PARAMETER, "true")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        val parameters = runningBuild.sharedConfigParameters
        val url = parameters[VaultConstants.URL_PROPERTY]
        val wrapped = parameters[VaultConstants.WRAPPED_TOKEN_PROPERTY]

        if (url == null || url.isNullOrBlank()) return

        // TODO: Use better constructor / refactor VaultFeatureSettings
        val settings = VaultFeatureSettings(url, true, "", "")

        val logger = runningBuild.buildLogger
        if (wrapped == null || wrapped.isNullOrEmpty()) {
            logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped Vault token not found", null)
            return
        }
        if (VaultConstants.SPECIAL_VALUES.contains(wrapped)) {
            logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped Vault token value is incorrect, seems there was error fetching token on TeamCity server side", null)
            return
        }
        val token: String
        try {
            token = VaultTokenProvider.unwrap(settings, wrapped)
        } catch(e: Exception) {
            logger.error("Failed to unwrap Vault token: " + e.message)
            logger.exception(e)
            return
        }
        logger.message("Vault token successfully fetched")

        if (isShouldSetConfigParameters(parameters)) {
            runningBuild.addSharedConfigParameter(VaultConstants.AGENT_CONFIG_PROP, token)
        }
        if (isShouldSetEnvParameters(parameters)) {
            runningBuild.addSharedEnvironmentVariable(VaultConstants.AgentEnvironment.VAULT_TOKEN, token)
            runningBuild.addSharedEnvironmentVariable(VaultConstants.AgentEnvironment.VAULT_ADDR, settings.url)
        }

        myVaultParametersResolver.resolve(runningBuild, settings, token)
    }
}
