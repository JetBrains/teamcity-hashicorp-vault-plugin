package org.jetbrains.teamcity.vault

data class VaultFeatureSettings(val url: String, val verifySsl: Boolean, val roleId: String, val secretId: String) {
    constructor(map: Map<String, String>) : this(
            map[VaultConstants.FeatureSettings.URL] ?: "",
            map[VaultConstants.FeatureSettings.VERIFY_SSL]?.toBoolean() ?: false,
            map[VaultConstants.FeatureSettings.ROLE_ID] ?: "",
            map[VaultConstants.FeatureSettings.SECRET_ID] ?: ""
    )

    fun toMap(map: MutableMap<String, String>) {
        map[VaultConstants.FeatureSettings.URL] = url
        map[VaultConstants.FeatureSettings.VERIFY_SSL] = verifySsl.toString()
        map[VaultConstants.FeatureSettings.ROLE_ID] = roleId
        map[VaultConstants.FeatureSettings.SECRET_ID] = secretId
    }
}