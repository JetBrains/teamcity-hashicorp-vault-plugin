package org.jetbrains.teamcity.vault

import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings.DEFAULT_ID
import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings

data class VaultParameterSettings(
    val namespace: String,
    val vaultQuery: String
) {
    fun toMap(): Map<String, String> = mapOf(
        ParameterSettings.VAULT_ID to namespace,
        ParameterSettings.VAULT_QUERY to vaultQuery
    )

    companion object {
        fun getInvalidProperties(arguments: Map<String, String>): Map<String, String> {
            val invalids = mutableMapOf<String, String>()
            if (arguments[ParameterSettings.VAULT_QUERY].isNullOrBlank()) {
                invalids[ParameterSettings.VAULT_QUERY] = "The vault query is required"
            }

            if (arguments[ParameterSettings.VAULT_ID] == ParameterSettings.NAMESPACE_NOT_SELECTED_VALUE) {
                invalids[ParameterSettings.VAULT_ID] = "Please choose a vault connection"
            }

            return invalids
        }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(arguments: Map<String, String>): VaultParameterSettings {
            val invalids = getInvalidProperties(arguments)
            require(invalids.isEmpty()) { invalids.firstNotNullOf { it.value } }

            val namespace = arguments[ParameterSettings.VAULT_ID] ?: DEFAULT_ID
            val query = arguments.getValue(ParameterSettings.VAULT_QUERY)
            return VaultParameterSettings(namespace, query)
        }

    }
}