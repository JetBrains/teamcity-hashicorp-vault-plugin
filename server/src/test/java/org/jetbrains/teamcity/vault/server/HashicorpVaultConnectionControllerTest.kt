package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.impl.BaseServerTestCase
import jetbrains.buildServer.web.util.WebAuthUtil
import org.jetbrains.teamcity.vault.Auth
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.server.HashiCorpVaultConnectionResolver.ParameterNamespaceCollisionException
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.testng.MockitoTestNGListener
import org.springframework.web.server.ResponseStatusException
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import javax.servlet.http.HttpServletRequest

@Listeners(MockitoTestNGListener::class)
class HashicorpVaultConnectionControllerTest : BaseServerTestCase() {
    @Mock
    private lateinit var hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver
    @Mock
    private lateinit var request: HttpServletRequest
    private lateinit var controller: HashicorpVaultConnectionController

    @BeforeMethod
    override fun setUp() {
        super.setUp()
        controller = HashicorpVaultConnectionController(myProjectManager, myFixture.buildsManager, hashiCorpVaultConnectionResolver)
    }

    @Test
    fun testGetToken() {
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))
        Mockito.`when`(request.getAttribute(WebAuthUtil.TEAM_CITY_AUTHENTICATED_BUILD)).thenReturn(build.buildId)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
                .thenReturn(serverSettings)

        val agentSettings = getDefaultSettings(
            Auth.getAgentAuthFromProperties(
                emptyMap()
            )
        )
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE)).thenReturn(agentSettings)

        val settingsMap = controller.getToken(NAMESPACE, request)
        Assert.assertEquals(settingsMap, agentSettings.toFeatureProperties())
    }

    @Test(expectedExceptions = [ResponseStatusException::class])
    fun testGetToken_SecondAttemptFails() {
        myTestLogger?.doNotFailOnErrorMessages()
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())
        val serverSettings = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))
        Mockito.`when`(request.getAttribute(WebAuthUtil.TEAM_CITY_AUTHENTICATED_BUILD)).thenReturn(build.buildId)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
                .thenReturn(serverSettings)

        val agentSettings = getDefaultSettings(
            Auth.getAgentAuthFromProperties(
                emptyMap()
            )
        )
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings, NAMESPACE)).thenReturn(agentSettings)

        val settingsMap = controller.getToken(NAMESPACE, request)
        Assert.assertEquals(settingsMap, agentSettings.toFeatureProperties())
        controller.getToken(NAMESPACE, request)
    }

    @Test
    fun testGetToken_SecondAttemptForAnotherFeatureWorks() {
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())
        val serverSettings1 = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()))
        val namespace2 = "$NAMESPACE-2"
        val serverSettings2 = getDefaultSettings(Auth.getServerAuthFromProperties(emptyMap()), namespace2)
        Mockito.`when`(request.getAttribute(WebAuthUtil.TEAM_CITY_AUTHENTICATED_BUILD)).thenReturn(build.buildId)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
                .thenReturn(serverSettings1)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, namespace2))
                .thenReturn(serverSettings2)

        val agentSettings1 = getDefaultSettings(
            Auth.getAgentAuthFromProperties(
                emptyMap()
            )
        )
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings1, NAMESPACE)).thenReturn(agentSettings1)

        val agentSettings2 = getDefaultSettings(
            Auth.getAgentAuthFromProperties(
                emptyMap()
            ),
            namespace2
        )
        Mockito.`when`(hashiCorpVaultConnectionResolver.serverFeatureSettingsToAgentSettings(serverSettings2, namespace2)).thenReturn(agentSettings2)

        val settingsMap1 = controller.getToken(NAMESPACE, request)
        Assert.assertEquals(settingsMap1, agentSettings1.toFeatureProperties())
        val settingsMap2 = controller.getToken(namespace2, request)
        Assert.assertEquals(settingsMap2, agentSettings2.toFeatureProperties())
    }

    @Test(expectedExceptions = [ResponseStatusException::class])
    fun testGetToken_NoBuildId() {
        controller.getToken(NAMESPACE, request)
    }


    @Test(expectedExceptions = [ResponseStatusException::class])
    fun testGetToken_NoAppropriateVaultFeature() {
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())
        Mockito.`when`(request.getAttribute(WebAuthUtil.TEAM_CITY_AUTHENTICATED_BUILD)).thenReturn(build.buildId)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
                .thenReturn(null)

        controller.getToken(NAMESPACE, request)
    }


    @Test(expectedExceptions = [ResponseStatusException::class])
    fun testGetToken_NamespaceCollision() {
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())
        Mockito.`when`(request.getAttribute(WebAuthUtil.TEAM_CITY_AUTHENTICATED_BUILD)).thenReturn(build.buildId)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getVaultConnection(myProject, NAMESPACE))
                .thenThrow(ParameterNamespaceCollisionException(NAMESPACE, myProject.projectId))

        controller.getToken(NAMESPACE, request)
    }

    private fun getDefaultSettings(auth: Auth, namespace: String = NAMESPACE ) = VaultFeatureSettings(
        namespace, "url", "vaultNamespace", true, auth
    )

    companion object {
        const val NAMESPACE = "namespace"
    }
}