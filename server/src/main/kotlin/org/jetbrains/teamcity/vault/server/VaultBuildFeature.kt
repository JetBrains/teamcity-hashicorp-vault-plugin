package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.BuildFeature
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class VaultBuildFeature(private val descriptor: PluginDescriptor) : BuildFeature() {
    override fun getType(): String = VaultConstants.FEATURE_TYPE
    override fun getDisplayName(): String = "Vault Connection"

    override fun isMultipleFeaturesPerBuildTypeAllowed() = false
    override fun getPlaceToShow() = PlaceToShow.GENERAL

    override fun getEditParametersUrl(): String = descriptor.getPluginResourcesPath("editFeatureVault.jsp")

    override fun getDefaultParameters(): Map<String, String> {
        return mapOf(
                "teamcity.vault.requirement" to "%teamcity.vault.supported%",
                VaultConstants.URL to "http://localhost:8200",
                VaultConstants.VERIFY_SSL to "true"
        )
    }

    override fun describeParameters(params: MutableMap<String, String>): String {
        val settings = VaultFeatureSettings(params)
        return buildString {
            append("Vault URL: ${settings.url}")
            if (!settings.verifySsl) {
                append("<br>!!! SSL verification disabled. Do not use in prodfuction")
            }
        }
    }

    override fun getParametersProcessor(): PropertiesProcessor? {
        return PropertiesProcessor {
            val errors = ArrayList<InvalidProperty>()
            VaultFeatureSettings(it)
            if (it[VaultConstants.URL].isNullOrBlank()) {
                errors.add(InvalidProperty(VaultConstants.URL, "Should not be empty"))
            }
            if (it[VaultConstants.ROLE_ID].isNullOrBlank()) {
                errors.add(InvalidProperty(VaultConstants.ROLE_ID, "Should not be empty"))
            }
            if (it[VaultConstants.SECRET_ID].isNullOrBlank()) {
                errors.add(InvalidProperty(VaultConstants.SECRET_ID, "Should not be empty"))
            }
            return@PropertiesProcessor errors
        }
    }

    override fun toString(): String {
        return "VaultBuildFeature(${descriptor.pluginName})"
    }

    fun getParametersForAgent(parameters: Map<String, String>): Map<String, String> {
        val result = HashMap(parameters)
        result.remove(VaultConstants.SECRET_ID)
        return result
    }
}