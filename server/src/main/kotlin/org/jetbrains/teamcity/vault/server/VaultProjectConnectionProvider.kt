package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthProvider
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class VaultProjectConnectionProvider(private val descriptor: PluginDescriptor) : OAuthProvider() {
    override fun getType(): String = VaultConstants.FeatureSettings.FEATURE_TYPE

    override fun getDisplayName(): String = "HashiCorp Vault"

    override fun describeConnection(connection: OAuthConnectionDescriptor): String {
        val settings = VaultFeatureSettings(connection.parameters)
        return "Connection to HashiCorp Vault secrets server at ${settings.url}"
    }

    override fun getDefaultProperties(): Map<String, String> {
        return mapOf(
                VaultConstants.FeatureSettings.URL to "http://localhost:8200"
        )
    }

    override fun getEditParametersUrl(): String? {
        return descriptor.getPluginResourcesPath("editProjectConnectionVault.jsp");
    }

    override fun getPropertiesProcessor(): PropertiesProcessor? {
        return VaultBuildFeature.getParametersProcessor()
    }
}