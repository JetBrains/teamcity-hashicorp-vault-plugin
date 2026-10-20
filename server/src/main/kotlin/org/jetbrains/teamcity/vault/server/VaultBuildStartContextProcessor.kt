package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.buildLog.MessageAttrs
import jetbrains.buildServer.serverSide.impl.RunningBuildState
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
        val build = context.build as RunningBuildEx
        val project = build.buildType?.project ?: return

        val settingsList = try {
            hashiCorpVaultConnectionResolver.getVaultConnections(project)
        } catch (e: ParameterNamespaceCollisionException) {
            val ns = if (e.namespace.isEmpty()) "empty ID" else "ID '${e.namespace}'"
            val message = "Multiple HashiCorp Vault connections with $ns present in project '${e.projectId}'"
            build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${e.namespace}_${e.projectId}", "VaultConnection", message))
            build.stop(RunningBuildState.INTERRUPTED_BY_SYSTEM, null, message, false)
            return
        }

        if (context.build.buildPromotion.customParameters.isNotEmpty() &&
            !TeamCityProperties.getBoolean("teamcity.internal.unsafe.vault.allowCustomParameters") &&
            !isTrustedTrigger(context)) {
            if (stopBuildIfCustomRemoteParam(build)) {
                return
            }

            // TW-104571 We don't want to allow custom queries on vault queries
            removeCustomQueries(context, settingsList)
            removeSetEnvIfAdded(context, settingsList)
        }


        // Set teamcity.vault.<namespace>.legacy.parameters.present parameter for builds where
        // legacy vault references are present
        settingsList.forEach { settings ->
            if (!isParametersContainLegacyVaultReferences(build, settings, context.sharedParameters)) {
                val ns = if (isDefault(settings.id)) "" else " ('${settings.id}' namespace)"
                LOG.debug("There's no need to fetch HashiCorp Vault $ns parameter for build $build")
                return@forEach
            }
            context.addSharedParameter(getVaultParameterName(settings.id, VaultConstants.LEGACY_REFERENCES_USED_SUFFIX), "true")
        }
        setWriteEngineFeatureFlag(context);
    }

    private fun collectVaultQueries(buildPromotion: BuildPromotion, namespaces: Collection<String>): Map<String, String> {
        val customParameters = buildPromotion.customParameters
        val prefixes = namespaces.map { VaultConstants.VAULT_PARAMETER_PREFIX + if (isDefault(it)) "" else "$it:" }
        val parentParameters = (buildPromotion.buildSettings as BuildSettings)
            .parentSettings.parametersCollection.map { it.name to it }.toMap()
        return customParameters.filter { (key, value) ->
            if (key.startsWith("dep.")) false
            // Is legacy parameter
            else if (prefixes.any { prefix -> value.contains(prefix) }) true
            // Is new vault parameter
            else if (parentParameters[key]?.controlDescription?.parameterTypeArguments?.get("remoteType") == VaultConstants.PARAMETER_TYPE) true
            else false
        }.toMap()
    }


    // In the case of custom remote parameters were added or changed, we cannot restore the spec, so we stop the build
    private fun stopBuildIfCustomRemoteParam(build: RunningBuildEx): Boolean {
        val buildPromotion = build.buildPromotion
        val parentParameters = buildPromotion.buildSettings.parentSettings.parametersCollection.map { it.name to it }.toMap()
        val customQueries = buildPromotion.buildSettings.parametersCollection
            .filter { it.controlDescription?.parameterTypeArguments?.get("remoteType") == VaultConstants.PARAMETER_TYPE }
            .filter { !parentParameters.containsKey(it.name) || parentParameters[it.name]?.controlDescription?.parameterTypeArguments != it.controlDescription?.parameterTypeArguments || parentParameters[it.name]?.controlDescription?.parameterType != it.controlDescription?.parameterType }

        if (customQueries.isNotEmpty()) {
            build.stop(RunningBuildState.INTERRUPTED_BY_SYSTEM,
                null,
                "Custom remote parameters were added or changed: ${customQueries.joinToString(", ") { it.name }}. Build will be stopped.", false)

            return true
        }
        return false
    }

    private fun removeSetEnvIfAdded(context: BuildStartContext, settingsList: List<VaultFeatureSettings>) {
        val buildPromotion = context.build.buildPromotion
        val settingsParameters = (buildPromotion.buildSettings as BuildSettings)
            .parentSettings.parameters

        settingsList.forEach {
            val exposeEnvParam = getVaultParameterName(it.id, VaultConstants.BehaviourParameters.ExposeEnvSuffix)
            if (buildPromotion.customParameters[exposeEnvParam] != null) {
                buildPromotion.buildLog
                    .messageAsync(
                        "$exposeEnvParam cannot be altered since the user triggering the build doesn't have permissions to edit the project",
                        Status.WARNING, MessageAttrs.serverMessage()
                    )
                context.addSharedParameter(exposeEnvParam, settingsParameters[exposeEnvParam] ?: "false")
            }
        }
    }

    // Either custom build by project admin or trusted trigger
    private fun isTrustedTrigger(context: BuildStartContext): Boolean {
        val user = context.build.triggeredBy.user
        val projectId = context.build.projectId
        return user == null || projectId == null || user.isPermissionGrantedForProject(projectId, Permission.EDIT_PROJECT)
    }

    private fun removeCustomQueries(context: BuildStartContext, settingsList: List<VaultFeatureSettings>) {
        val buildPromotion = context.build.buildPromotion
        val customParameterRefs = collectVaultQueries(buildPromotion, settingsList.map { it.id })
        if (customParameterRefs.isEmpty()) return
        val settingsParameters = (buildPromotion.buildSettings as BuildSettings)
            .parentSettings.parameters


        customParameterRefs.forEach { key, _ ->
            buildPromotion.buildLog
                .messageAsync(
                    "Custom value of parameter '$key' with a HashiCorp Vault reference is ignored in build. You lack the permissions to do so",
                    Status.WARNING, MessageAttrs.serverMessage()
                )
            context.addSharedParameter(key, settingsParameters[key] ?: "")
        }


    }

    private fun isParametersContainLegacyVaultReferences(
        build: SBuild,
        settings: VaultFeatureSettings,
        sharedParameters: Map<String, String>
    ): Boolean {
        val namespace = settings.id
        if (isShouldSetEnvParameters(build.buildOwnParameters, namespace)) return true;

        // Some parameters may be set by TeamCity (for example, docker registry username and password)
        if (VaultReferencesUtil.hasReferences(sharedParameters, listOf(namespace))) return true;

        if (TeamCityProperties.getBoolean("teamcity.vault.legacyRefsCheck.checkParametersProvider")) {
            // this call should not be required because shared parameters already contain all the parameters
            // generated by the parameters provider
            return VaultReferencesUtil.hasReferences(build.parametersProvider.all, listOf(namespace));
        }

        return false;
    }

    private fun setWriteEngineFeatureFlag(context: BuildStartContext) {
        val propertyValue = TeamCityProperties.getPropertyOrNull(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES) ?: return
        if (!context.sharedParameters.containsKey(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES)) {
            context.addSharedParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, propertyValue)
        }
    }

    override fun getOrderId() = "HashiCorpVaultPluginBuildStartContextProcessor"

    override fun getConstraint() = PositionConstraint.last()
}