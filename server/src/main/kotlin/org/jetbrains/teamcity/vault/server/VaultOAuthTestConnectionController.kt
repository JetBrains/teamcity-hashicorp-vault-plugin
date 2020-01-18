/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.controllers.*
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jdom.Element
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class VaultOAuthTestConnectionController(server: SBuildServer, wcm: WebControllerManager,
                                         private val trustStoreProvider: SSLTrustStoreProvider,
                                         private val connector: VaultConnector) : BaseFormXmlController(server) {
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
        val processor = VaultProjectConnectionProvider.getParametersProcessor()
        val errors = ActionErrors()
        processor.process(properties).forEach { errors.addError(it) }
        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
            return
        }

        try {
            val settings = VaultFeatureSettings(properties)
            val token = connector.tryRequestToken(settings)
            VaultConnector.revoke(token, trustStoreProvider)
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