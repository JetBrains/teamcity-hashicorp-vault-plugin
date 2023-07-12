package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.serverSide.BuildsManager
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.web.util.WebAuthUtil
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping(VaultConstants.ControllerSettings.URL)
class HashicorpVaultConnectionController(
    private val projectManager: ProjectManager,
    private val buildsManager: BuildsManager,
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver,
) {
    private val LOG = Logger.getInstance(HashiCorpVaultConnectionResolver::class.java)

    companion object {
        private const val STORAGE_ID = "hashicorp-vault-token-generation"
        private const val IS_GENERATED = "isGenerated"
    }

    fun getTokenGenerationId(feature: VaultFeatureSettings) = "$STORAGE_ID-${feature.namespace}"

    @RequestMapping(VaultConstants.ControllerSettings.WRAP_TOKEN_PATH, method = [RequestMethod.GET], produces = ["application/json"])
    fun getToken(@RequestParam(name = "namespace") namespace: String, request: HttpServletRequest): Map<String, String> {
        val buildId = WebAuthUtil.getAuthenticatedBuildId(request) ?: throw ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "No authenticated build has been found. Access to the build tokens is denied."
        )
        val build = buildsManager.findRunningBuildById(buildId)
        if (build == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Running build with id $buildId not found")
        }

        val project = projectManager.findProjectById(build.projectId)

        if (project == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project with id ${build.projectId} not found")
        }

        val feature = hashiCorpVaultConnectionResolver
            .getProjectToConnectionPairs(project)
            .filter { it.second.namespace == namespace }
            .firstOrNull()?.second

        if (feature == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Vault namespace $namespace not found")
        }


        if (hasTokenBeenGenerated(build, feature)) {
            val errorMessage = "There has been an attempt to generate a second hashicorp vault token for build ${build.buildId} in project ${project.projectId}"
            LOG.error(errorMessage)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "A token has already been generated for this build")
        }

        return try {
            val agentFeatureSettings = hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(feature, namespace)
            agentFeatureSettings.toFeatureProperties()
        } catch (e: Throwable) {
            LOG.error("Failed to request token for hashicorp vault namespace ${feature.namespace} build ${build.buildId} of ${project.projectId}", e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to request token", e)
        }
    }

    private fun hasTokenBeenGenerated(build: SRunningBuild, feature: VaultFeatureSettings): Boolean {
        val customStorage = build.getCustomDataStorage(STORAGE_ID)
        val tokenGenerationId = getTokenGenerationId(feature)
        customStorage.refresh()
        val value = customStorage.getValue(tokenGenerationId)
        if (!StringUtil.isEmpty(value)) {
            return true
        }

        customStorage.putValue(tokenGenerationId, IS_GENERATED)
        customStorage.flush()

        return false
    }
}
