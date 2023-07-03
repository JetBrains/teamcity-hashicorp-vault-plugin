package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.Auth
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class HashiCorpVaultConnectionResolver(private val connector: VaultConnector) {
    val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + HashiCorpVaultConnectionResolver::class.java.name)

    fun getProjectToConnectionPairs(buildType: SBuildType): List<Pair<String, VaultFeatureSettings>> {
        return getProjectToConnectionPairs(buildType.project)
    }

    fun getProjectToConnectionPairs(project: SProject): List<Pair<String, VaultFeatureSettings>> {
        val connectionFeatures = project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).filter {
            VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
        }

        // Two features with same prefix cannot coexist in same project
        // Though it's possible to override feature with same prefix in subproject
        val projectToFeaturePairs = connectionFeatures.map {
            it.projectId to VaultFeatureSettings(it.parameters)
        }
        return projectToFeaturePairs
    }

    fun serverFeatureSettingsToAgentSettings(settings: VaultFeatureSettings, namespace: String): VaultFeatureSettings =
        if (settings.auth is Auth.AppRoleAuthServer || settings.auth is Auth.LdapServer) {
            val wrappedToken: String = try {
                connector.requestWrappedToken(settings)
            } catch (e: Throwable) {
                val message = "Failed to fetch HashiCorp Vault$namespace wrapped token: ${e.message}"
                LOG.warn(message, e)
                throw RuntimeException(message, e)
            }
            val featureSettingsMap = settings.toFeatureProperties().toMutableMap()
            val agentAuth = when (settings.auth) {
                is Auth.AppRoleAuthServer -> Auth.AppRoleAuthAgent(wrappedToken)
                is Auth.LdapServer -> Auth.LdapAgent(wrappedToken)
                else -> throw RuntimeException("Settings auth shouldn't change")
            }

            agentAuth.toMap(featureSettingsMap)
            VaultFeatureSettings.getAgentFeatureFromProperties(featureSettingsMap)
        } else {
            settings
        }
}