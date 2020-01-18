/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider
import org.jetbrains.teamcity.vault.*

class VaultParametersProvider : AbstractBuildParametersProvider() {
    companion object {
        internal fun isFeatureEnabled(build: SBuild): Boolean {
            val buildType = build.buildType ?: return false
            val project = buildType.project

            // It's faster than asking OAuthConectionsManager
            if (project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).any {
                        VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
                    }) return true

            return false
        }

    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        val buildType = build.buildType ?: return emptyList()
        if (build.isFinished) return emptyList()

        if (!isFeatureEnabled(build)) return emptyList()

        val exposed = HashSet<String>()
        val connectionFeatures = buildType.project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
            VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
        }
        val vaultFeatures = connectionFeatures.map {
            VaultFeatureSettings(it.parameters)
        }
        val parameters = build.buildOwnParameters
        vaultFeatures.forEach { feature: VaultFeatureSettings ->

            if (isShouldSetEnvParameters(parameters, feature.namespace)) {
                val envPrefix = getEnvPrefix(feature.namespace)

                exposed += Constants.ENV_PREFIX + envPrefix + VaultConstants.AgentEnvironment.VAULT_TOKEN
                exposed += Constants.ENV_PREFIX + envPrefix + VaultConstants.AgentEnvironment.VAULT_ADDR
            }
        }
        VaultReferencesUtil.collect(parameters, exposed, vaultFeatures.map { feature -> feature.namespace })
        return exposed
    }
}

