
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.serverSide.TeamCityProperties

data class VaultQuery(val vaultPath: String, val jsonPath: String? = null, val isWriteEngine: Boolean? = false) {
    companion object {
        const val WRITE_PREFIX: String = "write:";
        @JvmStatic
        fun extract(path: String): VaultQuery {
            val isWriteEngine = TeamCityProperties.getBoolean(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES) && path.startsWith(WRITE_PREFIX)
            val split = path.substringAfter(WRITE_PREFIX).split("!/", limit = 2)
            if (split.size == 1) {
                return VaultQuery(split[0], null, isWriteEngine)
            }
            return VaultQuery(split[0], split[1], isWriteEngine)
        }
    }

    val full: String get() = (if (jsonPath == null) vaultPath else vaultPath + "!/" + jsonPath).ensureHasPrefix("/")
}