package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.*

class VaultParametersProvider(private val connector: VaultConnector) : AbstractBuildParametersProvider() {
    companion object {
        val LOG = Logger.getInstance(VaultParametersProvider::class.java.name)!!

        internal fun isShouldEnableVaultIntegration(build: SBuild): Boolean {
            val buildFeature = build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            val parameters = build.buildOwnParameters
            return buildFeature != null || isShouldSetConfigParameters(parameters) || isShouldSetEnvParameters(parameters) || VaultReferencesUtil.hasReferences(build.parametersProvider.all)
        }

        private fun getFeature(build: SBuild): VaultFeatureSettings? {
            val project = build.buildType?.project
            val buildFeature = build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            val projectFeature = project?.getAvailableFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE)?.firstOrNull()
            if (buildFeature != null) {
                // For compatibility
                return VaultFeatureSettings(buildFeature.parameters)
            } else if (projectFeature != null) {
                return VaultFeatureSettings(projectFeature.parameters)
            } else return null
        }

    }

    override fun getParameters(build: SBuild, emulationMode: Boolean): Map<String, String> {
        if (build.isFinished) return emptyMap()

        val settings = getFeature(build) ?: return emptyMap()
        if (!settings.enabled) return emptyMap()

        if (!isShouldEnableVaultIntegration(build)) {
            LOG.debug("There's no need to fetch vault parameter for build $build")
            return emptyMap()
        }

        val wrapped: String

        if (emulationMode) {
            wrapped = VaultConstants.SPECIAL_EMULATTED
        } else {
            wrapped = try {
                connector.requestWrappedToken(build, settings)
            } catch(e: VaultConnector.ConnectionException) {
                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection", e.message))
                VaultConstants.SPECIAL_FAILED_TO_FETCH
            } catch(e: Throwable) {
                val message = "Failed to fetch Vault wrapped token: ${e.message}"
                LOG.warnAndDebugDetails(message, e)
                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection", e.message))
                VaultConstants.SPECIAL_FAILED_TO_FETCH
            }
        }
        return mapOf(
                VaultConstants.WRAPPED_TOKEN_PROPERTY to wrapped,
                VaultConstants.URL_PROPERTY to settings.url,
                VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE
        )
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        if (build.isFinished) return emptyList()

        val settings = getFeature(build) ?: return emptyList()
        if (!settings.enabled) return emptyList()

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

