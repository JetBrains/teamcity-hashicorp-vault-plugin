package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.controllers.*
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.serverSide.ConfigActionFactory
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.web.util.SessionUser
import org.jdom.Element
import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class VaultProjectFeatureController(server: SBuildServer, wcm: WebControllerManager,
                                    val configActionFactory: ConfigActionFactory,
                                    val persister: UIConfigsPersister,
                                    val projectManager: ProjectManager) : BaseFormXmlController(server) {
    init {
        wcm.registerController("/admin/project/hashicorp-vault/edit.html", this)
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) = null

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse, xmlResponse: Element) {
        if (PublicKeyUtil.isPublicKeyExpired(request)) {
            PublicKeyUtil.writePublicKeyExpiredError(xmlResponse)
            return
        }
        val projectExternalId = request.getParameter("projectId")
        val project = projectManager.findProjectByExternalId(projectExternalId) ?: return error(xmlResponse, "Project with id '$projectExternalId' does not exist anymore.")

        val propertiesBean = BasePropertiesBean(emptyMap())
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propertiesBean)
        val properties = propertiesBean.properties.toMutableMap()

        val feature = project.getOwnFeaturesOfType(FeatureSettings.FEATURE_TYPE).firstOrNull()
        val action = request.getParameter("do-action")
        var persist: String? = null

        if (action == "delete") {
            if (feature != null) {
                project.removeFeature(feature.id)
                persist = "Remove HashiCorp Vault feature"
            }
        } else if (action == "test-connection") {
            val processor = VaultBuildFeature.getParametersProcessor()
            val errors = ActionErrors()
            processor.process(properties).forEach { errors.addError(it) }
            if (errors.hasErrors()) {
                errors.serialize(xmlResponse)
                return
            }
            val result = doTestConnection(properties, errors)
            if (errors.hasErrors()) {
                errors.serialize(xmlResponse)
            }
            val el = Element("test_connection")
            el.setAttribute("result", result.toString())
            xmlResponse.addContent(el)
        } else {
            val processor = VaultBuildFeature.getParametersProcessor()
            val errors = ActionErrors()
            processor.process(properties).forEach { errors.addError(it) }
            if (errors.hasErrors()) {
                errors.serialize(xmlResponse)
                return
            }

            if (feature == null) {
                project.addFeature(FeatureSettings.FEATURE_TYPE, properties)
                persist = "Add HashiCorp Vault feature"
            } else {
                project.updateFeature(feature.id, feature.type, properties)
                persist = "Update HashiCorp Vault feature"
            }
        }
        if (persist != null) {
            persister.persist(project, configActionFactory.createAction(SessionUser.getUser(request), project, persist))
        }
    }


    fun error(xmlResponse: Element, message: String) {
        val errors = ActionErrors()
        errors.addError("general", message)
        errors.serialize(xmlResponse)
    }

    private fun doTestConnection(properties:Map<String,String>, errors: ActionErrors) : Boolean {
        // TODO: Implement
        return true;
    }
}