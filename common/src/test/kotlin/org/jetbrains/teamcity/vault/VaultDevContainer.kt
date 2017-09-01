package org.jetbrains.teamcity.vault

import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultToken
import org.testcontainers.containers.GenericContainer
import java.net.URI
import java.util.*

val vault_version = "0.7.3"

class VaultDevContainer(val token: String = UUID.randomUUID().toString()) : GenericContainer<VaultDevContainer>("vault:$vault_version") {
    init {
        withExposedPorts(8200)
        withEnv("VAULT_DEV_ROOT_TOKEN_ID", token)
    }

    val url: String
        get() = "http://$containerIpAddress:$firstMappedPort"

    val endpoint: VaultEndpoint
        get() = VaultEndpoint.from(URI.create(url))

    val simpleSessionManager: SimpleSessionManager get() = SimpleSessionManager { VaultToken.of(token) }
}