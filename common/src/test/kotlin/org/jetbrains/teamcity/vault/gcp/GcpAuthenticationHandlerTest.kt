/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.gcp

import com.jayway.jsonpath.JsonPath
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.assertj.core.api.Assertions
import org.jetbrains.teamcity.vault.*
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.vault.VaultException
import org.springframework.vault.authentication.GcpIamCredentialsAuthentication
import org.springframework.vault.support.VaultToken
import org.springframework.web.client.RestTemplate
import org.testcontainers.lifecycle.Startables
import org.testng.annotations.Test

class GcpAuthenticationHandlerTest {

    @Test
    fun failIfAuthOfWrongType() {
        val trustStoreMock = Mockito.mock(SSLTrustStoreProvider::class.java)
        val handler = GcpAuthenticationHandler(trustStoreMock)

        val wrongSettings =
            VaultFeatureSettings("id", "url", "namespace", Auth.AppRoleAuthServer("endp", "role", "secret"))
        Assertions.assertThatThrownBy { handler.vaultTokenData(wrongSettings) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageMatching(".*Unsupported auth method.*")
    }

    @Test
    fun failIfNoTokenProvidedFromGcpServices() {
        val trustStoreMock = SSLTrustStoreProvider { null }
        val handler = Mockito.spy(GcpAuthenticationHandler(trustStoreMock))
        val gcpAuthenticationMock = Mockito.mock(GcpIamCredentialsAuthentication::class.java)
        val gcpIamAuth = Auth.GcpIamAuth("kjk", "role", "gcp")

        val restMock = Mockito.mock(RestTemplate::class.java)
        val vaultSettings = VaultFeatureSettings("id", "http://localhost", "namespace", gcpIamAuth)
        val token = Mockito.mock(VaultToken::class.java)

        Mockito.doReturn(gcpAuthenticationMock)
            .`when`(handler).gcpIamCredentialsAuthentication(
                Mockito.nullable(Auth.GcpIamAuth::class.java) ?: gcpIamAuth,
                Mockito.nullable(RestTemplate::class.java) ?: restMock
            )
        Mockito.`when`(gcpAuthenticationMock.login())
            .thenReturn(token)
        Mockito.`when`(token.token)
            .thenReturn(null)

        Assertions.assertThatThrownBy { handler.vaultTokenData(vaultSettings) }
            .isInstanceOf(VaultException::class.java)
            .hasMessageMatching(".*Failed to obtain a token from GCP services.*")

    }

    @Test
    fun testCorrectTokenRetrieval() {
        val user = "myUser"
        val pass = "myPass"
        VaultDevContainer().use {
            it.start()
            Startables.deepStart(it)
                .join()

            val template = createRestTemplate(it.endpoint, createClientHttpRequestFactory { null })
            val headers = HttpHeaders().apply {
                set("X-Vault-Token", it.token)
            }

            enableUserpass(headers, template)
            createUserWithPass(user, pass, headers, template)
            val (token, accessor) = tokenAccessorPair(user, pass, headers, template)

            val trustStoreMock = SSLTrustStoreProvider { null }
            val handler = Mockito.spy(GcpAuthenticationHandler(trustStoreMock))
            val gcpAuthenticationMock = Mockito.mock(GcpIamCredentialsAuthentication::class.java)
            val gcpIamAuth = Auth.GcpIamAuth("kjk", "role", "gcp")

            val restMock = Mockito.mock(RestTemplate::class.java)
            val vaultSettings = VaultFeatureSettings("id", "${it.url}/v1", "", gcpIamAuth)
            val vaultToken = VaultToken.of(token)

            Mockito.doReturn(gcpAuthenticationMock)
                .`when`(handler).gcpIamCredentialsAuthentication(
                    Mockito.nullable(Auth.GcpIamAuth::class.java) ?: gcpIamAuth,
                    Mockito.nullable(RestTemplate::class.java) ?: restMock
                )
            Mockito.`when`(gcpAuthenticationMock.login())
                .thenReturn(vaultToken)

            val res = handler.vaultTokenData(vaultSettings)

            Assertions.assertThat(res.token)
                .isEqualTo(token)
            Assertions.assertThat(res.tokenAccessor)
                .isEqualTo(accessor)
        }
    }

    private fun tokenAccessorPair(
        user: String,
        pass: String,
        headers: HttpHeaders,
        template: RestTemplate
    ): Pair<String, String> {
        val loginUrl = "/auth/userpass/login/$user"
        val loginRequest: HttpEntity<String?> = HttpEntity("{\"password\": \"$pass\"}", headers)
        val loginResp =
            template.exchange(loginUrl, HttpMethod.POST, loginRequest, String::class.java)
        Assertions.assertThat(loginResp.statusCode.is2xxSuccessful).isTrue()
        val loginBody: String? = loginResp.body
        val token: String = JsonPath.read(loginBody, "$.auth.client_token")
        val accessor: String = JsonPath.read(loginBody, "$.auth.accessor")
        Assertions.assertThat(token)
            .isNotNull()
            .isNotEmpty()
        Assertions.assertThat(accessor)
            .isNotNull()
            .isNotEmpty()

        return Pair(token, accessor)
    }

    private fun createUserWithPass(
        user: String,
        pass: String,
        headers: HttpHeaders,
        template: RestTemplate
    ) {
        val createUser = "/auth/userpass/users/$user"
        val requestBody = "{\"password\": \"$pass\", \"policies\": [\"default\"]}"
        val createUserReq = HttpEntity(requestBody, headers)

        val response = template.exchange(createUser, HttpMethod.POST, createUserReq, Map::class.java)
        println(response)
        Assertions.assertThat(response.statusCode.is2xxSuccessful).isTrue()
    }

    private fun enableUserpass(
        headers: HttpHeaders,
        template: RestTemplate
    ) {
        val userpassUrl = "/sys/auth/userpass"

        val roleRequest = HttpEntity("{\"type\": \"userpass\"}", headers)
        val resp = template.exchange(userpassUrl, HttpMethod.POST, roleRequest, String::class.java)
        println(resp)
        Assertions.assertThat(resp.statusCode.is2xxSuccessful).isTrue()
    }
}