
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.util.ThreadUtil
import org.testcontainers.containers.GenericContainer
import java.util.*

open class VaultDevContainer(override val token: String = UUID.randomUUID().toString(), val version: String = "1.4.1")
    : GenericContainer<VaultDevContainer>("hashicorp/vault-enterprise:${version}_ent"), VaultDevEnvironment {
    init {
        withExposedPorts(8200)
        withEnv("VAULT_DEV_ROOT_TOKEN_ID", token)
    }

    override fun start() {
        super.start()
        ThreadUtil.sleep(500)
    }

    override val url: String
        get() = "http://$containerIpAddress:$firstMappedPort"

}