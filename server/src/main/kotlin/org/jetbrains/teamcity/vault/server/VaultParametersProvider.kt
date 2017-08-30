package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.*

class VaultParametersProvider : AbstractBuildParametersProvider() {
    companion object {
        val LOG = Logger.getInstance(VaultParametersProvider::class.java.name)!!

        internal fun isShouldEnableVaultIntegration(build: SBuild): Boolean {
            val buildFeature = build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            if (buildFeature != null) return true
            val parameters = build.buildOwnParameters
            return isShouldSetConfigParameters(parameters) || isShouldSetEnvParameters(parameters)
                    // Slowest part:
                    || VaultReferencesUtil.hasReferences(build.parametersProvider.all)
        }

        internal fun isFeatureEnabled(build: SBuild): Boolean {
            val buildType = build.buildType ?: return false
            val project = buildType.project

            val projectFeature = project.getAvailableFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            if (projectFeature != null) return true

            val buildFeature = buildType.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            return buildFeature != null && buildType.isEnabled(buildFeature.id)
        }

    }

    override fun getParameters(build: SBuild, emulationMode: Boolean): Map<String, String> {
        if (build.isFinished) return emptyMap()
        if (!isFeatureEnabled(build)) return emptyMap()

        // TODO: check could be slow since it checks all parameter values for '%vault:'
        if (!isShouldEnableVaultIntegration(build)) {
            LOG.debug("There's no need to fetch vault parameter for build $build")
            return emptyMap()
        }

        return mapOf(
                VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE
        )
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        if (build.isFinished) return emptyList()

        if (!isFeatureEnabled(build)) return emptyList()

        val exposed = HashSet<String>()
        val parameters = build.buildOwnParameters
        if (isShouldSetEnvParameters(parameters)) {
            exposed += Constants.ENV_PREFIX + VaultConstants.AgentEnvironment.VAULT_TOKEN
            exposed += Constants.ENV_PREFIX + VaultConstants.AgentEnvironment.VAULT_ADDR
        }
        if (isShouldSetConfigParameters(parameters)) {
            exposed += VaultConstants.AGENT_CONFIG_PROP
        }
        VaultReferencesUtil.collect(parameters, exposed)
        return exposed
    }
}

