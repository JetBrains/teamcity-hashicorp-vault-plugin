package org.jetbrains.teamcity.vault.server

import assertk.assertions.contains
import jetbrains.buildServer.controllers.BaseControllerTestCase
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController
import jetbrains.buildServer.serverSide.InternalParameters
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SimpleParameter
import jetbrains.buildServer.serverSide.impl.ProjectEx
import org.jetbrains.teamcity.vault.*
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.testng.MockitoTestNGListener
import org.springframework.http.HttpStatus
import org.springframework.vault.VaultException
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import java.util.UUID

@Listeners(MockitoTestNGListener::class)
class VaultTestQueryControllerTest : BaseControllerTestCase<VaultTestQueryController>() {
    @Mock
    private lateinit var vaultResolver: VaultResolver

    @Mock
    private lateinit var hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver

    @Mock
    private lateinit var vaultConnector: VaultConnector

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var sessionManagerBuilder: SessionManagerBuilder

    private lateinit var projectWriteEngine: SProject;
    private lateinit var buildTypeWriteEngine: SBuildType;

    @BeforeMethod
    override fun setUp() {
        super.setUp()
        val projectId = UUID.randomUUID().toString()
        projectWriteEngine = createProject(projectId)
        val buildTypeId = UUID.randomUUID().toString()
        buildTypeWriteEngine = registerBuildType(buildTypeId, projectId)
    }

    @AfterMethod
    fun cleanUp() {
        projectWriteEngine.remove()
        buildTypeWriteEngine.remove()
    }

    override fun createController(): VaultTestQueryController = VaultTestQueryController(
        myServer,
        webFixture.webManager,
        webFixture.authorizationInterceptor,
        { null },
        vaultResolver,
        myProjectManager,
        hashiCorpVaultConnectionResolver,
        sessionManagerBuilder,
        vaultConnector
    )

    @Test
    fun testQuery() {
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
            .thenReturn(serverSettings)

        val agentSettings = getDefaultSettings(Auth.getAgentAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE, null))
            .thenReturn(agentSettings)
        Mockito.`when`(sessionManagerBuilder.build(agentSettings).sessionToken.token)
            .thenReturn(TOKEN)

        val query = VaultQuery.extract(VAULT_QUERY)
        val result = VaultResolver.ResolvingResult(
            mapOf(
                query.full to SECRET_VALUE
            ), emptyMap()
        )

