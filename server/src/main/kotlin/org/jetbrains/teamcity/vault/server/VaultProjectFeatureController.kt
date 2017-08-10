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
import org.jetbrains.teamcity.vault.VaultFeatureSettings
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

        val processor = VaultBuildFeature.getParametersProcessor()
        val errors = ActionErrors()
        processor.process(properties).forEach { errors.addError(it) }
        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
            return
        }

        val feature = project.getOwnFeaturesOfType(FeatureSettings.FEATURE_TYPE).firstOrNull()
        val settings = VaultFeatureSettings(properties)
        val enabled = !settings.roleId.isNullOrBlank() && !settings.secretId.isNullOrBlank() && !settings.url.isNullOrBlank()
        var persist = false

        if (feature == null) {
            if (enabled) {
                project.addFeature(FeatureSettings.FEATURE_TYPE, properties)
                persist = true
            }
        } else {
            if (enabled) {
                project.updateFeature(feature.id, feature.type, properties)
                persist = true
            } else {
                //TODO: Descide wheter delete or diable feature
                project.removeFeature(feature.id)
                persist = true
            }
        }
        if (persist) {
            persister.persist(project, configActionFactory.createAction(SessionUser.getUser(request), project, "Update HashiCorp Vault feature"))
        }
    }


    fun error(xmlResponse: Element, message: String) {
        val errors = ActionErrors()
        errors.addError("general", message)
        errors.serialize(xmlResponse)
    }
}