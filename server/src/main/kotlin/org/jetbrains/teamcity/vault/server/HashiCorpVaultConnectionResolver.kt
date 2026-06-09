package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.log.LogUtil
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.util.SecretValueMasker
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

    fun serverFeatureSettingsToAgentSettings(settings: VaultFeatureSettings, namespace: String, build: SBuild?): VaultFeatureSettings =
        if (settings.auth is Auth.AppRoleAuthServer || settings.auth is Auth.LdapServer) {
            val ctx = VaultLoginContext(
                buildId = build?.buildId,
                projectId = build?.projectId
            )
            val wrappedToken: String = try {
                connector.requestWrappedToken(settings, ctx)
            } catch (e: Throwable) {
                var message = "Failed to fetch HashiCorp Vault wrapped token: ${e.message}, namespace: $namespace, project feature id: ${settings.id}"
                if (build != null) {
                    message += ", build: ${LogUtil.describe(build)}"
                }
                throw RuntimeException(message, e)
            }
            logIssuedToken(settings, namespace, build, wrappedToken)
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

    /**
     * Logs which project requested a Vault wrapped token, which connection/credentials were used to
     * obtain it, and the resulting token. Helps diagnose the sporadic "permission denied" failures
     * (TW-93821): if a same-namespace connection from a different project is picked up, the role id /
     * secret logged here will differ. The secret and the wrapped token are masked via [SecretValueMasker].
     */
    private fun logIssuedToken(settings: VaultFeatureSettings, namespace: String, build: SBuild?, wrappedToken: String) {
        val (credentialId, secretMasked) = when (val auth = settings.auth) {
            is Auth.AppRoleAuthServer -> auth.roleId to SecretValueMasker.mask(auth.secretId)
            is Auth.LdapServer -> auth.username to SecretValueMasker.mask(auth.password)
            else -> "" to ""
        }
        LOG.info(
            "Vault wrapped token issued: build_id=${build?.buildId}, project_id='${build?.projectId}', " +
            "namespace='$namespace', connection_id='${settings.id}', auth_method=${settings.auth.method.name}, " +
            "role_id='$credentialId', secret_id='$secretMasked', wrapped_token='${SecretValueMasker.mask(wrappedToken)}'"
        )
    }

    private data class ConnectionDescriptor(val projectId: String, val parameterNamespace: String)
}