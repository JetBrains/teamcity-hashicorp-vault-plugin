package org.jetbrains.teamcity.vault

import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.BaseTestCase
import org.jetbrains.teamcity.vault.VaultResolver.VaultParametersFetcher
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.mockito.Mockito
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.vault.support.VaultResponse
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.testng.Assert
import org.testng.Assert.*
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.nio.charset.Charset

class VaultResolverTest : BaseTestCase(){
    lateinit var vaultTemplate: VaultTemplate
    lateinit var vaultParametersFetcher: VaultParametersFetcher

    @BeforeMethod
    override fun setUp() {
        super.setUp()
        vaultTemplate = Mockito.mock(VaultTemplate::class.java)
        vaultParametersFetcher = VaultParametersFetcher(vaultTemplate)
    }

    @Test
    fun testFetch() {
        val query = VaultQuery(PATH)
        val response = VaultResponse()
        response.data = mapOf(VaultResolver.DATA_KEY to VALUE)
        Mockito.`when`(vaultTemplate.read(PATH)).thenReturn(response)

        val (replacements, errors) = vaultParametersFetcher.doFetchAndPrepareReplacements(listOf(query))
        assertk.assertThat(errors).isEmpty()
        assertk.assertThat(replacements).contains("/$PATH" to VALUE)
    }

    @Test
    fun testFetch_WithError() {
        val query = VaultQuery(PATH)
        Mockito.`when`(vaultTemplate.read(PATH)).thenThrow(RuntimeException("error"))

        val (replacements, errors) = vaultParametersFetcher.doFetchAndPrepareReplacements(listOf(query))
        assertk.assertThat(errors).isNotEmpty()
        assertk.assertThat(replacements).isEmpty()
    }

    @Test
    fun testFetch_WithFirstHttpErrorRetries() {
        val query = VaultQuery(PATH)
        val response = VaultResponse()
        response.data = mapOf(VaultResolver.DATA_KEY to VALUE)
        Mockito.`when`(vaultTemplate.read(PATH))
            .thenThrow(HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Mock error", HttpHeaders.EMPTY, byteArrayOf(), Charset.defaultCharset()))
            .thenReturn(response)

        val (replacements, errors) = vaultParametersFetcher.doFetchAndPrepareReplacements(listOf(query))
        assertk.assertThat(errors).isEmpty()
        assertk.assertThat(replacements).contains("/$PATH" to VALUE)
    }

    companion object {
        const val PATH = "path"
        const val VALUE = "value"
        private val objectMapper = jacksonObjectMapper()
    }
}