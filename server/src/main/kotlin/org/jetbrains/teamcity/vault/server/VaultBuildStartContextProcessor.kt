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

class VaultBuildStartContextProcessor(private val connector: VaultConnector) : BuildStartContextProcessor {
    companion object {
        val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + VaultBuildStartContextProcessor::class.java.name)!!

        private fun getFeature(build: SBuild): List<VaultFeatureSettings> {
            val buildType = build.buildType ?: return emptyList()

            val connectionFeatures = buildType.project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
                VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
            }
            val vaultFeatures = connectionFeatures.map {
                VaultFeatureSettings(it.parameters)
            }
            return vaultFeatures
        }

        internal fun isShouldEnableVaultIntegration(build: SBuild): Boolean {
            val parameters = build.buildOwnParameters
            val features = getFeature(build)
            val prefixes = features.map { it.parameterPrefix }.toSet()
            return isShouldSetEnvParameters(parameters)
                    // Slowest part:
                    || VaultReferencesUtil.hasReferences(build.parametersProvider.all, prefixes)
        }

    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build

        val settingsList = getFeature(build)
        if(settingsList.isEmpty())
            return;

        if (!isShouldEnableVaultIntegration(build)) {
            LOG.debug("There's no need to fetch vault parameter for build $build")
            return
        }

        val wrappedTokens: List<Pair<String,String>> = try {
            settingsList.map {
                Pair(it.parameterPrefix,connector.requestWrappedToken(build, it))
            }
        } catch (e: Throwable) {
            val message = "Failed to fetch HashiCorp Vault wrapped token: ${e.message}"
            LOG.warn(message, e)
            build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}", "VaultConnection",
                    message + ": " + e.toString() + ", see teamcity-server.log for details"
            ))
            return
        }

        wrappedTokens.forEach { (prefix: String, token: String) ->
            context.addSharedParameter(VaultConstants.WRAPPED_TOKEN_PROPERTY + "." + prefix, token)
        }
        settingsList.forEach { settings: VaultFeatureSettings ->
            context.addSharedParameter(VaultConstants.URL_PROPERTY + "." + settings.parameterPrefix, settings.url)
        }
    }
}