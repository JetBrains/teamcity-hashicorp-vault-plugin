package org.jetbrains.teamcity.vault

import jetbrains.buildServer.parameters.ReferencesResolverUtil

object VaultReferencesUtil {

    @JvmStatic fun hasReferences(parameters: Map<String, String>): Boolean {
        for ((key, value) in parameters) {
            if (!ReferencesResolverUtil.mayContainReference(value)) continue
            val refs = getVaultReferences(value)
            if (refs.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    @JvmStatic fun collect(parameters: Map<String, String>, references: MutableCollection<String>, keys: MutableCollection<String>? = null) {
        for ((key, value) in parameters) {
            if (!ReferencesResolverUtil.mayContainReference(value)) continue
            val refs = getVaultReferences(value)
            if (refs.isNotEmpty()) {
                keys?.add(key)
                references.addAll(refs)
            }
        }
    }

    @JvmStatic fun getVaultPath(ref: String): String {
        return ref.removePrefix(VaultConstants.VAULT_PARAMETER_PREFIX).ensureHasPrefix("/")
    }

    private fun getVaultReferences(value: String): Collection<String> {
        if (!value.contains(VaultConstants.VAULT_PARAMETER_PREFIX)) return emptyList()

        val references = ArrayList<String>(1)
        ReferencesResolverUtil.resolve(value, object : ReferencesResolverUtil.ReferencesResolverListener {
            override fun appendText(text: String) {}

            override fun appendReference(referenceKey: String): Boolean {
                if (referenceKey.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX)) {
                    references.add(referenceKey)
                }
                return true
            }

        })
        return references
    }

    /**
     * @return value with resolved references or null if string is not modified
     */
    @JvmStatic fun resolve(value: String, replacements: Map<String, String>): String? {
        val result = StringBuilder(value.length)
        ReferencesResolverUtil.resolve(value, object : ReferencesResolverUtil.ReferencesResolverListener {
            override fun appendReference(referenceKey: String): Boolean {
                if (!referenceKey.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX)) return false
                val unprefixed = referenceKey.removePrefix(VaultConstants.VAULT_PARAMETER_PREFIX).ensureHasPrefix("/")
                val replacement = replacements[unprefixed] ?: return false
                result.append(replacement)
                return true
            }

            override fun appendText(text: String) {
                result.append(text)
            }
        })
        val resolved = result.toString()
        if (resolved == value) return null
        return resolved
    }

}