package org.jetbrains.teamcity.vault

object VaultConstants {
    val FEATURE_SUPPORTED_AGENT_PARAMETER = "teamcity.vault.supported"
    val PROJECT_FEATURE_TAB_SUPPORTED_PARAMETER = "teamcity.vault.supported.in.project"

    val URL_PROPERTY = "teamcity.vault.url"
    val WRAPPED_TOKEN_PROPERTY = "teamcity.vault.wrapped.token"

    @JvmField val VAULT_PARAMETER_PREFIX = "vault:"

    object AgentEnvironment {
        val VAULT_TOKEN = "VAULT_TOKEN"
        val VAULT_ADDR = "VAULT_ADDR"
    }

    object FeatureSettings {
        @JvmField val FEATURE_TYPE = "teamcity-vault"

        // Feature settings
        @JvmField val URL = "url"

        @JvmField val ROLE_ID = "role-id"
        @JvmField val SECRET_ID = "secure:secret-id"

        @JvmField val AGENT_SUPPORT_REQUIREMENT = "teamcity.vault.requirement"
        @JvmField val AGENT_SUPPORT_REQUIREMENT_VALUE = "%$FEATURE_SUPPORTED_AGENT_PARAMETER%"
    }

    object BehaviourParameters {
        val ExposeEnvParameters = "teamcity.vault.set.env"
    }


    // Special values
    val SPECIAL_FAILED_TO_FETCH = "FAILED_TO_FETCH"
    val SPECIAL_VALUES = setOf<String>(SPECIAL_FAILED_TO_FETCH)
}

