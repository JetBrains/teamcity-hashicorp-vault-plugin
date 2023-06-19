/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint
import org.jetbrains.teamcity.vault.*

class VaultBuildStartContextProcessor(
    private val connector: VaultConnector,
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver
) : BuildStartContextProcessor, PositionAware {
    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)
        internal fun isShouldEnableVaultIntegration(build: SBuild, settings: VaultFeatureSettings, sharedParameters: Map<String, String>): Boolean {
            val parameters = build.buildOwnParameters
            return isShouldSetEnvParameters(parameters, settings.namespace)
                    // Slowest part:
                    || VaultReferencesUtil.hasReferences(build.parametersProvider.all, listOf(settings.namespace))
                    // Some parameters may be set by TeamCity (for example, docker registry username and password)
                    || VaultReferencesUtil.hasReferences(sharedParameters, listOf(settings.namespace))
        }
    }

    private fun getFeatures(build: SRunningBuild, reportProblems: Boolean): List<VaultFeatureSettings> {
        val buildType = build.buildType ?: return emptyList()

        val projectToConnectionPairs = hashiCorpVaultConnectionResolver.getProjectToConnectionPairs(buildType)

        if (reportProblems) {
            projectToConnectionPairs.groupBy({ it.first }, { it.second }).forEach { pid, features ->
                features.groupBy { it.namespace }
                    .filterValues { it.size > 1 }
                    .forEach { namespace, clashing ->
                        val nsDescripption = if (isDefault(namespace)) "default namespace" else "'$namespace' namespace"
                        val message = "Multiple HashiCorp Vault connections with $nsDescripption present in project '$pid'"
                        build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${namespace}_$pid", "VaultConnection", message))
                        if (clashing.any { it.failOnError }) {
                            build.stop(null, message)
                        }
                    }
            }
        }
        val vaultFeatures = projectToConnectionPairs.map { it.second }

        return vaultFeatures.groupBy { it.namespace }.map { (_, v) -> v.first() }
    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build

        val settingsList = getFeatures(build, true)
        if (settingsList.isEmpty())
            return

        settingsList.map { settings ->
            val ns = if (isDefault(settings.namespace)) "" else " ('${settings.namespace}' namespace)"
            if (!isShouldEnableVaultIntegration(build, settings, context.sharedParameters)) {
                LOG.debug("There's no need to fetch HashiCorp Vault$ns parameter for build $build")
                return@map
            }

            try {
                val agentSettings = hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(build, settings, ns)
                agentSettings
                    .toSharedParameters().forEach {
                        context.addSharedParameter(it.key, it.value)
                    }
                val auth = agentSettings.auth
                val wrappedToken = when (auth) {
                    is Auth.AppRoleAuthAgent -> auth.wrappedToken
                    is Auth.LdapAgent -> auth.wrappedToken
                    else -> null
                }

                if (wrappedToken != null) {
                    context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.WRAPPED_TOKEN_PROPERTY_SUFFIX), wrappedToken)

                }
            } catch (e: Throwable) {
                build.stop(null, e.localizedMessage)
            }
        }
    }

    override fun getOrderId() = "HashiCorpVaultPluginBuildStartContextProcessor"

    override fun getConstraint() = PositionConstraint.last()
}
