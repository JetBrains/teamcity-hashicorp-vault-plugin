package org.jetbrains.teamcity.vault.agent

import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.isJava8OrNewer

class VaultBuildFeature constructor(dispatcher: EventDispatcher<AgentLifeCycleListener>) : AgentLifeCycleAdapter() {
    init {
        if (isJava8OrNewer()) {
            dispatcher.addListener(this)
        }
    }

    override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
        agent.configuration.addConfigurationParameter(VaultConstants.FEATURE_SUPPORTED_AGENT_PARAMETER, "true")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        val feature = runningBuild.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return
        val settings = VaultFeatureSettings(feature.parameters)

        val wrapped = runningBuild.sharedConfigParameters[VaultConstants.WRAPPED_TOKEN_PROEPRTY]
        if (wrapped == null || wrapped.isNullOrEmpty()) {
            runningBuild.buildLogger.error("Wrapped Vault token not found")
            return
        }
        val token: String
        try {
            token = VaultTokenProvider.getToken(settings, wrapped)
        } catch(e: Exception) {
            runningBuild.buildLogger.error("Failed to fetch Vault token")
            runningBuild.buildLogger.exception(e)
            return
        }
        runningBuild.addSharedConfigParameter(VaultConstants.AGENT_CONFIG_PROP, token)
        runningBuild.addSharedEnvironmentVariable(VaultConstants.AGENT_ENV_PROP, token)
        runningBuild.buildLogger.message("Vault token successfully fetched");
    }
}
