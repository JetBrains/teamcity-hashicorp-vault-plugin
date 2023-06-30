package org.jetbrains.teamcity.vault

import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE
import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings

data class VaultParameterSettings(
    val namespace: String,
    val vaultQuery: String
) {
    fun toMap(): Map<String, String> = mapOf(
        ParameterSettings.NAMESPACE to namespace,
        ParameterSettings.VAULT_QUERY to vaultQuery
    )

    companion object {
        fun getInvalidProperties(arguments: Map<String, String>): Map<String, String> {
            val invalids = mutableMapOf<String, String>()
            if (arguments[ParameterSettings.VAULT_QUERY].isNullOrBlank()) {
                invalids[ParameterSettings.VAULT_QUERY] = "The vault query is required"
            }

            if (arguments[ParameterSettings.NAMESPACE] == ParameterSettings.NAMESPACE_NOT_SELECTED_VALUE) {
                invalids[ParameterSettings.NAMESPACE] = "Please choose a namespace connection"
            }

            return invalids
        }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(arguments: Map<String, String>): VaultParameterSettings {
            val invalids = getInvalidProperties(arguments)
            require(invalids.isEmpty()) { invalids.firstNotNullOf { it.value } }

            val namespace = arguments[ParameterSettings.NAMESPACE] ?: DEFAULT_PARAMETER_NAMESPACE
            val query = arguments.getValue(ParameterSettings.VAULT_QUERY)
            return VaultParameterSettings(namespace, query)
        }

    }
}