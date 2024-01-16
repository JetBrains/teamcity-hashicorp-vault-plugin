
package org.jetbrains.teamcity.vault

data class VaultQuery(val vaultPath: String, val jsonPath: String? = null) {
    companion object {
        @JvmStatic
        fun extract(path: String): VaultQuery {
            val split = path.split("!/", limit = 2)
            if (split.size == 1) {
                return VaultQuery(split[0], null)
            }
            return VaultQuery(split[0], split[1])
        }
    }

    val full: String get() = (if (jsonPath == null) vaultPath else vaultPath + "!/" + jsonPath).ensureHasPrefix("/")
}