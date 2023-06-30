package org.jetbrains.teamcity.vault.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.serverSide.ControlDescription
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameterConstants
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jetbrains.teamcity.vault.*
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test


//TODO: Add tests for the already existing functionality - TW-79369
@Listeners(MockitoTestNGListener::class)
class HashiCorpVaultParameterTest : BaseServerTestCase() {

    private lateinit var client: HashiCorpVaultParameter
    private val objectMapper = jacksonObjectMapper()

    @BeforeMethod
    override fun setUp() {
        super.setUp()
        client = HashiCorpVaultParameter(Mockito.mock(PluginDescriptor::class.java))
    }

    @Test
    fun testCreateVaultParameter() {
        testVaultParameter(NAMESPACE, NAMESPACE)
    }

    @Test
    fun testCreateVaultParameter_DefaultNamespace() {
        testVaultParameter(VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE, "")
    }

    private fun testVaultParameter(storedNamespace: String, expectedNamespace: String) {
        val vaultParameterSettings = VaultParameterSettings(storedNamespace, VAULT_QUERY)
        val vaultParameter = parameterFactory.createParameter(PARAMETER_NAME, "", object : ControlDescription {
            override fun getParameterType(): String = RemoteParameterConstants.PARAMETER_TYPE

            override fun getParameterTypeArguments(): Map<String, String> {
                return mapOf(
                    RemoteParameterConstants.REMOTE_TYPE_PARAM to VaultConstants.PARAMETER_TYPE
                ) + vaultParameterSettings.toMap()
            }

        })
        myBuildType.addParameter(vaultParameter)
        val settings =
            VaultFeatureSettings(expectedNamespace, URL, VAULT_NAMESPACE, true, Auth.getServerAuthFromProperties(emptyMap()))
        myProject.addFeature(
            OAuthConstants.FEATURE_TYPE, mapOf(
                OAuthConstants.OAUTH_TYPE_PARAM to VaultConstants.FeatureSettings.FEATURE_TYPE
            ) + settings.toFeatureProperties()
        )
        val build = createRunningBuild(myBuildType, emptyArray(), emptyArray())

        val remoteParameter = client.createRemoteParameter(build, vaultParameter)
        Assert.assertEquals(remoteParameter.value, "")
        Assert.assertEquals(remoteParameter.name, PARAMETER_NAME)
        Assert.assertTrue(remoteParameter.isSecret)
    }

    companion object {
        const val VAULT_QUERY = "vault/query!/value"
        const val NAMESPACE = "namespace"
        const val PARAMETER_NAME = "parameterName"
        const val TOKEN = "mockToken"
        private const val URL = "http://localhost:8200"
        private const val VAULT_NAMESPACE = "vaultNamespace"
    }
}