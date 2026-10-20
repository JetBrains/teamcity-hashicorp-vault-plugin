package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameterConstants
import jetbrains.buildServer.serverSide.auth.RoleScope.projectScope
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.util.ActionThrow
import org.assertj.core.api.BDDAssertions.then
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.VaultParameterSettings
import org.jetbrains.teamcity.vault.getVaultParameterName
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class VaultBuildStartContextProcessorTest : BaseServerTestCase() {
    private lateinit var processor: VaultBuildStartContextProcessor
    private lateinit var developer: SUser
    private lateinit var admin: SUser

    @BeforeMethod(alwaysRun = true)
    override fun setUp() {
        super.setUp()
        processor = VaultBuildStartContextProcessor(HashiCorpVaultConnectionResolver(Mockito.mock(VaultConnector::class.java)))
        myProject.addFeature(
            OAuthConstants.FEATURE_TYPE,
            mapOf(
                OAuthConstants.OAUTH_TYPE_PARAM to VaultConstants.FeatureSettings.FEATURE_TYPE,
                // Without the ID property the connection is resolved under the feature ID instead of the default namespace
                VaultConstants.FeatureSettings.ID to VaultConstants.FeatureSettings.DEFAULT_ID
            ) + VaultFeatureSettings(URL, VAULT_NAMESPACE).toFeatureProperties()
        )
        developer = createUser("developer")
        developer.addRole(projectScope(myProject.projectId), projectDevRole)

        admin = createUser("admin")
        admin.addRole(projectScope(myProject.projectId), projectAdminRole)
    }

    @Test
    fun customLegacyReferenceIsResetToConfiguredValueForUntrustedUser() {
        myBuildType.addParameter(SimpleParameter(PARAMETER_NAME, LEGACY_REFERENCE))
        val build = runBuild(developer, mapOf(PARAMETER_NAME to OTHER_LEGACY_REFERENCE))

        then(updateParameters(build)).containsEntry(PARAMETER_NAME, LEGACY_REFERENCE)
    }

    @Test
    fun customLegacyReferenceIsResetToEmptyValueWhenNotConfigured() {
        val build = runBuild(developer, mapOf(PARAMETER_NAME to OTHER_LEGACY_REFERENCE))

        then(updateParameters(build)).containsEntry(PARAMETER_NAME, "")
    }

    @Test
    fun customValueOfVaultRemoteParameterIsResetToConfiguredValueForUntrustedUser() {
        myBuildType.addParameter(vaultRemoteParameter(VAULT_QUERY))
        val build = runBuild(developer, mapOf(PARAMETER_NAME to "custom value"))

        then(updateParameters(build)).containsEntry(PARAMETER_NAME, "")
        then(build.isStopped()).isFalse()
    }

    @Test
    fun buildIsStoppedWhenUntrustedUserAddsVaultRemoteParameter() {
        val build = runBuild(developer, listOf(vaultRemoteParameter(VAULT_QUERY)))

        then(updateParameters(build)).doesNotContainKey(PARAMETER_NAME)
        then(build.isStopped()).isTrue()
        then(build.canceledInfo?.comment).contains("Custom remote parameters were added or changed: $PARAMETER_NAME")
    }

    @Test
    fun customSetEnvParameterIsResetToConfiguredValueForUntrustedUser() {
        myBuildType.addParameter(SimpleParameter(SET_ENV_PARAMETER, "false"))
        val build = runBuild(developer, mapOf(SET_ENV_PARAMETER to "true"))

        then(updateParameters(build)).containsEntry(SET_ENV_PARAMETER, "false")
    }

    @Test
    fun customSetEnvParameterIsResetToFalseWhenNotConfigured() {
        val build = runBuild(developer, mapOf(SET_ENV_PARAMETER to "true"))

        then(updateParameters(build)).containsEntry(SET_ENV_PARAMETER, "false")
    }

    @Test
    fun customParametersWithoutVaultReferencesAreKeptForUntrustedUser() {
        myBuildType.addParameter(SimpleParameter(PARAMETER_NAME, "configured"))
        val build = runBuild(developer, mapOf(PARAMETER_NAME to "custom", "dep.other.param" to OTHER_LEGACY_REFERENCE))

        val sharedParameters = updateParameters(build)

        then(build.isStopped()).isFalse()
        then(sharedParameters).doesNotContainKeys(PARAMETER_NAME, "dep.other.param")
    }

    @Test
    fun customVaultParametersAreKeptForProjectAdmin() {
        myBuildType.addParameter(SimpleParameter(PARAMETER_NAME, LEGACY_REFERENCE))
        val build = runBuild(admin, mapOf(PARAMETER_NAME to OTHER_LEGACY_REFERENCE, SET_ENV_PARAMETER to "true"))

        then(updateParameters(build)).doesNotContainKeys(PARAMETER_NAME, SET_ENV_PARAMETER)
    }

    @Test
    fun addedVaultRemoteParameterIsKeptForProjectAdmin() {
        val build = runBuild(admin, listOf(vaultRemoteParameter(VAULT_QUERY)))

        updateParameters(build)

        then(build.isStopped()).isFalse()
    }

    @Test
    fun customVaultParametersAreKeptWhenBuildIsNotTriggeredByUser() {
        myBuildType.addParameter(SimpleParameter(PARAMETER_NAME, LEGACY_REFERENCE))
        val build = runBuild(null, mapOf(PARAMETER_NAME to OTHER_LEGACY_REFERENCE))

        then(updateParameters(build)).doesNotContainKey(PARAMETER_NAME)
    }

    @Test
    fun customVaultParametersAreKeptWhenAllowedByInternalProperty() {
        setInternalProperty("teamcity.internal.unsafe.vault.allowCustomParameters", "true")
        val build = runBuild(developer, listOf(vaultRemoteParameter(VAULT_QUERY), SimpleParameter(SET_ENV_PARAMETER, "true")))

        then(updateParameters(build)).doesNotContainKeys(PARAMETER_NAME, SET_ENV_PARAMETER)
        then(build.isStopped()).isFalse()
    }

    private fun runBuild(user: SUser?, customParameters: Map<String, String>): RunningBuildEx =
        runBuild(user) { it.setParameters(customParameters) }

    private fun runBuild(user: SUser?, customParameters: List<Parameter>): RunningBuildEx =
        runBuild(user) { it.setParameters(customParameters) }

    private fun runBuild(user: SUser?, customize: (BuildCustomizerEx) -> Unit): RunningBuildEx {
        val customizer = myFixture.getSingletonService(BuildCustomizerFactory::class.java).createBuildCustomizer(myBuildType, user) as BuildCustomizerEx
        customize(customizer)
        val triggeredBy = if (user == null) TriggeredByBuilder() else TriggeredByBuilder(user)
        val queuedBuild = myBuildType.addToQueue(customizer.createPromotion() as BuildPromotionEx, triggeredBy.toString())
        return BuildBuilder.run(queuedBuild!!, myFixture)
    }

    private fun updateParameters(build: RunningBuildEx): Map<String, String> {
        val sharedParameters = mutableMapOf<String, String>()
        val context = mock<BuildStartContext> {
            on { this.build } doReturn build
            on { this.sharedParameters } doReturn sharedParameters
            on { addSharedParameter(any(), any()) } doAnswer { sharedParameters[it.getArgument(0)] = it.getArgument(1) }
        }
        processor.updateParameters(context)
        return sharedParameters
    }

    private fun vaultRemoteParameter(vaultQuery: String): Parameter =
        parameterFactory.createParameter(PARAMETER_NAME, "", object : ControlDescription {
            override fun getParameterType(): String = RemoteParameterConstants.PARAMETER_TYPE

            override fun getParameterTypeArguments(): Map<String, String> =
                mapOf(RemoteParameterConstants.REMOTE_TYPE_PARAM to VaultConstants.PARAMETER_TYPE) +
                    VaultParameterSettings(VaultConstants.FeatureSettings.DEFAULT_ID, vaultQuery).toMap()
        })

    private fun RunningBuildEx.isStopped() = canceledInfo != null

    private fun logMessages(build: SBuild): List<String> =
        build.buildLog.messagesIterator.asSequence().mapNotNull { it.text }.toList()

    companion object {
        private const val URL = "http://localhost:8200"
        private const val VAULT_NAMESPACE = "vaultNamespace"
        private const val PARAMETER_NAME = "env.SECRET"
        private const val VAULT_QUERY = "secret/path!/key"
        private const val LEGACY_REFERENCE = "%vault:secret/path!/key%"
        private const val OTHER_LEGACY_REFERENCE = "%vault:other/path!/key%"
        private val SET_ENV_PARAMETER = getVaultParameterName(VaultConstants.FeatureSettings.DEFAULT_ID, VaultConstants.BehaviourParameters.ExposeEnvSuffix)
    }
}
