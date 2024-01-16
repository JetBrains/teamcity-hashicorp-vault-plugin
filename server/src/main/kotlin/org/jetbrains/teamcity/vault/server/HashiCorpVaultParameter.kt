
package org.jetbrains.teamcity.vault.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.SimpleView
import jetbrains.buildServer.controllers.parameters.InvalidParametersException
import jetbrains.buildServer.controllers.parameters.ParameterContext
import jetbrains.buildServer.controllers.parameters.ParameterEditContext
import jetbrains.buildServer.controllers.parameters.ParameterRenderContext
import jetbrains.buildServer.controllers.parameters.remote.RemoteParameterControlProvider
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameter
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameterProvider
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultConstants.PARAMETER_TYPE
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.VaultParameterSettings
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest

class HashiCorpVaultParameter(private val descriptor: PluginDescriptor) : RemoteParameterProvider,
    RemoteParameterControlProvider {

    private val objectMapper = jacksonObjectMapper()

    //TODO: TW-79366
    override fun renderControl(request: HttpServletRequest, context: ParameterRenderContext): ModelAndView {
        val modelAndView = fillModelWithVault(context) ?: return getErrorModel()
        modelAndView.viewName = descriptor.getPluginResourcesPath("editCustomParameter.jsp")
        modelAndView.model["propertiesBean"] = BasePropertiesBean(context.description.parameterTypeArguments)

        return modelAndView
    }

    override fun validateDefaultParameterValue(context: ParameterContext, value: String?) {}

    override fun validateParameterValue(request: HttpServletRequest, context: ParameterRenderContext, value: String?): MutableCollection<InvalidProperty> = mutableListOf()

    override fun convertParameterValue(request: HttpServletRequest, context: ParameterRenderContext, value: String?): String? = value

    override fun presentParameterValue(context: ParameterContext, value: String?): String = StringUtil.emptyIfNull(value)

    override fun getRemoteParameterDescription(): String = "HashiCorp Vault Parameter"

    private fun fillModelWithVault(context: ParameterContext): ModelAndView? {
        val project = context.getAdditionalParameter(ParameterContext.PROJECT) ?: return null
        val modelAndView = ModelAndView()

        val vaultFeatureSettings = VaultConnectionUtils.getFeatures(project)
        modelAndView.model[VAULT_FEATURE_SETTINGS] = vaultFeatureSettings

        return modelAndView
    }

    override fun renderSpecEditor(request: HttpServletRequest, context: ParameterEditContext): ModelAndView {
        val modelAndView = fillModelWithVault(context) ?: return getErrorModel()
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
        override fun getValue(): String = ""

        override fun isSecret(): Boolean = true

        override fun getName(): String = parameter.name
    }


    companion object {
        const val VAULT_FEATURE_SETTINGS = "vaultFeatureSettings"
    }
}