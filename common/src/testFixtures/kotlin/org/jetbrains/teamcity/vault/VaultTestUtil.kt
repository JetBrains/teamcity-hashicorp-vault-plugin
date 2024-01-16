
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