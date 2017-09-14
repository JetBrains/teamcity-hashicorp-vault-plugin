/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import jetbrains.buildServer.parameters.ReferencesResolverUtil

object VaultReferencesUtil {

    @JvmStatic fun hasReferences(parameters: Map<String, String>): Boolean {
        for ((_, value) in parameters) {
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
}