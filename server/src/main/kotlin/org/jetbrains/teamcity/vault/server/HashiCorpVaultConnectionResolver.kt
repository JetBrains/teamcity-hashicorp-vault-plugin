package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.Auth
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings

class HashiCorpVaultConnectionResolver(private val connector: VaultConnector) {
    private val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + HashiCorpVaultConnectionResolver::class.java.name)

    @Suppress("serial")
    class ParameterNamespaceCollisionException(val namespace: String, val projectId: String) : Exception()

    @Throws(ParameterNamespaceCollisionException::class)
    fun getVaultConnections(project: SProject): List<VaultFeatureSettings> {
        return getVaultConnections(project, null)
    }

    @Throws(ParameterNamespaceCollisionException::class)
    fun getVaultConnection(project: SProject, namespace: String): VaultFeatureSettings? {
        val rawResult = getVaultConnections(project, namespace)
        if (rawResult.isEmpty()) {
            return null
        }
        return rawResult.single()
    }

    @Throws(ParameterNamespaceCollisionException::class)
    private fun getVaultConnections(project: SProject, parameterNamespace: String?): List<VaultFeatureSettings> {
        // Namespace is a key
        val effectiveFeatures = mutableMapOf<String, VaultFeatureSettings>()

        // Own features come first, Root project's last. Own feature has higher priority.
        val rawProjectFeatures = project
                .getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)
                .filter {
                    VaultConstants.FeatureSettings.FEATURE_TYPE == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
                }

        val knownDescriptors = mutableSetOf<ConnectionDescriptor>()

        rawProjectFeatures.forEach { featureDescriptor ->
            val settings = VaultFeatureSettings(featureDescriptor)

            // Filter connections by parameter namespace if specified
            if (parameterNamespace != null && parameterNamespace != settings.id) {
                return@forEach
            }

            // Detect namespace collisions:
            // When multiple connections in the same project have the same parameter namespace
            val descriptor = ConnectionDescriptor(featureDescriptor.projectId, settings.id)
            if (descriptor in knownDescriptors) {
                throw ParameterNamespaceCollisionException(settings.id, featureDescriptor.projectId)
            }

            knownDescriptors.add(descriptor)
            effectiveFeatures.putIfAbsent(settings.id, settings)
        }

        return effectiveFeatures.map { (_, settings) -> settings }
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

    private data class ConnectionDescriptor(val projectId: String, val parameterNamespace: String)
}