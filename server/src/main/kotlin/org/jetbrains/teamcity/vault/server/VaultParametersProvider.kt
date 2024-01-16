
package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings

class VaultParametersProvider : AbstractBuildParametersProvider() {
    companion object {
        internal fun isFeatureEnabled(build: SBuild): Boolean {
            val buildType = build.buildType ?: return false
            val project = buildType.project

            // It's faster than asking OAuthConectionsManager
            if (project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).any {
                    FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
                }) return true

            return false
        }

    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        val buildType = build.buildType ?: return emptyList()
        if (build.isFinished) return emptyList()

        if (!isFeatureEnabled(build)) return emptyList()

        val exposed = HashSet<String>()
        val connectionFeatures = buildType.project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
            FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
        }
        val vaultFeatures = connectionFeatures.map {
            VaultFeatureSettings(it.parameters)
        }
        val parameters = build.buildOwnParameters
        vaultFeatures.forEach { feature: VaultFeatureSettings ->

            if (isShouldSetEnvParameters(parameters, feature.id)) {
                val envPrefix = getEnvPrefix(feature.id)

                exposed += Constants.ENV_PREFIX + envPrefix + VaultConstants.AgentEnvironment.VAULT_TOKEN
                exposed += Constants.ENV_PREFIX + envPrefix + VaultConstants.AgentEnvironment.VAULT_ADDR
            }
        }
        VaultReferencesUtil.collect(parameters, exposed, vaultFeatures.map { feature -> feature.id })
        return exposed
    }
}