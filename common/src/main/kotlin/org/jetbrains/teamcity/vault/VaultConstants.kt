package org.jetbrains.teamcity.vault

object VaultConstants {
    val FEATURE_SUPPORTED_AGENT_PARAMETER = "teamcity.vault.supported"
    @JvmField val FEATURE_TYPE = "teamcity-vault"

    val URL_PROPERTY = "teamcity.vault.url"
    val WRAPPED_TOKEN_PROPERTY = "teamcity.vault.wrapped.token"

    @JvmField val VAULT_PARAMETER_PREFIX = "vault:"

    // Agent-side parameters:
    val AGENT_CONFIG_PROP = "teamcity.vault.token"
    val AGENT_ENV_PROP = "VAULT_TOKEN"

    // Feature settings
    @JvmField val URL = "url"
    @JvmField val VERIFY_SSL = "verify-ssl"

    @JvmField val ROLE_ID = "role-id"
    @JvmField val SECRET_ID = "secure:secret-id"

    // Special values
    val SPECIAL_EMULATTED = "EMULATED"
    val SPECIAL_FAILED_TO_FETCH = "FAILED_TO_FETCH"
    val SPECIAL_VALUES = setOf<String>(SPECIAL_EMULATTED, SPECIAL_FAILED_TO_FETCH)
}

