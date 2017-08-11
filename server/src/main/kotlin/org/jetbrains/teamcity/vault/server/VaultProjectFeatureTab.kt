package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.PublicKeyUtil
import jetbrains.buildServer.controllers.admin.projects.EditProjectTab
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.crypt.RSACipher
import jetbrains.buildServer.web.openapi.PagePlaces
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.util.SessionUser
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import javax.servlet.http.HttpServletRequest

class VaultProjectFeatureTab(pagePlaces: PagePlaces, descriptor: PluginDescriptor)
    : EditProjectTab(pagePlaces, "HashiCorp_Vault", descriptor.getPluginResourcesPath("editProjectFeatureVault.jsp"), "HashiCorp Vault") {

    override fun fillModel(model: MutableMap<String, Any>, request: HttpServletRequest) {
        super.fillModel(model, request)
        val project = getProject(request) ?: return

        val feature = project.getOwnFeaturesOfType(VaultConstants.FeatureSettings.FEATURE_TYPE).firstOrNull()
        val default = VaultFeatureSettings.getDefaultParameters()
        val parameters = (feature?.parameters ?: default)

        val propertiesBean = BasePropertiesBean(parameters, default)

        model.put("defined", (feature != null).toString())
        model.put("propertiesBean", propertiesBean)
        model.put(PublicKeyUtil.PUBLIC_KEY_PARAM, RSACipher.getHexEncodedPublicKey())
    }

    override fun isAvailable(request: HttpServletRequest): Boolean {
        val project = getProject(request)
        val user = SessionUser.getUser(request)
        return project != null && user != null && user.isPermissionGrantedForProject(project.projectId, Permission.EDIT_PROJECT)
    }
}