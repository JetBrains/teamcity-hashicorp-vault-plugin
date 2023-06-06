package org.jetbrains.teamcity.vault

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings
import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings

data class VaultParameterSettings(
    private val namespace: String,
    val vaultQuery: String
) {
    fun getNamespace(): String = if (namespace == ParameterSettings.DEFAULT_UI_PARAMETER_NAMESPACE) {
        FeatureSettings.DEFAULT_VAULT_NAMESPACE
    } else {
        namespace
    }

    fun toMap(): Map<String, String> = mapOf(
        ParameterSettings.NAMESPACE to namespace,
        ParameterSettings.VAULT_QUERY to vaultQuery
    )

    companion object {

        private val objectMapper by lazy {
            jacksonObjectMapper()
        }

        fun getInvalidProperties(arguments: Map<String, String>): Map<String, String> {
            val invalids = mutableMapOf<String, String>()
            if (arguments[ParameterSettings.VAULT_QUERY].isNullOrBlank()) {
                invalids[ParameterSettings.VAULT_QUERY] = "The vault query is required"
            }

            if (arguments[ParameterSettings.NAMESPACE].isNullOrBlank()) {
                invalids[ParameterSettings.NAMESPACE] = "Please choose a namespace connection"
            }

            return invalids
        }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(arguments: Map<String, String>): VaultParameterSettings {
            val invalids = getInvalidProperties(arguments)
            if (invalids.isNotEmpty()){
                throw IllegalArgumentException(invalids.firstNotNullOf { it.value })
            }

            return VaultParameterSettings(
                arguments.getValue(ParameterSettings.NAMESPACE),
                arguments.getValue(ParameterSettings.VAULT_QUERY)
            )
        }

    }
}