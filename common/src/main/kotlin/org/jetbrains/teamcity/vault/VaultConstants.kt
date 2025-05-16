
package org.jetbrains.teamcity.vault

import org.springframework.vault.authentication.AppRoleAuthenticationOptions
import org.springframework.vault.authentication.GcpIamCredentialsAuthenticationOptions

@Suppress("MayBeConstant")
object VaultConstants {
    val FEATURE_SUPPORTED_AGENT_PARAMETER = "teamcity.vault.supported"

    const val PARAMETER_PREFIX = "teamcity.vault"
    const val TOKEN_REFRESH_TIMEOUT_PROPERTY_SUFFIX = ".token.refresh.timeout"
    const val VAULT_CLIENT_CONNECTION_TIMEOUT_SECONDS = ".client.connection.timeoutInSeconds"
    const val VAULT_CLIENT_READ_TIMEOUT_SECONDS = ".client.read.timeoutInSeconds"
    const val LEGACY_REFERENCES_USED_SUFFIX = ".legacy.references.used"

    const val PARAMETER_TYPE = "hashicorp-vault"
    const val PROJECT_ID = "projectId"
    const val BUILD_TYPE_ID = "buildTypeId"
    const val CONNECTION_ID = "connectionFeatureId"

    const val VAULT_PARAMETERS_FETCHED_SUFFIX = "parameters.fetched"


    @JvmField val VAULT_PARAMETER_PREFIX = "vault:"

    object AgentEnvironment {
        val VAULT_TOKEN = "VAULT_TOKEN"
        val VAULT_ADDR = "VAULT_ADDR"
    }

    object FeatureSettings {
        @JvmField val FEATURE_TYPE = "teamcity-vault"

        // Feature settings
        @JvmField val ID = "namespace"
        @JvmField val DEFAULT_ID = ""

        @JvmField val USER_DEFINED_ID_PARAM = "id"

        @JvmField val VAULT_NAMESPACE = "vault-namespace"
        @JvmField val DEFAULT_VAULT_NAMESPACE = ""
        @JvmField val EMPTY_NAMESPACE = "emptyNameSpace"

        @JvmField val URL = "url"

        @JvmField val ENDPOINT = "endpoint"
        @JvmField val DEFAULT_ENDPOINT_PATH = AppRoleAuthenticationOptions.DEFAULT_APPROLE_AUTHENTICATION_PATH

        @JvmField val ROLE_ID = "role-id"
        @JvmField val SECRET_ID = "secure:secret-id"

        @JvmField val USERNAME = "username"
        @JvmField val PASSWORD = "secure:password"
        @JvmField val PATH = "path"

        @JvmField val AUTH_METHOD = "auth-method"
        @JvmField val AUTH_METHOD_APPROLE = AuthMethod.APPROLE.id
        @JvmField val AUTH_METHOD_LDAP = AuthMethod.LDAP.id
        @JvmField val DEFAULT_AUTH_METHOD = AuthMethod.APPROLE.id
        @JvmField val AUTH_METHOD_GCP_IAM = AuthMethod.GCP_IAM.id
        @JvmField val WRAPPED_TOKEN = "wrapped-token"

        @JvmField val DISPLAY_NAME = "displayName"

        @JvmField val AGENT_SUPPORT_REQUIREMENT = "teamcity.vault.requirement"
        @JvmField val AGENT_SUPPORT_REQUIREMENT_VALUE = "%$FEATURE_SUPPORTED_AGENT_PARAMETER%"

        @JvmField val CONNECTIONS_DETAIL = "teamcity.vault.connection"

        @JvmField val GCP_ROLE = "gcp-role"
        @JvmField val GCP_SERVICE_ACCOUNT = "gcp-service-account"
        @JvmField val GCP_ENDOINT_PATH = "gcp-endpoint-path"
        @JvmField val DEFAULT_GCP_ENPOINT_PATH = GcpIamCredentialsAuthenticationOptions.DEFAULT_GCP_AUTHENTICATION_PATH
    }

    object BehaviourParameters {
        val ExposeEnvSuffix = ".set.env"
    }

    object ParameterSettings {
        const val NAMESPACE_NOT_SELECTED_VALUE = "parameter-namespace-not-selected-please-select"
        const val VAULT_ID = "teamcity_hashicorp_vault_namespace"
        const val VAULT_QUERY = "teamcity_hashicorp_vault_vaultQuery"
    }

    object ControllerSettings {
        const val URL = "hashicorp-vault/connection"
        const val WRAP_TOKEN_PATH = "/token/v1"
    }


    // Special values
    val SPECIAL_FAILED_TO_FETCH = "FAILED_TO_FETCH"
    val SPECIAL_VALUES = setOf<String>(SPECIAL_FAILED_TO_FETCH)

    val GCP_IAM_PROPERTIES_SET: Set<String> by lazy {
        setOf(
            FeatureSettings.GCP_ROLE,
            FeatureSettings.GCP_ENDOINT_PATH,
            FeatureSettings.GCP_SERVICE_ACCOUNT
        )
    }

    val APPROLE_PROPERTIES_SET: Set<String> by lazy {
        setOf(
            FeatureSettings.ENDPOINT,
            FeatureSettings.ROLE_ID,
            FeatureSettings.SECRET_ID
        )
    }

    val LDAP_PROPERTIES_SET: Set<String> by lazy {
        setOf(
            FeatureSettings.USERNAME,
            FeatureSettings.PASSWORD,
            FeatureSettings.PATH
        )
    }

    object FeatureFlags {
        /**
         * Feature flag that enables/disables write engines support, when only a write operation is possible to retrieve/generate a secret (for example, k8s engine).
         */
        const val FEATURE_ENABLE_WRITE_ENGINES: String = "teamcity.internal.vault.write.engines.enabled"
    }
}