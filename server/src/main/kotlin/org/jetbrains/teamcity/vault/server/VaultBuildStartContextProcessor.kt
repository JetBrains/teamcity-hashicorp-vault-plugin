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
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.*

class VaultBuildStartContextProcessor(private val connector: VaultConnector) : BuildStartContextProcessor {
    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)!!

        private fun getFeatures(build: SRunningBuild, reportProblems: Boolean): List<VaultFeatureSettings> {
            val buildType = build.buildType ?: return emptyList()

            val connectionFeatures = buildType.project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
                VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
            }

            // Two features with same prefix cannot coexist in same project
            // Though it's possible to override feature with same prefix in subproject
            val projectToFeaturePairs = connectionFeatures.map {
                it.projectId to VaultFeatureSettings(it.parameters)
            }

            if (reportProblems) {
                projectToFeaturePairs.groupBy({ it.first }, { it.second }).forEach { pid, features ->
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
            val vaultFeatures = projectToFeaturePairs.map { it.second }

            return vaultFeatures.groupBy { it.namespace }.map { (_, v) -> v.first() }
        }

        internal fun isShouldEnableVaultIntegration(build: SBuild, settings: VaultFeatureSettings): Boolean {
            val parameters = build.buildOwnParameters
            return isShouldSetEnvParameters(parameters, settings.namespace)
                    // Slowest part:
                    || VaultReferencesUtil.hasReferences(build.parametersProvider.all, listOf(settings.namespace))
        }

    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build

        val settingsList = getFeatures(build, true)
        if (settingsList.isEmpty())
            return

        settingsList.map { settings ->
            val ns = if (isDefault(settings.namespace)) "" else " ('${settings.namespace}' namespace)"
            if (!isShouldEnableVaultIntegration(build, settings)) {
                LOG.debug("There's no need to fetch HashiCorp Vault$ns parameter for build $build")
                return@map
            }

            val wrappedToken: String = try {
                connector.requestWrappedToken(build, settings)
            } catch (e: Throwable) {
                val message = "Failed to fetch HashiCorp Vault$ns wrapped token: ${e.message}"
                LOG.warn(message, e)
                val msg = message + ": " + e.toString() + ", see teamcity-server.log for details"
                build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${settings.namespace}", "VaultConnection", msg))
                if (settings.failOnError) {
                    build.stop(null, msg)
                }
                return@map
            }

            context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.FAIL_ON_ERROR_PROPERTY_SUFFIX), settings.failOnError.toString())
            context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.MAX_ATTEMPTS_PERIOD_PROPERTY_SUFFIX), settings.maxAttempts.toString())
            context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.BACKOFF_PERIOD_PROPERTY_SUFFIX), settings.backoffPeriod.toString())
            context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.WRAPPED_TOKEN_PROPERTY_SUFFIX), wrappedToken)
            context.addSharedParameter(getVaultParameterName(settings.namespace, VaultConstants.URL_PROPERTY_SUFFIX), settings.url)
        }
    }
}