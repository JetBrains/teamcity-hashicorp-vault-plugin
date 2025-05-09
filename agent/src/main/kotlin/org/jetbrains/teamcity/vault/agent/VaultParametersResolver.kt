
package org.jetbrains.teamcity.vault.agent

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.*
import java.util.*

class VaultParametersResolver(trustStoreProvider: SSLTrustStoreProvider) : VaultResolver(trustStoreProvider) {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultParametersResolver::class.java.name)
    }

    fun resolveLegacyReferences(build: AgentRunningBuild, settings: VaultFeatureSettings, token: String, namespace: String, isWriteEngineEnabled: Boolean) {
        val references = getRelatedParameterReferences(build, namespace)
        if (references.isEmpty()) {
            LOG.info("There's nothing to resolve")
            return
        }
        val logger = build.buildLogger
        logger.message("${references.size} ${"reference".pluralize(references)} to resolve: $references")

        val parameters = references.map { VaultQuery.extract(VaultReferencesUtil.getPath(it, namespace), isWriteEngineEnabled) }

        val replacements = resolveReplacements(build, settings, parameters, token)

        replaceParametersReferences(build, replacements.replacements, references, namespace)
    }

    fun resolveParameters(build: AgentRunningBuild, settings: VaultFeatureSettings, vaultParameters: List<VaultParameter>, token: String, isWriteEngineEnabled: Boolean) {
        if (vaultParameters.isEmpty()) {
            return
        }

        val humanReadableParamsDesc = vaultParameters.map { vaultParameter ->
            "'param=${vaultParameter.parameterKey}, vaultQuery=${vaultParameter.vaultParameterSettings.vaultQuery}'"
        }
        val logger = build.buildLogger
        logger.message("${humanReadableParamsDesc.size} remote ${"parameter".pluralize(humanReadableParamsDesc)} to resolve: $humanReadableParamsDesc")

        val keyToQuery = vaultParameters.associate { parameter ->
            parameter.parameterKey to VaultQuery.extract(parameter.vaultParameterSettings.vaultQuery, isWriteEngineEnabled)
        }

        val replacements = resolveReplacements(build, settings, keyToQuery.values, token).replacements
        keyToQuery.forEach { (key, value) ->
            val replacement = replacements[value.full]
            if (replacement != null) {
                when {
                    key.startsWith(Constants.SYSTEM_PREFIX) -> build.addSharedSystemProperty(key.removePrefix(Constants.SYSTEM_PREFIX), replacement)
                    key.startsWith(Constants.ENV_PREFIX) -> build.addSharedEnvironmentVariable(key.removePrefix(Constants.ENV_PREFIX), replacement)
                    else -> build.addSharedConfigParameter(key, replacement)
                }
            }
        }
    }

    private fun resolveReplacements(
        build: AgentRunningBuild,
        settings: VaultFeatureSettings,
        parameters: Collection<VaultQuery>,
        token: String
    ): ResolvingResult {
        val replacements = doFetchAndPrepareReplacements(settings, token, parameters)

        if (replacements.errors.isNotEmpty()) {
            val ns = if (isDefault(settings.id)) "" else "('${settings.id}' namespace)"
            replacements.errors.values.forEach {
                build.buildLogger.warning(it)
            }

            val message = "${"Error".pluralize(replacements.errors.size)} while fetching data from HashiCorp Vault $ns"
            build.buildLogger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${settings.id}_A", "VaultConnection", message))
            build.stopBuild(message)
        }


        replacements.replacements.values.forEach { build.passwordReplacer.addPassword(it) }
        return replacements
    }


    private fun getRelatedParameterReferences(build: AgentRunningBuild, namespace: String): Collection<String> {
        val references = HashSet<String>()
        VaultReferencesUtil.collect(build.sharedConfigParameters, references, namespace)
        VaultReferencesUtil.collect(build.sharedBuildParameters.allParameters, references, namespace)
        return references.sorted()
    }

    private fun replaceParametersReferences(build: AgentRunningBuild, replacements: Map<String, String>, usages: Collection<String>, namespace: String) {
        // usage may not have leading slash
        for (usage in usages) {
            val replacement = replacements[VaultReferencesUtil.getPath(usage, namespace)]
            if (replacement != null) {
                build.addSharedConfigParameter(usage, replacement)
            }
        }
    }

}