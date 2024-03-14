
package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.controllers.*
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.serverSide.IOGuard
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.connections.ProjectConnectionsManager
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jdom.Element
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class VaultOAuthTestConnectionController(
    server: SBuildServer, wcm: WebControllerManager,
    private val trustStoreProvider: SSLTrustStoreProvider,
    private val connector: VaultConnector,
    private val projectConnectionsManager: ProjectConnectionsManager,
    private val projectManager: ProjectManager
) : BaseFormXmlController(server) {
    init {
        wcm.registerController("/admin/hashicorp-vault-test-connection.html", this)
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) = null

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse, xmlResponse: Element) {
        if (PublicKeyUtil.isPublicKeyExpired(request)) {
            PublicKeyUtil.writePublicKeyExpiredError(xmlResponse)
            return
        }
        val propertiesBean = BasePropertiesBean(emptyMap())
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propertiesBean)
        val properties = propertiesBean.properties.toMutableMap()

        doTestConnection(properties, xmlResponse)
    }

    private fun doTestConnection(properties: Map<String, String>, xmlResponse: Element) {
        val processor = VaultProjectConnectionProvider.getParametersProcessor(projectConnectionsManager, projectManager)
        val errors = ActionErrors()
        processor.process(properties).forEach { errors.addError(it) }
        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
            return
        }

        try {
            val settings = VaultFeatureSettings(properties)
            IOGuard.allowNetworkCall<Exception> {
                val token = connector.tryRequestToken(settings)
                VaultConnector.revoke(token, trustStoreProvider)
            }
            XmlResponseUtil.writeTestResult(xmlResponse, "")
            return
        } catch (e: Exception) {
            errors.addError(EditVcsRootsController.FAILED_TEST_CONNECTION_ERR, e.message)
        }
        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
        }
    }
}