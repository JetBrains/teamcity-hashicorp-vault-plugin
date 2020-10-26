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
package org.jetbrains.teamcity.vault

data class VaultFeatureSettings(val namespace: String, val url: String, val vaultNamespace: String, val endpoint: String, val roleId: String, val secretId: String, val failOnError: Boolean = true, val authMethod: String = VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD) {

    constructor(namespace: String, url: String, vaultNamespace: String, failOnError: Boolean) : this(namespace, url, vaultNamespace, VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH, "", "", failOnError)

    constructor(namespace: String, url: String, vaultNamespace: String, failOnError: Boolean, authMethod: String) : this(namespace, url, vaultNamespace, VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH, "", "", failOnError, authMethod)

    constructor(url: String, vaultNamespace: String, roleId: String, secretId: String) : this(VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE, url, vaultNamespace, VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH, roleId, secretId)

    constructor(namespace: String, url: String, vaultNamespace: String, endpoint: String, roleId: String, secretId: String, failOnError: Boolean) : this(namespace, url, vaultNamespace, endpoint, roleId, secretId, failOnError, VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD)

    constructor(map: Map<String, String>) : this(
            map[VaultConstants.FeatureSettings.NAMESPACE] ?: VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
            map[VaultConstants.FeatureSettings.URL] ?: "",
            map[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
                    ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
            // Default value to convert from previous config versions
            (map[VaultConstants.FeatureSettings.ENDPOINT] ?: VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH).removePrefix("/"),
            map[VaultConstants.FeatureSettings.ROLE_ID] ?: "",
            map[VaultConstants.FeatureSettings.SECRET_ID] ?: "",
            map[VaultConstants.FeatureSettings.FAIL_ON_ERROR]?.toBoolean() ?: true,
            map[VaultConstants.FeatureSettings.AUTH_METHOD] ?: VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD
    )

    fun toMap(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.NAMESPACE] = namespace
        map[VaultConstants.FeatureSettings.VAULT_NAMESPACE] = vaultNamespace
        map[VaultConstants.FeatureSettings.ENDPOINT] = getNormalizedEndpoint()
        map[VaultConstants.FeatureSettings.ROLE_ID] = roleId
        map[VaultConstants.FeatureSettings.SECRET_ID] = secretId
        map[VaultConstants.FeatureSettings.FAIL_ON_ERROR] = failOnError.toString()
        map[VaultConstants.FeatureSettings.AUTH_METHOD] = authMethod
    }

    fun getNormalizedEndpoint(): String {
        var x = endpoint
        while (x.startsWith("/")) {
            x = x.removePrefix("/");
        }
        return x
    }

    companion object {
        fun getDefaultParameters(): Map<String, String> {
            return mapOf(
                    VaultConstants.FeatureSettings.NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
                    VaultConstants.FeatureSettings.VAULT_NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
                    VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE,
                    VaultConstants.FeatureSettings.ENDPOINT to VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH,
                    VaultConstants.FeatureSettings.URL to "http://localhost:8200"
            )
        }
    }
}
