package org.jetbrains.teamcity.vault

object VaultConstants {
    val FEATURE_SUPPORTED_AGENT_PARAMETER = "teamcity.vault.supported"
    @JvmField val FEATURE_TYPE = "teamcity-vault"

    val WRAPPED_TOKEN_PROEPRTY = "teamcity.vault.wrapped.token"

    // Agent-side parameters:
    val AGENT_CONFIG_PROP = "teamcity.vault.token"
    val AGENT_ENV_PROP = "VAULT_TOKEN"

    // Feature settings
    @JvmField val URL = "url"
    @JvmField val VERIFY_SSL = "verify-ssl"

    @JvmField val ROLE_ID = "role-id"
    @JvmField val SECRET_ID = "secure:secret-id"
}

