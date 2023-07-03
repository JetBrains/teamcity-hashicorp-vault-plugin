package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.serverSide.BuildsManager
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.web.util.WebAuthUtil
import org.jetbrains.teamcity.vault.VaultConstants
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

    @RequestMapping(VaultConstants.ControllerSettings.WRAP_TOKEN_PATH, method = [RequestMethod.GET], produces = ["application/json"])
    fun getToken(@RequestParam(name = "namespace") namespace: String, request: HttpServletRequest): Map<String, String> {
        val buildId = WebAuthUtil.getAuthenticatedBuildId(request) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated build has been found. You do not have access to this build")
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


        return try {
            val agentFeatureSettings = hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(feature, namespace)
            agentFeatureSettings.toFeatureProperties()
        } catch (e: Throwable) {
            build.addBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${feature.namespace}", "VaultConnection", e.localizedMessage))
            build.stop(null, e.localizedMessage)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to request token", e)
        }
    }
}
