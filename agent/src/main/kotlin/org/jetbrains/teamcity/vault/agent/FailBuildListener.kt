package org.jetbrains.teamcity.vault.agent

import jetbrains.buildServer.RunBuildException
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentRunningBuild
import org.jetbrains.teamcity.vault.VaultConstants

class FailBuildListener : AgentLifeCycleAdapter() {
    override fun buildStarted(runningBuild: AgentRunningBuild) {
        val parameters = runningBuild.sharedConfigParameters
        val url = parameters[VaultConstants.URL_PROPERTY]

        if (url == null || url.isNullOrBlank()) return

        throw RunBuildException("HashiCorp Vault is not supported on this agent. Please add agent requirement for '${VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT}' agent parameter or run agent using Java 1.8")
    }
}