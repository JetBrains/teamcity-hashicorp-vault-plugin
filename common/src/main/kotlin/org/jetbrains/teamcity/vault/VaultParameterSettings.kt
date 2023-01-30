package org.jetbrains.teamcity.vault

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.util.StringUtil
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

        @Throws(IllegalArgumentException::class)
        operator fun invoke(arguments: Map<String, String>): VaultParameterSettings {
            if (arguments[ParameterSettings.VAULT_QUERY].isNullOrBlank()) {
                throw IllegalArgumentException("The vault query is required")
            }

            if (arguments[ParameterSettings.NAMESPACE].isNullOrBlank()) {
                throw IllegalArgumentException("Please choose a namespace connection")
            }

            return VaultParameterSettings(
                arguments.getValue(ParameterSettings.NAMESPACE),
                arguments.getValue(ParameterSettings.VAULT_QUERY)
            )
        }

    }
}