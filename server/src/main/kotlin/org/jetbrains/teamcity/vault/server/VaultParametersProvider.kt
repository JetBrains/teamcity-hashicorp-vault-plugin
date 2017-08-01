package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class VaultParametersProvider(private val connector: VaultConnector) : AbstractBuildParametersProvider() {
    companion object {
        val LOG = Logger.getInstance(VaultParametersProvider::class.java.name)!!
    }
    override fun getParameters(build: SBuild, emulationMode: Boolean): Map<String, String> {
        if (build.isFinished) return emptyMap()
        val feature = build.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return emptyMap()
        val wrapped: String
        val settings = VaultFeatureSettings(feature.parameters)
        if (emulationMode) {
            wrapped = "EMULATED"
        } else {
            wrapped = try {
                connector.requestWrappedToken(build, settings) ?: "Failed To Request"
            } catch(e: Throwable) {
                LOG.error("Failed to resolve: ${e.message}")
                "Failed to resolve: ${e.message}"
            }
        }
        return mapOf(VaultConstants.WRAPPED_TOKEN_PROEPRTY to wrapped, "teamcity.vault.url" to settings.url)
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        if (build.isFinished) return emptyList()
        build.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return emptyList()
        return listOf(VaultConstants.AGENT_CONFIG_PROP, Constants.ENV_PREFIX + VaultConstants.AGENT_ENV_PROP)
    }
}

