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

    @JvmStatic fun hasReferences(parameters: Map<String, String>, prefixes: Collection<String>): Boolean {
        for ((_, value) in parameters) {
            if (!ReferencesResolverUtil.mayContainReference(value)) continue
            val refs = getVaultReferences(value,prefixes)
            if (refs.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    @JvmStatic fun collect(parameters: Map<String, String>, references: MutableCollection<String>, prefix: String, keys: MutableCollection<String>? = null) {
        val prefixes = ArrayList<String>(1)
        prefixes.add(prefix)
        collect(parameters, references, prefixes,keys)
    }
    @JvmStatic fun collect(parameters: Map<String, String>, references: MutableCollection<String>, prefixes: Collection<String>, keys: MutableCollection<String>? = null) {
        for ((key, value) in parameters) {
            if (!ReferencesResolverUtil.mayContainReference(value)) continue
            val refs = getVaultReferences(value,prefixes)
            if (refs.isNotEmpty()) {
                keys?.add(key)
                references.addAll(refs)
            }
        }
    }

    @JvmStatic fun getVaultPath(ref: String, prefix: String): String {
        return ref.removePrefix(prefix + ":").ensureHasPrefix("/")
    }

    private fun getVaultReferences(value: String, prefixes: Collection<String>): Collection<String> {
        if (!prefixes.any { prefix -> value.contains(prefix + ":") }) return emptyList()

        val references = ArrayList<String>(prefixes.count())
        prefixes.forEach { prefix ->
            references.addAll(ReferencesResolverUtil.getReferences(value, arrayOf(prefix + ":"), true))
        }
        return references
    }
}