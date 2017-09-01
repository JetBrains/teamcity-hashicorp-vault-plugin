package org.jetbrains.teamcity.vault.agent

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.teamcity.vault.*
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler
import org.springframework.vault.authentication.CubbyholeAuthentication
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions
import org.springframework.vault.authentication.LifecycleAwareSessionManager
import org.springframework.vault.support.VaultToken
import java.util.concurrent.ConcurrentHashMap

class VaultBuildFeature(dispatcher: EventDispatcher<AgentLifeCycleListener>,
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
        val url = parameters[VaultConstants.URL_PROPERTY]
        val wrapped = parameters[VaultConstants.WRAPPED_TOKEN_PROPERTY]

        if (url == null || url.isNullOrBlank()) return

        // TODO: Use better constructor / refactor VaultFeatureSettings
        val settings = VaultFeatureSettings(url, "", "")

        val logger = runningBuild.buildLogger
        if (wrapped == null || wrapped.isNullOrEmpty()) {
            logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped HashiCorp Vault token not found", null)
            return
        }
        if (VaultConstants.SPECIAL_VALUES.contains(wrapped)) {
            logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, "Wrapped HashiCorp Vault token value is incorrect, seems there was error fetching token on TeamCity server side", null)
            return
        }
        val token: String
        try {
            val options = CubbyholeAuthenticationOptions.builder()
                    .wrapped()
                    .initialToken(VaultToken.of(wrapped))
                    .build()
            val template = createRestTemplate(settings)
            val authentication = CubbyholeAuthentication(options, template)

            val sessionManager = LifecycleAwareSessionManager(authentication, scheduler, template)
            sessions[runningBuild.buildId] = sessionManager
            token = sessionManager.sessionToken.token
        } catch(e: Exception) {
            logger.error("Failed to unwrap HashiCorp Vault token: " + e.message)
            logger.exception(e)
            return
        }
        logger.message("HashiCorp Vault token successfully fetched")

        runningBuild.passwordReplacer.addPassword(token)

        if (isShouldSetEnvParameters(parameters)) {
            runningBuild.addSharedEnvironmentVariable(VaultConstants.AgentEnvironment.VAULT_TOKEN, token)
            runningBuild.addSharedEnvironmentVariable(VaultConstants.AgentEnvironment.VAULT_ADDR, settings.url)
        }

        myVaultParametersResolver.resolve(runningBuild, settings, token)
    }

    override fun beforeBuildFinish(build: AgentRunningBuild, buildStatus: BuildFinishedStatus) {
        // Stop renewing token, revoke token
        val manager = sessions[build.buildId] ?: return
        manager.destroy()
    }
}
