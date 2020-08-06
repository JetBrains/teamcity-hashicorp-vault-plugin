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

import org.jetbrains.teamcity.vault.VaultConstants

class VaultJspKeys {
    val NAMESPACE = VaultConstants.FeatureSettings.NAMESPACE
    val URL = VaultConstants.FeatureSettings.URL
    val VAULT_NAMESPACE = VaultConstants.FeatureSettings.VAULT_NAMESPACE

    val ENDPOINT = VaultConstants.FeatureSettings.ENDPOINT

    val ROLE_ID = VaultConstants.FeatureSettings.ROLE_ID
    val SECRET_ID = VaultConstants.FeatureSettings.SECRET_ID

    val AWS_IAM_AUTH = VaultConstants.FeatureSettings.AWS_IAM_AUTH
    val VAULT_APPROLE_AUTH = VaultConstants.FeatureSettings.VAULT_APPROLE_AUTH
    val VAULT_AUTH = VaultConstants.FeatureSettings.VAULT_AUTH

    val FAIL_ON_ERROR = VaultConstants.FeatureSettings.FAIL_ON_ERROR

    val AGENT_REQUIREMENT = VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT
}
