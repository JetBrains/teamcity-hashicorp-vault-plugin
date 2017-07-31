package org.jetbrains.teamcity.vault

data class VaultFeatureSettings(val url: String, val verifySsl: Boolean, val roleId: String, val secretId: String) {
    constructor(map: Map<String, String>) : this(
            map[VaultConstants.URL] ?: "",
            map[VaultConstants.VERIFY_SSL]?.toBoolean() ?: false,
            map[VaultConstants.ROLE_ID] ?: "",
            map[VaultConstants.SECRET_ID] ?: ""
    )

    fun toMap(map: MutableMap<String, String>) {
        map[VaultConstants.URL] = url
        map[VaultConstants.VERIFY_SSL] = verifySsl.toString()
        map[VaultConstants.ROLE_ID] = roleId
        map[VaultConstants.SECRET_ID] = secretId
    }
}