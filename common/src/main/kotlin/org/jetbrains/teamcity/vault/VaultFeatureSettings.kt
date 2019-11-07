/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under  the Apache License, Version 2.0 (the "License");
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

data class VaultFeatureSettings(val namespace: String, val url: String, val endpoint: String, val roleId: String, val secretId: String, val backoffPeriod: Long, val maxAttempts: Int, val failOnError: Boolean = true) {

    constructor(namespace: String, url: String, backoffPeriod: Long, maxAttempts: Int, failOnError: Boolean) : this(namespace, url, VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH, "", "", backoffPeriod, maxAttempts, failOnError)

    constructor(url: String, roleId: String, secretId: String, backoffPeriod: Long, maxAttempts: Int) : this(VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE, url, VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH, roleId, secretId, backoffPeriod, maxAttempts)

    constructor(map: Map<String, String>) : this(
            map[VaultConstants.FeatureSettings.NAMESPACE] ?: VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
            map[VaultConstants.FeatureSettings.URL] ?: "",
            // Default value to convert from previous config versions
            (map[VaultConstants.FeatureSettings.ENDPOINT] ?: VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH).removePrefix("/"),
            map[VaultConstants.FeatureSettings.ROLE_ID] ?: "",
            map[VaultConstants.FeatureSettings.SECRET_ID] ?: "",
            map[VaultConstants.FeatureSettings.BACKOFF_PERIOD]?.toLongOrNull() ?: VaultConstants.FeatureSettings.DEFAULT_BACKOFF_PERIOD,
            map[VaultConstants.FeatureSettings.MAX_ATTEMPTS]?.toIntOrNull() ?: VaultConstants.FeatureSettings.DEFAULT_MAX_ATTEMPTS,
            map[VaultConstants.FeatureSettings.FAIL_ON_ERROR]?.toBoolean() ?: true
    )

    fun toMap(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.NAMESPACE] = namespace
        map[VaultConstants.FeatureSettings.ENDPOINT] = getNormalizedEndpoint()
        map[VaultConstants.FeatureSettings.ROLE_ID] = roleId
        map[VaultConstants.FeatureSettings.SECRET_ID] = secretId
        map[VaultConstants.FeatureSettings.BACKOFF_PERIOD] = backoffPeriod.toString()
        map[VaultConstants.FeatureSettings.MAX_ATTEMPTS] = maxAttempts.toString()
        map[VaultConstants.FeatureSettings.FAIL_ON_ERROR] = failOnError.toString()
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
                    VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE,
                    VaultConstants.FeatureSettings.ENDPOINT to VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH,
                    VaultConstants.FeatureSettings.URL to "http://localhost:8200",
                    VaultConstants.FeatureSettings.BACKOFF_PERIOD to VaultConstants.FeatureSettings.DEFAULT_BACKOFF_PERIOD.toString(),
                    VaultConstants.FeatureSettings.MAX_ATTEMPTS to VaultConstants.FeatureSettings.DEFAULT_MAX_ATTEMPTS.toString()
            )
        }
    }
}