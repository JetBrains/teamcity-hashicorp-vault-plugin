
package org.jetbrains.teamcity.vault

enum class AuthMethod(val id: String) {
    APPROLE("approle"),
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

                else -> error("Unexpected auth method '$kind'")
            }
        }
    }
}

data class VaultFeatureSettings(val namespace: String, val url: String, val vaultNamespace: String, val failOnError: Boolean = true, val auth: Auth) {



    constructor(url: String, vaultNamespace: String) : this(
        VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
        url,
        vaultNamespace,
        true,
        Auth.getServerAuthFromProperties(emptyMap())
    )

    constructor(namespace: String, url: String, vaultNamespace: String, endpoint: String, roleId: String, secretId: String, failOnError: Boolean) : this(
        namespace,
        url,
        vaultNamespace,
        failOnError,
        Auth.AppRoleAuthServer(endpoint, roleId, secretId)
    )

    constructor(map: Map<String, String>) : this(
        map[VaultConstants.FeatureSettings.NAMESPACE] ?: VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
        map[VaultConstants.FeatureSettings.URL] ?: "",
        map[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
            ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
        map[VaultConstants.FeatureSettings.FAIL_ON_ERROR]?.toBoolean() ?: false,
        Auth.getServerAuthFromProperties(map)
    )

    fun toFeatureProperties(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.NAMESPACE] = namespace
        map[VaultConstants.FeatureSettings.VAULT_NAMESPACE] = vaultNamespace
        map[VaultConstants.FeatureSettings.FAIL_ON_ERROR] = failOnError.toString()
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
                VaultConstants.FeatureSettings.NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
                VaultConstants.FeatureSettings.VAULT_NAMESPACE to VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
                VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE,
                VaultConstants.FeatureSettings.AUTH_METHOD to VaultConstants.FeatureSettings.DEFAULT_AUTH_METHOD,
                VaultConstants.FeatureSettings.FAIL_ON_ERROR to "true",
                VaultConstants.FeatureSettings.ENDPOINT to VaultConstants.FeatureSettings.DEFAULT_ENDPOINT_PATH,
                VaultConstants.FeatureSettings.URL to "http://localhost:8200"
            )
        }

        fun getAgentFeatureFromProperties(map: Map<String, String>): VaultFeatureSettings =
            VaultFeatureSettings(
                map[VaultConstants.FeatureSettings.NAMESPACE] ?: VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE,
                map[VaultConstants.FeatureSettings.URL] ?: "",
                map[VaultConstants.FeatureSettings.VAULT_NAMESPACE]
                    ?: VaultConstants.FeatureSettings.DEFAULT_VAULT_NAMESPACE,
                map[VaultConstants.FeatureSettings.FAIL_ON_ERROR]?.toBoolean() ?: false,
                Auth.getAgentAuthFromProperties(map)
            )
    }
}