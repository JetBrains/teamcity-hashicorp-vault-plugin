package org.jetbrains.teamcity.vault.agent

import jetbrains.buildServer.AgentServerFunctionalTestCase
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.impl.BaseAgentSpringTestCase
import jetbrains.buildServer.util.HTTPRequestBuilder
import jetbrains.buildServer.util.HTTPRequestBuilder.ApacheClient43RequestHandler
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.mockito.Mockito
import org.testng.annotations.Test

import org.testng.Assert.*
import org.testng.annotations.BeforeMethod

class VaultFeatureSettingsFetcherTest : BaseAgentSpringTestCase() {
    lateinit var vaultFeatureSettingsFetcher: VaultFeatureSettingsFetcher
    lateinit var requestHandler: HTTPRequestBuilder.RequestHandler
    lateinit var build: AgentRunningBuild

    @BeforeMethod
    override fun setUp1() {
        super.setUp1()
        requestHandler = Mockito.mock(HTTPRequestBuilder.RequestHandler::class.java)
        vaultFeatureSettingsFetcher = VaultFeatureSettingsFetcher(Mockito.mock(SSLTrustStoreProvider::class.java), requestHandler)
        build = Mockito.mock(AgentRunningBuild::class.java)
        Mockito.`when`(build.agentConfiguration).thenReturn(buildAgentConfiguration)
    }

    @Test
    fun testGetVaultFeatureSettings() {
        vaultFeatureSettingsFetcher.getVaultFeatureSettings("namespace", build)
    }
}