package org.jetbrains.teamcity.vault.agent

import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.util.retry.Retrier
import jetbrains.buildServer.util.retry.RetrierEventListener
import org.jetbrains.teamcity.vault.retrier.VaultRetrier
import java.lang.Exception
import java.util.concurrent.Callable

object VaultAgentRetrier {

    fun getAgentRetrier(build: AgentRunningBuild, retrierPurpose: String): Retrier {
        val agentLoggerListener = object : RetrierEventListener {
            override fun <T : Any?> onFailure(callable: Callable<T?>, atempt: Int, e: Exception) {
                build.buildLogger.warning("Hashicorp Vault request atempt $atempt failed: ${e.message}")
            }
        }

        return VaultRetrier.getRetrier(
            retrierPurpose,
            additionalListeners = listOf(agentLoggerListener),
            params = build.sharedConfigParameters
        )
    }
}