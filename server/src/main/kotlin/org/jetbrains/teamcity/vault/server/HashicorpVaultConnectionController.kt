package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.BuildsManager
import jetbrains.buildServer.serverSide.CustomDataConflictException
import jetbrains.buildServer.serverSide.CustomDataStorage
import jetbrains.buildServer.serverSide.IOGuard
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.RunningBuildEx
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.web.util.WebAuthUtil
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.server.HashiCorpVaultConnectionResolver.ParameterNamespaceCollisionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping(VaultConstants.ControllerSettings.URL)
class HashicorpVaultConnectionController(
    private val projectManager: ProjectManager,
    private val buildsManager: BuildsManager,
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver,
) {
    private val LOG = Logger.getInstance("${Loggers.SERVER_CATEGORY}.${HashicorpVaultConnectionController::class.java.name}")

    companion object {
        private const val STORAGE_ID = "hashicorp-vault-token-generation"
        private const val IS_GENERATED = "isGenerated"
    }

    fun getTokenGenerationId(feature: VaultFeatureSettings) = "$STORAGE_ID-${feature.id}"

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<String> {
        return ResponseEntity(StringEscapeUtils.escapeHtml(ex.reason), ex.status)
    }

    // http:localhost:8111/bs/app/url/path?namespace=something
    @RequestMapping(VaultConstants.ControllerSettings.WRAP_TOKEN_PATH, method = [RequestMethod.GET], produces = ["application/json"])
    fun getToken(@RequestParam(name = "namespace") namespace: String, request: HttpServletRequest): Map<String, String> {
        val buildId = WebAuthUtil.getAuthenticatedBuildId(request) ?: throw ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "No authenticated build has been found. Access to the build tokens is denied."
        )
        val build = buildsManager.findRunningBuildById(buildId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Running build with id $buildId not found")

        val project = projectManager.findProjectById(build.projectId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project with id ${build.projectId} not found")

        val feature = try {
            hashiCorpVaultConnectionResolver.getVaultConnection(project, namespace)
        } catch (e: ParameterNamespaceCollisionException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Project connection with ID '$namespace' is declared more than once in the same project")
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project connection with ID '$namespace' not found")

        if (hasTokenBeenGenerated(build, feature)) {
            val errorMessage = "There has been an attempt to generate a second HashiCorp Vault token for build ${build.buildId} in project ${project.projectId}"
            LOG.error(errorMessage)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "A token has already been generated for this build")
        }

        return try {
            val agentFeatureSettings = IOGuard.allowNetworkCall<VaultFeatureSettings, Exception> {
                hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(feature, namespace, build)
            }
            agentFeatureSettings.toFeatureProperties()
        } catch (e: Throwable) {
            LOG.warnAndDebugDetails("Failed to request token for hashicorp vault namespace ${feature.id} build ${build.buildId} of ${project.projectId}", e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to request token", e)
        }
    }

    private fun hasTokenBeenGenerated(build: SRunningBuild, feature: VaultFeatureSettings): Boolean {
        (1..3).forEach { attempt ->
            val customStorage = (build as RunningBuildEx).temporaryCustomDataStorage
            val tokenGenerationId = getTokenGenerationId(feature)
            customStorage.refresh()
            val value = customStorage.getValue(tokenGenerationId)
            if (!StringUtil.isEmpty(value)) {
                return true
            }

            customStorage.putValue(tokenGenerationId, IS_GENERATED)
            try {
                customStorage.flush(CustomDataStorage.ConflictResolution.FAIL)
            } catch (e: CustomDataConflictException) {
                // build storage has been changed by some other node, we need to run refresh again
            }
            return false
        }

        return false
    }
}
