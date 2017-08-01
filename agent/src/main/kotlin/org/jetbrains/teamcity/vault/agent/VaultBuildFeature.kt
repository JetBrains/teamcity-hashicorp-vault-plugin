package org.jetbrains.teamcity.vault.agent

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.isJava8OrNewer

class VaultBuildFeature constructor(dispatcher: EventDispatcher<AgentLifeCycleListener>) : AgentLifeCycleAdapter() {
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
        val feature = runningBuild.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return
        val settings = VaultFeatureSettings(feature.parameters)

        val logger = runningBuild.buildLogger
        val wrapped = runningBuild.sharedConfigParameters[VaultConstants.WRAPPED_TOKEN_PROPERTY]
        if (wrapped == null || wrapped.isNullOrEmpty()) {
            logger.internalError(VaultConstants.FEATURE_TYPE, "Wrapped Vault token not found", null)
            return
        }
        if (VaultConstants.SPECIAL_VALUES.contains(wrapped)) {
            logger.internalError(VaultConstants.FEATURE_TYPE, "Wrapped Vault token value is incorrect, seems there was error fetching token on TeamCity server side", null)
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
        runningBuild.addSharedConfigParameter(VaultConstants.AGENT_CONFIG_PROP, token)
        runningBuild.addSharedEnvironmentVariable(VaultConstants.AGENT_ENV_PROP, token)
        logger.message("Vault token successfully fetched")
    }
}
