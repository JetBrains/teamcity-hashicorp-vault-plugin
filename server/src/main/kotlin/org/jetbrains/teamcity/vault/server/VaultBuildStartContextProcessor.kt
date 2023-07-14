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
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver
) : BuildStartContextProcessor, PositionAware {
    companion object {
        private val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)
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

        // Set teamcity.vault.<namespace>.legacy.parameters.present parameter for builds where
        // legacy vault references are present
        settingsList.forEach { settings ->
            if (!isParamatersContainLegacyVaultReferences(build, settings, context.sharedParameters)) {
                val ns = if (isDefault(settings.namespace)) "" else " ('${settings.namespace}' namespace)"
                LOG.debug("There's no need to fetch HashiCorp Vault$ns parameter for build $build")
                return@forEach
            }
            context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.LEGACY_REFERENCES_USED_SUFFIX), settings.failOnError.toString())
        }
    }

    private fun isParamatersContainLegacyVaultReferences(
            build: SBuild,
            settings: VaultFeatureSettings,
            sharedParameters: Map<String, String>
    ): Boolean {
        val namespace = settings.namespace
        return isShouldSetEnvParameters(build.buildOwnParameters, namespace)
                // Slowest part:
                || VaultReferencesUtil.hasReferences(build.parametersProvider.all, listOf(namespace))
                // Some parameters may be set by TeamCity (for example, docker registry username and password)
                || VaultReferencesUtil.hasReferences(sharedParameters, listOf(namespace))
    }

    override fun getOrderId() = "HashiCorpVaultPluginBuildStartContextProcessor"

    override fun getConstraint() = PositionConstraint.last()
}
