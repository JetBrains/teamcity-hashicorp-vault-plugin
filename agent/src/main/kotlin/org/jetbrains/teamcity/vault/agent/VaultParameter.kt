package org.jetbrains.teamcity.vault.agent

data class VaultParameter constructor(val vaultPath: String, val jsonPath: String? = null) {
    companion object {
        fun extract(path: String): VaultParameter {
            val split = path.split("!/", limit = 2)
            if (split.size == 1) {
                return VaultParameter(split[0], null)
            }
            return VaultParameter(split[0], split[1])
        }
    }

    val full: String get() = (if (jsonPath == null) vaultPath else vaultPath + "!/" + jsonPath).ensureHasPrefix("/")
}