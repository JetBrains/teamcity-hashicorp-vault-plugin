/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.VaultReferencesUtil
import org.jetbrains.teamcity.vault.isShouldSetEnvParameters
import org.jetbrains.teamcity.vault.*

class VaultBuildStartContextProcessor(private val connector: VaultConnector) : BuildStartContextProcessor {
    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)!!

        private fun getFeatures(build: SBuild, reportProblems: Boolean): List<VaultFeatureSettings> {
            val buildType = build.buildType ?: return emptyList()

            val connectionFeatures = buildType.project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
                VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
            }

            // Two features with same prefix cannot coexist in same project
            // Though it's possible to override feature with same prefix in subproject

            if (reportProblems) {
                connectionFeatures.groupBy { it.projectId }.forEach { pid, features ->
                    features.groupBy { it.parameters[VaultConstants.FeatureSettings.PARAMETER_PREFIX] }
                            .filterValues { it.size > 1 }
                            .forEach{ prefix, _ ->
                                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${prefix}_$pid", "VaultConnection",
                                        "Multiple vault connections with prefix \"$prefix\" present in project $pid"
                                ))
                            }
                }
            }
            val vaultFeatures = connectionFeatures.map {
                VaultFeatureSettings(it.parameters)
            }
            return vaultFeatures.groupBy { it.prefix }.map { (_, v) -> v.first() }
        }

        internal fun isShouldEnableVaultIntegration(build: SBuild, settings: VaultFeatureSettings): Boolean {
            val parameters = build.buildOwnParameters
            return isShouldSetEnvParameters(parameters,settings.prefix)
                    // Slowest part:
                    || VaultReferencesUtil.hasReferences(build.parametersProvider.all, settings.prefix)
        }

    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build

        val settingsList = getFeatures(build, true)
        if(settingsList.isEmpty())
            return

        settingsList.map {settings ->
            if (!isShouldEnableVaultIntegration(build, settings)) {
                LOG.debug("There's no need to fetch vault parameter for build $build (${settings.prefix})")
                return@map
            }

            val wrappedToken: String = try {
                connector.requestWrappedToken(build, settings)
            } catch (e: Throwable) {
                val message = "Failed to fetch HashiCorp Vault wrapped token (${settings.prefix}): ${e.message}"
                LOG.warn(message, e)
                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection",
                        message + ": " + e.toString() + ", see teamcity-server.log for details"
                ))
                return@map
            }

            context.addSharedParameter(VaultConstants.PARAMETER_PREFIX + prefixOrDefault(settings.prefix) + VaultConstants.WRAPPED_TOKEN_PROPERTY_SUFFIX, wrappedToken)
            context.addSharedParameter(VaultConstants.PARAMETER_PREFIX + prefixOrDefault(settings.prefix) + VaultConstants.URL_PROPERTY_SUFFIX, settings.url)
        }
    }
}