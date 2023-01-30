/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.controllers.SimpleView
import jetbrains.buildServer.controllers.parameters.InvalidParametersException
import jetbrains.buildServer.controllers.parameters.ParameterContext
import jetbrains.buildServer.controllers.parameters.ParameterEditContext
import jetbrains.buildServer.controllers.parameters.ParameterRenderContext
import jetbrains.buildServer.controllers.parameters.api.ParameterControlProviderAdapter
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.VaultParameterSettings
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest

class HashiCorpVaultParameter(private val descriptor: PluginDescriptor) : ParameterControlProviderAdapter() {
    override fun getParameterType(): String = PARAMETER_TYPE

    override fun getParameterDescription(): String = "HashiCorp Vault"

    //TODO: TW-79366
    override fun renderControl(request: HttpServletRequest, context: ParameterRenderContext): ModelAndView {
        val modelAndView = fillModelWithVault(context) ?: return getErrorModel()

        return modelAndView
    }

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

    override fun convertSpecEditorParameters(parameters: MutableMap<String, String>): MutableMap<String, String> = try {
        VaultParameterSettings(parameters).toMap().toMutableMap()
    } catch (e: IllegalArgumentException) {
        throw InvalidParametersException(e.localizedMessage, e)
    }

    private fun getErrorModel() = SimpleView.createTextView("Could not show hashicorp connections")

    override fun validateParameterDescription(context: ParameterContext) {
        val arguments = context.description.parameterTypeArguments

        try {
            VaultParameterSettings(arguments)
        } catch (e: IllegalArgumentException) {
            throw InvalidParametersException(e.localizedMessage, e)
        }
    }

    companion object {
        const val PARAMETER_TYPE = "hashicorp-vault"
        const val VAULT_FEATURE_SETTINGS = "vaultFeatureSettings"
    }
}