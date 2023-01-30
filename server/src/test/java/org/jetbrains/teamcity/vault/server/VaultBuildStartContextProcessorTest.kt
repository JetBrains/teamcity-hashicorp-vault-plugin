package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SProject
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

//TODO: Add tests for the already existing functionality - TW-79369
@RunWith(MockitoJUnitRunner::class)
class VaultBuildStartContextProcessorTest{
    @Mock
    private lateinit var context: BuildStartContext
    @Mock
    private lateinit var build: SBuild
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var parameter: Parameter
    @Mock
    private lateinit var connector: VaultConnector
    private val client by lazy {
        VaultBuildStartContextProcessor(connector)
    }

    @Test
    fun testAddVaultParameters(){
        val vaultParameterSettings = VaultParameterSettings(NAMESPACE, VAULT_QUERY)
        Mockito.`when`(parameter.controlDescription?.parameterTypeArguments).thenReturn(vaultParameterSettings.toMap())
        Mockito.`when`(parameter.name).thenReturn(PARAMETER_NAME)

        val settings = listOf(
            VaultFeatureSettings(NAMESPACE, "http://localhost:8200", "vaultNamespace", true, Auth.fromProperties(emptyMap()))
        )
        client.addVaultParameters(settings, listOf(parameter), context)
        Mockito.verify(context).addSharedParameter(PARAMETER_NAME, VaultReferencesUtil.makeVaultReference(vaultParameterSettings))
    }

    @Test
    fun testAddVaultParameters_DefaultNamespace(){
        val vaultParameterSettings = VaultParameterSettings(ParameterSettings.DEFAULT_UI_PARAMETER_NAMESPACE, VAULT_QUERY)
        Mockito.`when`(parameter.controlDescription?.parameterTypeArguments).thenReturn(vaultParameterSettings.toMap())
        Mockito.`when`(parameter.name).thenReturn(PARAMETER_NAME)

        val settings = listOf(
            VaultFeatureSettings("http://localhost:8200", NAMESPACE)
        )
        client.addVaultParameters(settings, listOf(parameter), context)
        Mockito.verify(context).addSharedParameter(PARAMETER_NAME, VaultReferencesUtil.makeVaultReference(vaultParameterSettings))
    }

    companion object {
        const val VAULT_QUERY = "vault/query!/value"
        const val NAMESPACE = "namespace"
        const val PARAMETER_NAME = "parameterName"
    }
}