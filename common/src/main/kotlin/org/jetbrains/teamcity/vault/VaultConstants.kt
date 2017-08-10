package org.jetbrains.teamcity.vault

object VaultConstants {
    val FEATURE_SUPPORTED_AGENT_PARAMETER = "teamcity.vault.supported"

    val URL_PROPERTY = "teamcity.vault.url"
    val WRAPPED_TOKEN_PROPERTY = "teamcity.vault.wrapped.token"

    @JvmField val VAULT_PARAMETER_PREFIX = "vault:"

    // Agent-side parameters:
    val AGENT_CONFIG_PROP = "teamcity.vault.token"

    object AgentEnvironment {
        val VAULT_TOKEN = "VAULT_TOKEN"
        val VAULT_ADDR = "VAULT_ADDR"
        val VAULT_SKIP_VERIFY = "VAULT_SKIP_VERIFY"
    }

    object FeatureSettings {
        @JvmField val FEATURE_TYPE = "teamcity-vault"

        // Feature settings
        @JvmField val URL = "url"
        @JvmField val VERIFY_SSL = "verify-ssl"

        @JvmField val ROLE_ID = "role-id"
        @JvmField val SECRET_ID = "secure:secret-id"

        @JvmField val AGENT_SUPPORT_REQUIREMENT = "teamcity.vault.requirement"
        @JvmField val AGENT_SUPPORT_REQUIREMENT_VALUE = "%$FEATURE_SUPPORTED_AGENT_PARAMETER%"
    }

    object BehaviourParameters {
        val ExposeEnvParameters = "teamcity.vault.set.env"
        val ExposeConfigParameters = "teamcity.vault.set.parameter"
    }


    // Special values
    val SPECIAL_EMULATTED = "EMULATED"
    val SPECIAL_FAILED_TO_FETCH = "FAILED_TO_FETCH"
    val SPECIAL_VALUES = setOf<String>(SPECIAL_EMULATTED, SPECIAL_FAILED_TO_FETCH)
}

