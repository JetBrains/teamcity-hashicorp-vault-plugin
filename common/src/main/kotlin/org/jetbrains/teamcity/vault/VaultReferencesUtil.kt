
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.parameters.ReferencesResolverUtil

object VaultReferencesUtil {

    @JvmStatic
    fun hasReferences(parameters: Map<String, String>, namespaces: Collection<String>): Boolean {
        for ((_, value) in parameters) {
            if (!ReferencesResolverUtil.mayContainReference(value)) continue
            val refs = getVaultReferences(value, namespaces)
            if (refs.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun collect(parameters: Map<String, String>, references: MutableCollection<String>, namespace: String, keys: MutableCollection<String>? = null) {
        collect(parameters, references, arrayListOf(namespace), keys)
    }

    @JvmStatic
    fun collect(parameters: Map<String, String>, references: MutableCollection<String>, namespaces: Collection<String>, keys: MutableCollection<String>? = null) {
        for ((key, value) in parameters) {
            if (key.startsWith("dep.")) continue
            if (!ReferencesResolverUtil.mayContainReference(value)) continue
            val refs = getVaultReferences(value, namespaces)
            if (refs.isNotEmpty()) {
                keys?.add(key)
                references.addAll(refs)
            }
        }
    }

    @JvmStatic
    fun getPath(ref: String, namespace: String): String {
        val prefix = VaultConstants.VAULT_PARAMETER_PREFIX + if (isDefault(namespace)) "" else "$namespace:"
        return ref.removePrefix(prefix).ensureHasPrefix("/")
    }


    @JvmStatic
    fun getNamespace(ref: String): String {
        val value = ref.removePrefix(VaultConstants.VAULT_PARAMETER_PREFIX)
        val colon = value.indexOf(':')
        val slash = value.indexOf('/')
        if (colon < 0 || (slash in 0..(colon - 1))) return ""
        return value.substring(0, colon)
    }

    @JvmStatic
    fun makeVaultReference(namespace: String, query: String): String {
        val vaultNamespacePrefix =
            if (isDefault(namespace)) "" else "${namespace}:"
        val referenceableVaultParameter =
            ReferencesResolverUtil.makeReference("${VaultConstants.VAULT_PARAMETER_PREFIX}$vaultNamespacePrefix${query}")
        return referenceableVaultParameter
    }

    private fun getVaultReferences(value: String, namespaces: Collection<String>): Collection<String> {
        val prefixes = namespaces.map { VaultConstants.VAULT_PARAMETER_PREFIX + if (isDefault(it)) "" else "$it:" }
        if (!prefixes.any { prefix -> value.contains(prefix) }) return emptyList()

        val references = ReferencesResolverUtil.getReferences(value, prefixes.toTypedArray(), true)
        // If default namespace provided we may found references from other nemaspaces, let's filter them out
        return references.filter { namespaces.contains(getNamespace(it)) }
    }
}