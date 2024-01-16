
package org.jetbrains.teamcity.vault

import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultToken
import java.net.URI


interface VaultDevEnvironment {
    val token: String
    val url: String
    val endpoint: VaultEndpoint
        get() = VaultEndpoint.from(URI.create(url))
    val simpleSessionManager: SimpleSessionManager
        get() = SimpleSessionManager { VaultToken.of(token) }
}