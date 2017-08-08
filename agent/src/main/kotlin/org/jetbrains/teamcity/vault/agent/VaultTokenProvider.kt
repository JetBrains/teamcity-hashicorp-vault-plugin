package org.jetbrains.teamcity.vault.agent

import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.createRestTemplate
import org.springframework.vault.authentication.CubbyholeAuthentication
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions
import org.springframework.vault.authentication.LoginToken
import org.springframework.vault.support.VaultToken

object VaultTokenProvider {

    fun unwrap(settings: VaultFeatureSettings, wrapped: String): String {
        val options = CubbyholeAuthenticationOptions.builder()
                .wrapped()
                .initialToken(VaultToken.of(wrapped))
                .build()

        val template = createRestTemplate(settings)
        val authentication = CubbyholeAuthentication(options, template)

        val token = authentication.login()
        if (token is LoginToken) {
            // TODO: Store token details for autorenew procedure
        }
        return token.token
    }
}