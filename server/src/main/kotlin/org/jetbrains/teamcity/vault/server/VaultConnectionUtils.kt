/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

object VaultConnectionUtils {

    fun groupFeatures(projectToFeaturePairs: List<Pair<String, VaultFeatureSettings>>): List<VaultFeatureSettings> {
        val vaultFeatures = projectToFeaturePairs.map { it.second }

        return vaultFeatures.groupBy { it.id }.map { (_, v) -> v.first() }
    }

    fun getFeaturePairs(project: SProject): List<Pair<String, VaultFeatureSettings>> {
        val connectionFeatures = project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
            VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
        }

        // Two features with same prefix cannot coexist in same project
        // Though it's possible to override feature with same prefix in subproject
        val projectToFeaturePairs = connectionFeatures.map {
            it.projectId to VaultFeatureSettings(it.parameters)
        }
        return projectToFeaturePairs
    }

    fun getFeatures(project: SProject): List<VaultFeatureSettings> = groupFeatures(getFeaturePairs(project))
}