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

import jetbrains.buildServer.util.StringUtil
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.http.client.ClientHttpRequestFactory

object VaultTestUtil {
    @JvmStatic
    fun createNamespaceAndTemplate(vault: VaultDevEnvironment,
                                   factory: ClientHttpRequestFactory,
                                   namespace: String): VaultTemplate {
        val template = VaultTemplate(vault.endpoint, namespace, factory, vault.simpleSessionManager)
        if (StringUtil.isNotEmpty(namespace)) {
            val templateWithoutNamespace = VaultTemplate(vault.endpoint, "", factory, vault.simpleSessionManager)
            val exists = templateWithoutNamespace.read("/sys/namespaces/$namespace")
            if (exists == null) {
                // Add namespace if it doesn't exist
                templateWithoutNamespace.write("/sys/namespaces/$namespace", null)
                // Add secret backend in namespace with explicit version
                template.write("/sys/mounts/secret", "{\"type\": \"kv\", \"options\": {\"version\": \"2\"}}")
            }
        }
        return template
    }
}