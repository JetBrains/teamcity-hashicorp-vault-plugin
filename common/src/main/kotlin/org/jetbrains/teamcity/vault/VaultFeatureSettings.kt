
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor

enum class AuthMethod(val id: String) {
    APPROLE("approle"),
    LDAP("ldap"),
    GCP_IAM("gcp-iam"),
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
            map[VaultConstants.FeatureSettings.WRAPPED_TOKEN] = wrappedToken
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
            map[VaultConstants.FeatureSettings.WRAPPED_TOKEN] = wrappedToken
        }
    }

    data class GcpIamAuth(val role: String, val serviceAccount: String, val endpointPath: String) : Auth(AuthMethod.GCP_IAM) {
        override fun toMap(map: MutableMap<String, String>) {
            map[VaultConstants.FeatureSettings.AUTH_METHOD] = method.id
            map[VaultConstants.FeatureSettings.GCP_ROLE] = role
            map[VaultConstants.FeatureSettings.GCP_SERVICE_ACCOUNT] = serviceAccount
            map[VaultConstants.FeatureSettings.GCP_ENDOINT_PATH] = endpointPath
        }
    }

    abstract fun toMap(map: MutableMap<String, String>)

    companion object {
        fun getServerAuthFromProperties(map: Map<String, String>): Auth {
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

                AuthMethod.LDAP.id -> LdapServer(
                        map[VaultConstants.FeatureSettings.USERNAME] ?: "",
                        map[VaultConstants.FeatureSettings.PASSWORD] ?: "",
                        map[VaultConstants.FeatureSettings.PATH] ?: ""
                )

                AuthMethod.GCP_IAM.id -> toGcpIamProperties(map)

                else -> error("Unexpected auth method '$kind'")
            }
        }

        fun getAgentAuthFromProperties(map: Map<String, String>): Auth {
            val kind = (map[VaultConstants.FeatureSettings.AUTH_METHOD]
                ?: VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD)
            return when (kind) {
                AuthMethod.APPROLE.id -> {
                    AppRoleAuthAgent(map[VaultConstants.FeatureSettings.WRAPPED_TOKEN] ?: "")
                }

                AuthMethod.LDAP.id -> LdapAgent(
                    map[VaultConstants.FeatureSettings.WRAPPED_TOKEN] ?: ""
                )

                AuthMethod.GCP_IAM.id -> toGcpIamProperties(map)

                else -> error("Unexpected auth method '$kind'")
            }
        }

        private fun toGcpIamProperties(map: Map<String, String>): GcpIamAuth {
            return GcpIamAuth(
            map[VaultConstants.FeatureSettings.GCP_ROLE] ?: "",
            map[VaultConstants.FeatureSettings.GCP_SERVICE_ACCOUNT] ?: "",
            map[VaultConstants.FeatureSettings.GCP_ENDOINT_PATH] ?: VaultConstants.FeatureSettings.DEFAULT_GCP_ENPOINT_PATH
            )
        }
    }
}

data class VaultFeatureSettings(val id: String, val url: String, val vaultNamespace: String, val auth: Auth, val displayName: String? = null) {
    constructor(url: String, vaultNamespace: String) : this(
        VaultConstants.FeatureSettings.DEFAULT_ID,
        url,
        vaultNamespace,
        Auth.getServerAuthFromProperties(emptyMap())
    )

    constructor(namespace: String, url: String, vaultNamespace: String, endpoint: String, roleId: String, secretId: String) : this(
        namespace,
        url,
        vaultNamespace,
        Auth.AppRoleAuthServer(endpoint, roleId, secretId)
    )

    constructor(map: Map<String, String>) : this(
        map[VaultConstants.FeatureSettings.ID] ?: VaultConstants.FeatureSettings.DEFAULT_ID,
        map[VaultConstants.FeatureSettings.URL] ?: "",
        map[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
            ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
        Auth.getServerAuthFromProperties(map),
        map[VaultConstants.FeatureSettings.DISPLAY_NAME]
    )

    constructor(projectFeature: SProjectFeatureDescriptor) : this(
        projectFeature.parameters[VaultConstants.FeatureSettings.ID] ?: projectFeature.id,
        projectFeature.parameters[VaultConstants.FeatureSettings.URL] ?: "",
        projectFeature.parameters[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
            ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
        Auth.getServerAuthFromProperties(projectFeature.parameters),
        projectFeature.parameters[VaultConstants.FeatureSettings.DISPLAY_NAME]
    )


    fun toFeatureProperties(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.VAULT_NAMESPACE] = vaultNamespace
        auth.toMap(map)
    }

    fun toFeatureProperties(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        toFeatureProperties(map)
        return map
    }

    companion object {
        fun getDefaultParameters(): Map<String, String> {
            return mapOf(
                VaultConstants.FeatureSettings.VAULT_NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
                VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE,
                VaultConstants.FeatureSettings.AUTH_METHOD to VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD,
                VaultConstants.FeatureSettings.ENDPOINT to VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH,
                VaultConstants.FeatureSettings.URL to "http://localhost:8200",
                VaultConstants.FeatureSettings.GCP_ENDOINT_PATH to VaultConstants.FeatureSettings.DEFAULT_GCP_ENPOINT_PATH,
            )
        }

        fun getAgentFeatureFromProperties(map: Map<String, String>): VaultFeatureSettings =
            VaultFeatureSettings(
                map[VaultConstants.FeatureSettings.ID] ?: VaultConstants.FeatureSettings.DEFAULT_ID,
                map[VaultConstants.FeatureSettings.URL] ?: "",
                map[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
                    ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
                Auth.getAgentAuthFromProperties(map)
            )
    }
}