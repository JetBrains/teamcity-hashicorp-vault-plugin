
package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.controllers.*
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.auth.AccessDeniedException
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.jdom.Element
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.server.HashiCorpVaultConnectionResolver.ParameterNamespaceCollisionException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler
import java.io.OutputStreamWriter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class VaultTestQueryController(
    server: SBuildServer, wcm: WebControllerManager,
    authInterceptor: AuthorizationInterceptor,
    private val sslTrustStoreProvider: SSLTrustStoreProvider,
    private val vaultResolver: VaultResolver,
    private val projectManager: ProjectManager,
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver,
    private val sessionManagerBuilder: SessionManagerBuilder,
    private val connector: VaultConnector,
) : BaseFormXmlController(server), RequestPermissionsCheckerEx {

    private val scheduler: TaskScheduler = ConcurrentTaskScheduler()

    companion object {
        const val PATH = "/admin/hashicorp-vault-test-query.html"
    }

    init {
        wcm.registerController(PATH, this)
        authInterceptor.addPathBasedPermissionsChecker(PATH, this)
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) = null

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse, xmlResponse: Element) {
        val properties = getRequestProperties(request)

        val projectId = properties[VaultConstants.PROJECT_ID] ?: return writeError(response, HttpStatus.BAD_REQUEST, "Failed to find projectId parameter")
        val project = projectManager.findProjectByExternalId(projectId) ?: return writeError(response, HttpStatus.NOT_FOUND, "Project $projectId not found")
        val buildTypeId = properties[VaultConstants.BUILD_TYPE_ID]
        val buildType = buildTypeId?.let { projectManager.findBuildTypeByExternalId(it) ?: return writeError(response, HttpStatus.NOT_FOUND, "BuildType $it not found") }
        doTestQuery(project, buildType, properties, xmlResponse)
    }

    private fun writeError(response: HttpServletResponse, httpStatus: HttpStatus, errorMessage: String): Unit {
        val writer = OutputStreamWriter(response.outputStream)
        response.apply {
            status = httpStatus.value()
            contentType = MediaType.TEXT_PLAIN_VALUE
        }

        writer.write(errorMessage)
        writer.flush()
    }

    private fun doTestQuery(project: SProject, buildType: SBuildType?, properties: Map<String, String>, xmlResponse: Element) {
        val errors = ActionErrors()

        val invalids = VaultParameterSettings.getInvalidProperties(properties)
        if (invalids.isNotEmpty()) {
            val errorMessage = "Errors found in the parameter definition: ${StringUtil.join(invalids.values, ", ")}"
            failTestConnection(errors, xmlResponse, errorMessage)
            return
        }

        try {
            val parameterSettings = VaultParameterSettings(properties)
            val serverFeature = try {
                hashiCorpVaultConnectionResolver.getVaultConnection(project, parameterSettings.namespace)
            } catch (e: ParameterNamespaceCollisionException) {
                failTestConnection(errors, xmlResponse, "HashiCorp Vault connection with ID '${parameterSettings.namespace}' is declared more than once in the same project")
                return
            }

            if (serverFeature == null) {
                failTestConnection(errors, xmlResponse, "Failed to find HashiCorp Vault connection ${parameterSettings.namespace}")
                return
            }

            val isWriteEngineEnabled =  if (buildType is InternalParameters) {
                buildType.getBooleanInternalParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES)
            } else {
                project.getParameterValue(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES)?.toBoolean() ?: false
            }

            IOGuard.allowNetworkCall<Exception> {
                val agentFeature = hashiCorpVaultConnectionResolver
                    .serverFeatureSettingsToAgentSettings(serverFeature, parameterSettings.namespace, build = null)
                val token = sessionManagerBuilder
                    .build(agentFeature).sessionToken.token
                val query = VaultQuery.extract(parameterSettings.vaultQuery, isWriteEngineEnabled)


                val result = vaultResolver.doFetchAndPrepareReplacements(agentFeature, token, listOf(query))
                if (result.errors.isNotEmpty()) {
                    errors.addError(EditVcsRootsController.FAILED_TEST_CONNECTION_ERR, "Error while fetching parameter: ${result.errors.values.first()}")
                    errors.serialize(xmlResponse)
                } else {
                    XmlResponseUtil.writeTestResult(xmlResponse, "Success!")
                }
            }
            return
        } catch (e: Exception) {
            errors.addError(EditVcsRootsController.FAILED_TEST_CONNECTION_ERR, e.message)
        }

        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
        }
    }

    override fun checkPermissions(securityContext: SecurityContextEx, request: HttpServletRequest) {
        val projectProperties = getRequestProperties(request)
        val projectId = projectProperties[VaultConstants.PROJECT_ID]
        val project = projectManager.findProjectByExternalId(projectId)
        if (project == null) {
            throw AccessDeniedException(securityContext.authorityHolder, "No project $projectId")
        } else {
            securityContext.authorityHolder.isPermissionGrantedForProject(project.projectId, Permission.EDIT_PROJECT)
        }
    }

    private fun failTestConnection(
        errors: ActionErrors,
        xmlResponse: Element,
        errorMessage: String
    ) {
        errors.addError(EditVcsRootsController.FAILED_TEST_CONNECTION_ERR, errorMessage)
        errors.serialize(xmlResponse)
    }

    private fun getRequestProperties(request: HttpServletRequest): MutableMap<String, String> {
        val propertiesBean = BasePropertiesBean(emptyMap())
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propertiesBean)
        val properties = propertiesBean.properties.toMutableMap()
        return properties
    }
}