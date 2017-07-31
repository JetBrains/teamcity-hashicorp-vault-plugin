package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class VaultParametersProvider(private val connector: VaultConnector) : AbstractBuildParametersProvider() {

    override fun getParameters(build: SBuild, emulationMode: Boolean): Map<String, String> {
        if (build.isFinished) return emptyMap()
        val feature = build.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return emptyMap()
        val wrapped: String
        if (emulationMode) {
            wrapped = "EMULATED"
        } else {
            val settings = VaultFeatureSettings(feature.parameters)
            wrapped = connector.requestWrappedToken(build, settings) ?: "Failed To Request"
        }
        return mapOf("teamcity.vault.wrapped.token" to wrapped)
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        if (build.isFinished) return emptyList()
        build.getBuildFeaturesOfType(VaultConstants.FEATURE_TYPE).firstOrNull() ?: return emptyList()
        return listOf("teamcity.vault.token", Constants.ENV_PREFIX + "VAULT_TOKEN")
    }
}

