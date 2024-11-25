package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor
import jetbrains.buildServer.serverSide.connections.ProjectConnectionsManager
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthProvider
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.isDefault

class VaultProjectConnectionProvider(
    private val descriptor: PluginDescriptor,
    private val projectConnectionsManager: ProjectConnectionsManager,
    private val projectManager: ProjectManager
) : OAuthProvider() {
    override fun getType(): String = VaultConstants.FeatureSettings.FEATURE_TYPE

    override fun getDisplayName(): String = "HashiCorp Vault"

    override fun describeConnection(connection: OAuthConnectionDescriptor): String {
        val settings = VaultFeatureSettings(connection)
        return "Connection to HashiCorp Vault server at ${settings.url}" +
                if (isDefault(settings.id)) "" else ", ID '${settings.id}'"
    }

    override fun getDefaultProperties(): Map<String, String> {
        return VaultFeatureSettings.getDefaultParameters()
    }

    override fun getEditParametersUrl(): String {
        return descriptor.getPluginResourcesPath("editProjectConnectionVault.jsp")
    }

    override fun getPropertiesProcessor(): PropertiesProcessor {
        return getParametersProcessor(projectConnectionsManager, projectManager)
    }

    companion object {
        fun getParametersProcessor(projectConnectionsManager: ProjectConnectionsManager, projectManager: ProjectManager): PropertiesProcessor {
            return object : PropertiesProcessor {
                private fun verifyCollisions(project: SProject, errors: ArrayList<InvalidProperty>, namespace: String, connectionId: String?) {
                    val connectionsOfType = projectConnectionsManager.getAvailableConnectionsOfType(project, VaultConstants.FeatureSettings.FEATURE_TYPE)

                    if (connectionsOfType.any {
                            it.parameters[VaultConstants.FeatureSettings.ID] == namespace &&
                                    it.id != connectionId
                        }) {
                        errors.add(InvalidProperty(VaultConstants.FeatureSettings.ID, """Vault ID "$namespace" is already in use"""))
                    }
                }

                override fun process(properties: MutableMap<String, String>): Collection<InvalidProperty> {
                    val errors = ArrayList<InvalidProperty>()
                    if (properties[VaultConstants.FeatureSettings.URL].isNullOrBlank()) {
                        errors.add(InvalidProperty(VaultConstants.FeatureSettings.URL, "Should not be empty"))
                    }

                    // TW-90895 Ensure the empty value is kept - even if it isn't a default value anymore
                    if (properties[VaultConstants.FeatureSettings.ID] == VaultConstants.FeatureSettings.EMPTY_NAMESPACE){
                        properties[VaultConstants.FeatureSettings.ID] = ""
                    }

                    val namespace = properties[VaultConstants.FeatureSettings.ID]
                    if(!namespace.isNullOrBlank()) {
                        val namespaceRegex = "[a-zA-Z0-9_-]+"
                        if (namespace != "" && !namespace.matches(namespaceRegex.toRegex())) {
                            errors.add(InvalidProperty(VaultConstants.FeatureSettings.ID, "Non-default ID should match regex '$namespaceRegex'"))
                        }

                        // Project ID was not being added before so it might not be present
                        val projectExternalId = properties[VaultConstants.PROJECT_ID]
                        val connectionId = properties[VaultConstants.CONNECTION_ID]
                        val project = projectManager.findProjectByExternalId(projectExternalId)
                        if (project != null) {
                            verifyCollisions(project, errors, namespace, connectionId)
                        }
                    }
                    // IDs are only there for verification and shouldn't be committed to storage
                    properties.remove(VaultConstants.PROJECT_ID)
                    properties.remove(VaultConstants.CONNECTION_ID)


                    when (properties[VaultConstants.FeatureSettings.AUTH_METHOD]) {
                        VaultConstants.FeatureSettings.AUTH_METHOD_APPROLE -> {
                            properties.remove(VaultConstants.FeatureSettings.USERNAME)
                            properties.remove(VaultConstants.FeatureSettings.PASSWORD)
                            removeGcpProperties(properties)

                            if (properties[VaultConstants.FeatureSettings.ENDPOINT].isNullOrBlank()) {
                                errors.add(InvalidProperty(VaultConstants.FeatureSettings.ENDPOINT, "Should not be empty"))
                            }
                            if (properties[VaultConstants.FeatureSettings.ROLE_ID].isNullOrBlank()) {
                                errors.add(InvalidProperty(VaultConstants.FeatureSettings.ROLE_ID, "Should not be empty"))
                            }
                            if (properties[VaultConstants.FeatureSettings.SECRET_ID].isNullOrBlank()) {
                                errors.add(InvalidProperty(VaultConstants.FeatureSettings.SECRET_ID, "Should not be empty"))
                            }
                        }

                        VaultConstants.FeatureSettings.AUTH_METHOD_LDAP -> {
                            properties.remove(VaultConstants.FeatureSettings.ENDPOINT)
                            properties.remove(VaultConstants.FeatureSettings.ROLE_ID)
                            properties.remove(VaultConstants.FeatureSettings.SECRET_ID)
                            removeGcpProperties(properties)

                            if (properties[VaultConstants.FeatureSettings.USERNAME].isNullOrBlank()) {
                                errors.add(InvalidProperty(VaultConstants.FeatureSettings.USERNAME, "Should not be empty"))
                            }
                            if (properties[VaultConstants.FeatureSettings.PASSWORD].isNullOrBlank()) {
                                errors.add(InvalidProperty(VaultConstants.FeatureSettings.PASSWORD, "Should not be empty"))
                            }
                        }

                        VaultConstants.FeatureSettings.AUTH_METHOD_GCP_IAM -> {
                            removeNonGcpProperties(properties)

                            if (properties[VaultConstants.FeatureSettings.GCP_ROLE].isNullOrBlank()) {
                                errors.add(InvalidProperty(VaultConstants.FeatureSettings.GCP_ROLE, "Should not be empty"))
                            }
                        }
                    }

                    // Convert slashes if needed of add new fields
                    VaultFeatureSettings(properties).toFeatureProperties(properties)

                    return errors
                }
            }
        }

        private fun removeGcpProperties(properties: MutableMap<String, String>) {
            properties.keys.removeAll(VaultConstants.GCP_IAM_PROPERTIES_SET)
        }

        private fun removeNonGcpProperties(properties: MutableMap<String, String>) {
            properties.keys.removeAll(VaultConstants.LDAP_PROPERTIES_SET)
            properties.keys.removeAll(VaultConstants.APPROLE_PROPERTIES_SET)
        }
    }
}