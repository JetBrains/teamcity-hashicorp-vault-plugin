package org.jetbrains.teamcity.vault

data class VaultFeatureSettings(val url: String, val verifySsl: Boolean, val roleId: String, val secretId: String, val enabled: Boolean = true) {
    constructor(map: Map<String, String>) : this(
            map[VaultConstants.FeatureSettings.URL] ?: "",
            map[VaultConstants.FeatureSettings.VERIFY_SSL]?.toBoolean() ?: false,
            map[VaultConstants.FeatureSettings.ROLE_ID] ?: "",
            map[VaultConstants.FeatureSettings.SECRET_ID] ?: "",
            map[VaultConstants.FeatureSettings.ENABLED]?.toBoolean() ?: false

    )

    fun toMap(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.VERIFY_SSL] = verifySsl.toString()
        map[VaultConstants.FeatureSettings.ROLE_ID] = roleId
        map[VaultConstants.FeatureSettings.SECRET_ID] = secretId
        map[VaultConstants.FeatureSettings.ENABLED] = enabled.toString()
    }

    companion object {
        fun getDefaultParameters(): Map<String, String> {
            return mapOf(
                    VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT to VaultConstants.FeatureSettings.AGENT_SUPPORT_REQUIREMENT_VALUE,
                    VaultConstants.FeatureSettings.URL to "http://localhost:8200",
                    VaultConstants.FeatureSettings.VERIFY_SSL to true.toString(),
                    VaultConstants.FeatureSettings.ENABLED to true.toString()
            )
        }
    }

    fun ensureEnabled(): VaultFeatureSettings {
        if (enabled) return this
        val map = HashMap<String, String>()
        this.toMap(map)
        map[VaultConstants.FeatureSettings.ENABLED] = true.toString()
        return VaultFeatureSettings(map)
    }
}