        Mockito.`when`(vaultResolver.doFetchAndPrepareReplacements(agentSettings, TOKEN, listOf(query)))
            .thenReturn(result)

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("testConnectionResult")?.value
        Assert.assertNotNull(response)
    }

    @Test
    fun testQuery_EmptyNamespace() {
        val namespace = ""
        val settings = getDefaultSettings(Auth.getAgentAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, namespace))
            .thenReturn(settings)
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(settings, namespace, null))
            .thenReturn(settings)
        Mockito.`when`(sessionManagerBuilder.build(settings).sessionToken.token)
            .thenReturn(TOKEN)

        val query = VaultQuery.extract(VAULT_QUERY)
        val result = VaultResolver.ResolvingResult(
            mapOf(
                query.full to SECRET_VALUE
            ), emptyMap()
        )

        Mockito.`when`(vaultResolver.doFetchAndPrepareReplacements(settings, TOKEN, listOf(query)))
            .thenReturn(result)

        // namespace is empty, property is not sent
        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
        )

        val response = myResponse.returnedContentAsXml.getChild("testConnectionResult")?.value
        Assert.assertNotNull(response)
    }

    @Test
    fun testQuery_NoProjectId() {
        doPost(
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        Assert.assertEquals(myResponse.status, HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun testQuery_WrongProjectId() {
        doPost(
            "prop:${VaultConstants.PROJECT_ID}", "fakeProject",
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        Assert.assertEquals(myResponse.status, HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun testQuery_WrongBuildTypeId() {
        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.BUILD_TYPE_ID}", "fakeBuildType",
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        Assert.assertEquals(myResponse.status, HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun testQuery_MissingVaultQuery() {

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)
        assertk.assertThat(response!!)
            .contains("vault query")
    }

    @Test
    fun testQuery_NamespaceNotSelected() {
        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", VaultConstants.ParameterSettings.NAMESPACE_NOT_SELECTED_VALUE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)
        assertk.assertThat(response!!)
            .contains("connection")
    }

    @Test
    fun testQuery_MissingConnection() {
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
            .thenReturn(null)

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)

        // assert the response contains message & ignore case
        assertk.assertThat(response!!)
            .contains("Failed to find HashiCorp Vault connection", true)
    }

    @Test
    fun testQuery_FailureToGenerateAgentSettings() {
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
            .thenReturn(serverSettings)

        val error = "Mock error"
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE, null))
            .thenThrow(VaultException(error))

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)
        assertk.assertThat(response!!)
            .contains(error)
    }

    @Test
    fun testQuery_FailureToBuildSession() {
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
            .thenReturn(serverSettings)
        val agentSettings = getDefaultSettings(Auth.getAgentAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE, null))
            .thenReturn(agentSettings)
        val error = "Mock error"
        Mockito.`when`(sessionManagerBuilder.build(agentSettings).sessionToken.token)
            .thenThrow(VaultException(error))

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)
        assertk.assertThat(response!!)
            .contains(error)
    }

    @Test
    fun testQuery_FailureToGetReplacements() {
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
            .thenReturn(serverSettings)
        val agentSettings = getDefaultSettings(Auth.getAgentAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE, null))
            .thenReturn(agentSettings)
        Mockito.`when`(sessionManagerBuilder.build(agentSettings).sessionToken.token)
            .thenReturn(TOKEN)

        val query = VaultQuery.extract(VAULT_QUERY)
        VaultResolver.ResolvingResult(
            mapOf(
                query.full to SECRET_VALUE
            ), emptyMap()
        )

        val error = "Mock error"
        Mockito.`when`(vaultResolver.doFetchAndPrepareReplacements(agentSettings, TOKEN, listOf(query)))
            .thenThrow(VaultException(error))

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)
        assertk.assertThat(response!!)
            .contains(error)
    }

    @Test
    fun testQuery_FailureErrorReplacement() {
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
            .thenReturn(serverSettings)
        val agentSettings = getDefaultSettings(Auth.getAgentAuthFromProperties(emptyMap()))

        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE, null))
            .thenReturn(agentSettings)
        Mockito.`when`(sessionManagerBuilder.build(agentSettings).sessionToken.token)
            .thenReturn(TOKEN)

        val error = "Mock error"
        val query = VaultQuery.extract(VAULT_QUERY)
        val result = VaultResolver.ResolvingResult(
            emptyMap(), mapOf(
                query.full to error
            )
        )

        Mockito.`when`(vaultResolver.doFetchAndPrepareReplacements(agentSettings, TOKEN, listOf(query)))
            .thenReturn(result)

        doPost(
            "prop:${VaultConstants.PROJECT_ID}", myProject.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )

        val response = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
        Assert.assertNotNull(response)
        assertk.assertThat(response!!)
            .contains(error)
    }

    @Test
    fun testQuery_WriteEngineProjectOn() {
        projectWriteEngine.addParameter(SimpleParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, "true"))
        testQuery_WriteEngine()
    }

    @Test
    fun testQuery_WriteEngineProjectOff() {
        projectWriteEngine.addParameter(SimpleParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, "false"))
        testQuery_WriteEngine()
    }

    @Test
    fun testQuery_WriteEngineProjectOnBuildTypeOff() {
        projectWriteEngine.addParameter(SimpleParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, "true"))
        buildTypeWriteEngine.addParameter(SimpleParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, "false"))
        testQuery_WriteEngine(buildTypeWriteEngine.externalId)
    }

    @Test
    fun testQuery_WriteEngineProjectOffBuildTypeOn() {
        projectWriteEngine.addParameter(SimpleParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, "false"))
        buildTypeWriteEngine.addParameter(SimpleParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES, "true"))
        testQuery_WriteEngine(buildTypeWriteEngine.externalId)
    }

    fun testQuery_WriteEngine(buildTypeId: String? = null) {
        val buildType = buildTypeId?.let { myProjectManager.findBuildTypeByExternalId(it) }
        val isWriteEngineEnabled =  if (buildType is InternalParameters) {
            buildType.getBooleanInternalParameter(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES)
        } else {
            projectWriteEngine.getParameterValue(VaultConstants.FeatureFlags.FEATURE_ENABLE_WRITE_ENGINES)?.toBoolean() ?: false
        }

        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(projectWriteEngine, VaultTestQueryControllerTest.NAMESPACE))
            .thenReturn(serverSettings)
        val agentSettings = getDefaultSettings(Auth.getAgentAuthFromProperties(emptyMap()))
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, VaultTestQueryControllerTest.NAMESPACE, null))
            .thenReturn(agentSettings)
        Mockito.`when`(sessionManagerBuilder.build(agentSettings).sessionToken.token)
            .thenReturn(VaultTestQueryControllerTest.TOKEN)

        val query = VaultQuery.extract(WRITE_VAULT_QUERY, isWriteEngineEnabled)
        if (isWriteEngineEnabled) {
            val result = VaultResolver.ResolvingResult(
                mapOf(
                    query.full to VaultTestQueryControllerTest.SECRET_VALUE
                ), emptyMap()
            )
            Mockito.`when`(vaultResolver.doFetchAndPrepareReplacements(agentSettings, VaultTestQueryControllerTest.TOKEN, listOf(query)))
                .thenReturn(result)
        } else {
            val errorResult = VaultResolver.ResolvingResult(
                emptyMap(), mapOf(
                    EditVcsRootsController.FAILED_TEST_CONNECTION_ERR to "Error while fetching parameter: write engine exception"
                )
            )
            Mockito.`when`(vaultResolver.doFetchAndPrepareReplacements(agentSettings, VaultTestQueryControllerTest.TOKEN, listOf(query)))
                .thenReturn(errorResult)
        }

        val testRequestParams = arrayOf(
            "prop:${VaultConstants.PROJECT_ID}", projectWriteEngine.externalId,
            "prop:${VaultConstants.ParameterSettings.VAULT_QUERY}", WRITE_VAULT_QUERY,
            "prop:${VaultConstants.ParameterSettings.VAULT_ID}", NAMESPACE,
        )
        if (buildType == null) {
            doPost(*testRequestParams)
        } else {
            doPost(*testRequestParams + "prop:${VaultConstants.BUILD_TYPE_ID}", buildType.externalId)
        }

        if (isWriteEngineEnabled) {
            val response = myResponse.returnedContentAsXml.getChild("testConnectionResult")?.value
            Assert.assertNotNull(response)
        } else {
            val errorResponse = myResponse.returnedContentAsXml.getChild("errors")?.getChild("error")?.value
            Assert.assertNotNull(errorResponse)
        }
    }

    private fun getDefaultSettings(auth: Auth) = VaultFeatureSettings(
        NAMESPACE, "url", "vaultNamespace", auth
    )

    companion object {
        const val VAULT_QUERY = "path/to!/key"
        const val WRITE_VAULT_QUERY = "${VaultQuery.WRITE_PREFIX}path/to!/key"
        const val NAMESPACE = "namespace"
        const val TOKEN = "token"
        const val SECRET_VALUE = "secretValue"
    }
}