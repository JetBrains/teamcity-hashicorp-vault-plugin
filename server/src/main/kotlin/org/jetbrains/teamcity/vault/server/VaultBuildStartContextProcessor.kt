
package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.server.HashiCorpVaultConnectionResolver.ParameterNamespaceCollisionException

class VaultBuildStartContextProcessor(
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver
) : BuildStartContextProcessor, PositionAware {
    companion object {
        private val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)
    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build
        val project = build.buildType?.project ?: return

        val settingsList = try {
            hashiCorpVaultConnectionResolver.getVaultConnections(project)
        } catch (e: ParameterNamespaceCollisionException) {
            val ns = if (e.namespace.isEmpty()) "empty namespace" else "namespace '${e.namespace}'"
            val message = "Multiple HashiCorp Vault connections with $ns present in project '${e.projectId}'"
            build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${e.namespace}_${e.projectId}", "VaultConnection", message))
            build.stop(null, message)
            return
        }

        // Set teamcity.vault.<namespace>.legacy.parameters.present parameter for builds where
        // legacy vault references are present
        settingsList.forEach { settings ->
            if (!isParamatersContainLegacyVaultReferences(build, settings, context.sharedParameters)) {
                val ns = if (isDefault(settings.id)) "" else " ('${settings.id}' namespace)"
                LOG.debug("There's no need to fetch HashiCorp Vault$ns parameter for build $build")
                return@forEach
            }
            context.addSharedParameter(getVaultParameterName(settings.id, VaultConstants.LEGACY_REFERENCES_USED_SUFFIX), settings.failOnError.toString())
        }
    }

    private fun isParamatersContainLegacyVaultReferences(
            build: SBuild,
            settings: VaultFeatureSettings,
            sharedParameters: Map<String, String>
    ): Boolean {
        val namespace = settings.id
        return isShouldSetEnvParameters(build.buildOwnParameters, namespace)
                // Slowest part:
                || VaultReferencesUtil.hasReferences(build.parametersProvider.all, listOf(namespace))
                // Some parameters may be set by TeamCity (for example, docker registry username and password)
                || VaultReferencesUtil.hasReferences(sharedParameters, listOf(namespace))
    }

    override fun getOrderId() = "HashiCorpVaultPluginBuildStartContextProcessor"

    override fun getConstraint() = PositionConstraint.last()
}