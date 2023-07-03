package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.impl.BaseServerTestCase
import jetbrains.buildServer.web.util.WebAuthUtil
import org.jetbrains.teamcity.vault.Auth
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.testng.MockitoTestNGListener
import org.springframework.web.server.ResponseStatusException
import org.testng.Assert
import org.testng.Assert.*
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
        Mockito.`when`(hashiCorpVaultConnectionResolver.getProjectToConnectionPairs(myProject)).thenReturn(
            listOf(
                myProject.projectId to serverSettings,
                myProject.projectId to VaultFeatureSettings("url", "vaultNamespace")
            )
        )

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
    fun testGetToken_NoBuildId() {
        controller.getToken(NAMESPACE, request)
    }



    @Test(expectedExceptions = [ResponseStatusException::class])
    fun testGetToken_NoAppropriateVaultFeature() {
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())
        Mockito.`when`(request.getAttribute(WebAuthUtil.TEAM_CITY_AUTHENTICATED_BUILD)).thenReturn(build.buildId)
        Mockito.`when`(hashiCorpVaultConnectionResolver.getProjectToConnectionPairs(myProject)).thenReturn(
            listOf(
                myProject.projectId to VaultFeatureSettings("url", "vaultNamespace")
            )
        )

        controller.getToken(NAMESPACE, request)
    }

    private fun getDefaultSettings(auth: Auth) = VaultFeatureSettings(
        NAMESPACE, "url", "vaultNamespace", true, auth
    )

    companion object {
        const val NAMESPACE = "namespace"
    }
}