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
package org.jetbrains.teamcity.vault

enum class AuthMethod(val id: String) {
    APPROLE("approle"),
    AWS_IAM("iam"),
    LDAP("ldap"),
}

sealed class Auth(val method: AuthMethod) {
    data class AppRoleAuthServer(val endpoint: String, val roleId: String, val secretId: String) : Auth(AuthMethod.APPROLE) {
        fun getNormalizedEndpoint(): String {
            var x = endpoint
            while (x.startsWith("/")) {
                x = x.removePrefix("/")
            }
            return x
        }

        override fun toMap(map: MutableMap<String, String>) {
            map[VaultConstants.FeatureSettings.AUTH_METHOD] = method.id
            map[VaultConstants.FeatureSettings.ENDPOINT] = getNormalizedEndpoint()
            map[VaultConstants.FeatureSettings.ROLE_ID] = roleId
            map[VaultConstants.FeatureSettings.SECRET_ID] = secretId
        }
    }

    data class AppRoleAuthAgent(val wrappedToken: String) : Auth(AuthMethod.APPROLE) {
        override fun toMap(map: MutableMap<String, String>) {
            map[VaultConstants.FeatureSettings.AUTH_METHOD] = method.id
        }
    }

    object AwsIam : Auth(AuthMethod.AWS_IAM) {
        override fun toMap(map: MutableMap<String, String>) {
            map[VaultConstants.FeatureSettings.AUTH_METHOD] = method.id
        }
    }

    data class LdapServer(val username: String, val password: String, val path: String) : Auth(AuthMethod.LDAP) {
        override fun toMap(map: MutableMap<String, String>) {
            map[VaultConstants.FeatureSettings.AUTH_METHOD] = method.id
            map[VaultConstants.FeatureSettings.USERNAME] = username
            map[VaultConstants.FeatureSettings.PASSWORD] = password
            map[VaultConstants.FeatureSettings.PATH] = path
        }
    }

    data class LdapAgent(val wrappedToken: String) : Auth(AuthMethod.LDAP) {
        override fun toMap(map: MutableMap<String, String>) {
            map[VaultConstants.FeatureSettings.AUTH_METHOD] = method.id
        }
    }

    abstract fun toMap(map: MutableMap<String, String>)

    companion object {
        fun fromProperties(map: Map<String, String>): Auth {
            val kind = (map[VaultConstants.FeatureSettings.AUTH_METHOD]
                    ?: VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD)
            return when (kind) {
                AuthMethod.APPROLE.id -> {
                    AppRoleAuthServer(
                            // Default value to convert from previous config versions
                            (map[VaultConstants.FeatureSettings.ENDPOINT]
                                    ?: VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH).removePrefix("/"),
                            map[VaultConstants.FeatureSettings.ROLE_ID] ?: "",
                            map[VaultConstants.FeatureSettings.SECRET_ID] ?: ""
                    )
                }
                AuthMethod.AWS_IAM.id -> AwsIam
                AuthMethod.LDAP.id -> LdapServer(
                        map[VaultConstants.FeatureSettings.USERNAME] ?: "",
                        map[VaultConstants.FeatureSettings.PASSWORD] ?: "",
                        map[VaultConstants.FeatureSettings.PATH] ?: ""
                )
                else -> error("Unexpected auth method '$kind'")
            }
        }

        fun fromSharedParameters(parameters: Map<String, String>, namespace: String): Auth {
            val kind = parameters[getVaultParameterName(namespace, VaultConstants.VAULT_AUTH_PROPERTY_SUFFIX)]
                    ?: VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD
            return when (kind) {
                AuthMethod.APPROLE.id -> AppRoleAuthAgent(parameters.getOrDefault(getVaultParameterName(namespace, VaultConstants.WRAPPED_TOKEN_PROPERTY_SUFFIX), ""))
                AuthMethod.AWS_IAM.id -> AwsIam
                AuthMethod.LDAP.id -> LdapAgent(parameters.getOrDefault(getVaultParameterName(namespace, VaultConstants.WRAPPED_TOKEN_PROPERTY_SUFFIX), ""))

                // default
                else -> error("Unexpected auth method '$kind'")
            }
        }
    }
}

data class VaultFeatureSettings(val namespace: String, val url: String, val vaultNamespace: String, val failOnError: Boolean = true, val auth: Auth) {

    constructor(url: String, vaultNamespace: String) : this(VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE, url, vaultNamespace, true, Auth.fromProperties(emptyMap()))

    constructor(namespace: String, url: String, vaultNamespace: String, endpoint: String, roleId: String, secretId: String, failOnError: Boolean) : this(namespace, url, vaultNamespace, failOnError, Auth.AppRoleAuthServer(endpoint, roleId, secretId))

    constructor(map: Map<String, String>) : this(
            map[VaultConstants.FeatureSettings.NAMESPACE] ?: VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
            map[VaultConstants.FeatureSettings.URL] ?: "",
            map[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
                    ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
            map[VaultConstants.FeatureSettings.FAIL_ON_ERROR]?.toBoolean() ?: false,
            Auth.fromProperties(map)
    )

    fun toFeatureProperties(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.NAMESPACE] = namespace
        map[VaultConstants.FeatureSettings.VAULT_NAMESPACE] = vaultNamespace
        map[VaultConstants.FeatureSettings.FAIL_ON_ERROR] = failOnError.toString()
        auth.toMap(map)
    }

    fun toSharedParameters(): Map<String, String> {
        return mapOf(
                VaultConstants.FAIL_ON_ERROR_PROPERTY_SUFFIX to failOnError.toString(),
                VaultConstants.URL_PROPERTY_SUFFIX to url,
                VaultConstants.VAULT_NAMESPACE_PROPERTY_SUFFIX to vaultNamespace,
                VaultConstants.VAULT_AUTH_PROPERTY_SUFFIX to auth.method.id
        ).mapKeys { getVaultParameterName(namespace, it.key) }
    }

    companion object {
        fun getDefaultParameters(): Map<String, String> {
            return mapOf(
                    VaultConstants.FeatureSettings.NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
                    VaultConstants.FeatureSettings.VAULT_NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
                    VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE,
                    VaultConstants.FeatureSettings.AUTH_METHOD to VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD,
                    VaultConstants.FeatureSettings.FAIL_ON_ERROR to "true",
                    VaultConstants.FeatureSettings.ENDPOINT to VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH,
                    VaultConstants.FeatureSettings.URL to "http://localhost:8200"
            )
        }

        fun fromSharedParameters(parameters: Map<String, String>, namespace: String): VaultFeatureSettings {
            val url = parameters[getVaultParameterName(namespace, VaultConstants.URL_PROPERTY_SUFFIX)] ?: ""
            val vaultNamespace = parameters[getVaultParameterName(namespace, VaultConstants.VAULT_NAMESPACE_PROPERTY_SUFFIX)]
                    ?: ""
            val failOnError = parameters[getVaultParameterName(namespace, VaultConstants.FAIL_ON_ERROR_PROPERTY_SUFFIX)]?.toBoolean()
                    ?: false

            return VaultFeatureSettings(namespace, url, vaultNamespace, failOnError, Auth.fromSharedParameters(parameters, namespace))
        }
    }
}
