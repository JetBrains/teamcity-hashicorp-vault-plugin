package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.VaultReferencesUtil
import org.jetbrains.teamcity.vault.isShouldSetEnvParameters

class VaultBuildStartContextProcessor(private val connector: VaultConnector) : BuildStartContextProcessor {
    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)!!

        private fun getFeature(build: SBuild): VaultFeatureSettings? {
            val buildType = build.buildType ?: return null
            val project = buildType.project

            val buildFeature = build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            if (buildFeature != null) {
                // For compatibility
                return VaultFeatureSettings(buildFeature.parameters)
            }
            val projectFeature = project.getAvailableFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            if (projectFeature != null) {
                return VaultFeatureSettings(projectFeature.parameters)
            }
            val connectionFeature = project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).firstOrNull {
                VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
            }
            if (connectionFeature != null) {
                return VaultFeatureSettings(connectionFeature.parameters)
            }
            return null
        }

        internal fun isShouldEnableVaultIntegration(build: SBuild): Boolean {
            val buildFeature = build.getBuildFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
            if (buildFeature != null) return true
            val parameters = build.buildOwnParameters
            return isShouldSetEnvParameters(parameters)
                    // Slowest part:
                    || VaultReferencesUtil.hasReferences(build.parametersProvider.all)
        }

    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build

        val settings = getFeature(build) ?: return

        if (!isShouldEnableVaultIntegration(build)) {
            LOG.debug("There's no need to fetch vault parameter for build $build")
            return
        }

        val wrapped: String = try {
            connector.requestWrappedToken(build, settings)
        } catch (e: Throwable) {
            val message = "Failed to fetch HashiCorp Vault wrapped token: ${e.message}"
            LOG.warnAndDebugDetails(message, e)
            build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection", message))
            return
        }

        context.addSharedParameter(VaultConstants.WRAPPED_TOKEN_PROPERTY, wrapped)
        context.addSharedParameter(VaultConstants.URL_PROPERTY, settings.url)
    }
}