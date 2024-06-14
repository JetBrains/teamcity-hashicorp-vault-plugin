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

import com.google.auth.oauth2.GoogleCredentials
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.Auth
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.createRestTemplate
import org.jetbrains.teamcity.vault.data.VaultTokenData
import org.jetbrains.teamcity.vault.withVaultToken
import org.springframework.vault.VaultException
import org.springframework.vault.authentication.GcpIamCredentialsAuthentication
import org.springframework.vault.authentication.GcpIamCredentialsAuthenticationOptions
import org.springframework.vault.support.VaultResponse
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

private const val VAULT_LOOKUP_SELF_URL = "/auth/token/lookup-self"
private const val ACCESSOR_PARAMETER = "accessor"

class GcpAuthenticationHandler(private val trustStoreProvider: SSLTrustStoreProvider) {

    fun vaultTokenData(settings: VaultFeatureSettings): VaultTokenData {

        if (settings.auth !is Auth.GcpIamAuth) {
            error("Unsupported auth method: ${settings.auth.method}, class: ${settings.auth::class.qualifiedName}")
        }

        val gcpIamAuth = settings.auth
        val template = createRestTemplate(settings, trustStoreProvider)
        val gcpAuth = gcpIamCredentialsAuthentication(gcpIamAuth, template)

        try {
            return retrieveLeasedTokenInfo(gcpAuth, template)
        } catch (e: VaultException) {
            val cause = e.cause

            if (cause is HttpStatusCodeException) {
                throw VaultException("Cannot log in to HashiCorp Vault using ${gcpIamAuth.method.name} method: ${cause.message}")
            }

            throw e
        }
    }

    fun gcpIamCredentialsAuthentication(
        gcpIamAuth: Auth.GcpIamAuth,
        template: RestTemplate
    ): GcpIamCredentialsAuthentication {
        val gcpAuthOptions =
            GcpIamCredentialsAuthenticationOptions.builder()
                .role(gcpIamAuth.role)
                .path(gcpIamAuth.endpointPath)
                .credentials(GoogleCredentials.getApplicationDefault())

        val serviceAccId = gcpIamAuth.serviceAccount
        if (serviceAccId.isNotEmpty()) {
            gcpAuthOptions.serviceAccountId(serviceAccId)
        }

        return GcpIamCredentialsAuthentication(gcpAuthOptions.build(), template)
    }

    private fun retrieveLeasedTokenInfo(
        gcpAuth: GcpIamCredentialsAuthentication,
        template: RestTemplate,
    ): VaultTokenData {
        val vaultToken = gcpAuth.login().token ?: throw VaultException("Failed to obtain a token from GCP services")

        val tokenData =
            template.withVaultToken(vaultToken).getForEntity(VAULT_LOOKUP_SELF_URL, VaultResponse::class.java)

        val vaultData = tokenData.body?.data
            ?: throw VaultException("HashiCorp Vault hasn't returned an expected response from $VAULT_LOOKUP_SELF_URL")
        val accessor = (vaultData[ACCESSOR_PARAMETER]
            ?: throw VaultException("HashiCorp Vault hasn't returned an '$ACCESSOR_PARAMETER' parameter")) as String

        return VaultTokenData(vaultToken, accessor)
    }
}