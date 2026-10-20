
package org.jetbrains.teamcity.vault.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.controllers.ActionErrors
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.SimpleView
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController
import jetbrains.buildServer.controllers.parameters.InvalidParametersException
import jetbrains.buildServer.controllers.parameters.ParameterContext
import jetbrains.buildServer.controllers.parameters.ParameterEditContext
import jetbrains.buildServer.controllers.parameters.ParameterRenderContext
import jetbrains.buildServer.controllers.parameters.remote.RemoteParameterControlProvider
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameter
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameterProvider
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.util.SessionUser
import org.jdom.Element
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultConstants.PARAMETER_TYPE
import org.jetbrains.teamcity.vault.VaultParameterSettings
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest

class HashiCorpVaultParameter(private val descriptor: PluginDescriptor) : RemoteParameterProvider,
    RemoteParameterControlProvider {

    private val objectMapper = jacksonObjectMapper()

    override fun renderControl(request: HttpServletRequest, context: ParameterRenderContext): ModelAndView {
        val modelAndView = fillModelWithVault(request, context) ?: return getErrorModel()
        modelAndView.viewName = descriptor.getPluginResourcesPath("editCustomParameter.jsp")
        modelAndView.model["propertiesBean"] = BasePropertiesBean(context.description.parameterTypeArguments)

        return modelAndView
    }

    override fun validateDefaultParameterValue(context: ParameterContext, value: String?) {}

    override fun validateParameterValue(request: HttpServletRequest, context: ParameterRenderContext, value: String?): MutableCollection<InvalidProperty> = mutableListOf()

    // The value should be used to store a representation of the query
    override fun convertParameterValue(request: HttpServletRequest, context: ParameterRenderContext, value: String?): String? = if (StringUtil.isNotEmpty(value)){
        value
    } else {
        "${VaultConstants.VAULT_PARAMETER_PREFIX}${VaultParameterSettings(context.description.parameterTypeArguments).vaultQuery}"
    }

    override fun presentParameterValue(context: ParameterContext, value: String?): String = StringUtil.emptyIfNull(value)

    override fun getRemoteParameterDescription(): String = "HashiCorp Vault Parameter"

    private fun fillModelWithVault(request: HttpServletRequest, context: ParameterContext): ModelAndView? {
        val project = context.getAdditionalParameter(ParameterContext.PROJECT) ?: return null
        val modelAndView = ModelAndView()

        val vaultFeatureSettings = VaultConnectionUtils.getFeatures(project)
        modelAndView.model[VAULT_FEATURE_SETTINGS] = vaultFeatureSettings
        modelAndView.model[HAS_EDIT_PERMISSION] = checkHasAccess(request, project)
        
        return modelAndView
    }

    private fun checkHasAccess(
        request: HttpServletRequest,
        project: SProject
    ): Boolean {
        val user: SUser? = SessionUser.getUser(request)
        return user?.isPermissionGrantedForProject(project.getProjectId(), Permission.EDIT_PROJECT) ?: false
    }


    override fun renderSpecEditor(request: HttpServletRequest, context: ParameterEditContext): ModelAndView {
        val modelAndView = fillModelWithVault(request, context) ?: return getErrorModel()
        modelAndView.viewName = descriptor.getPluginResourcesPath("editProjectSpec.jsp")
        return modelAndView
    }

    override fun convertSpecEditorParameters(parameters: MutableMap<String, String>): Map<String, String> = try {
        VaultParameterSettings(parameters)
        parameters
    } catch (e: IllegalArgumentException) {
        throw InvalidParametersException(e.localizedMessage, e)
    }

    private fun getErrorModel() = SimpleView.createTextView("Could not show hashicorp connections")

    override fun validateSpecEditorParameters(parameters: MutableMap<String, String>): Collection<InvalidProperty> =
        VaultParameterSettings.getInvalidProperties(parameters).map { InvalidProperty(it.key, it.value) }

    override fun getRemoteParameterType(): String = PARAMETER_TYPE

    override fun createRemoteParameter(build: SBuild, parameter: Parameter): RemoteParameter = object : RemoteParameter {
        override fun getValue(): String = parameter.value

        override fun isSecret(): Boolean = true

        override fun getName(): String = parameter.name
    }


    companion object {
        const val VAULT_FEATURE_SETTINGS = "vaultFeatureSettings"
        const val HAS_EDIT_PERMISSION = "hasEditPermission"
    }
}