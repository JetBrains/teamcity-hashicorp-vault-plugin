package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.isShouldSetConfigParameters
import org.jetbrains.teamcity.vault.isShouldSetEnvParameters

class VaultParametersProvider(private val connector: VaultConnector) : AbstractBuildParametersProvider() {
    companion object {
        val LOG = Logger.getInstance(VaultParametersProvider::class.java.name)!!
    }

    override fun getParameters(build: SBuild, emulationMode: Boolean): Map<String, String> {
        if (build.isFinished) return emptyMap()
        val buildType = build.buildType ?: return emptyMap()
        val project = buildType.project

        val settings: VaultFeatureSettings

        val buildFeature = build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
        val projectFeature = project.getAvailableFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
        if (buildFeature != null) {
            // For compatibility
            settings = VaultFeatureSettings(buildFeature.parameters)
        } else if (projectFeature != null) {
            settings = VaultFeatureSettings(projectFeature.parameters)
        } else return emptyMap()

        if (!isShouldSetConfigParameters(build.buildOwnParameters)
                && !isShouldSetEnvParameters(build.buildOwnParameters)
                && !hasVaultParameters(build)
                && buildFeature == null) {
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
        return mapOf(VaultConstants.WRAPPED_TOKEN_PROPERTY to wrapped, VaultConstants.URL_PROPERTY to settings.url)
    }

    private fun hasVaultParameters(build: SBuild): Boolean {
        // TODO: investigate how it would workwhen invoked from ParametersProvider
        // TODO: Check for SOE
        return build.parametersProvider.all.any { it.value.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX) }
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        if (build.isFinished) return emptyList()
        build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull() ?: return emptyList()
        val exposed = ArrayList<String>(3)
        if (isShouldSetEnvParameters(build.buildOwnParameters)) {
            exposed += Constants.ENV_PREFIX + VaultConstants.AgentEnvironment.VAULT_TOKEN
            exposed += Constants.ENV_PREFIX + VaultConstants.AgentEnvironment.VAULT_ADDR
        }
        if (isShouldSetConfigParameters(build.buildOwnParameters)) {
            exposed += VaultConstants.AGENT_CONFIG_PROP
        }
        return exposed
    }
}

