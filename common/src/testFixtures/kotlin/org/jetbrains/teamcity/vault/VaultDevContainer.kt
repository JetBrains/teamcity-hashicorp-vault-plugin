/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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