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

import org.assertj.core.api.BDDAssertions.then
import org.junit.Test
import java.util.*

class VaultReferencesUtilTest {
    @Test
    fun testSimpleReference() {
        val namespaces = listOf("")
        val map = mapOf("a" to "%vault:/test%")
        then(VaultReferencesUtil.hasReferences(map, namespaces)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, namespaces, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/test")
    }

    @Test
    fun testManyReferencesInOneParameter() {
        val namespaces = listOf("")
        val map = mapOf("a" to "%vault:/testA% %vault:/test B%")
        then(VaultReferencesUtil.hasReferences(map, namespaces)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, namespaces, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/testA", "vault:/test B")
    }

    @Test
    fun testReferencesWithDifferentPrefixes() {
        val namespaces = listOf("", "first", "second")
        val map = mapOf("a" to "%vault:first:/test%", "b" to "%vault:second:/test%", "c" to "%vault:/default%")
        then(VaultReferencesUtil.hasReferences(map, namespaces)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, namespaces, keys)
        then(keys).containsOnlyElementsOf(map.keys)
        then(refs).containsOnly("vault:first:/test", "vault:second:/test", "vault:/default")
    }

    @Test
    fun testReferencesWithOtherPrefixiesNotSelected() {
        val namespaces = listOf("")
        val map = mapOf("a" to "%vault:first:/test%", "b" to "%vault:second:/test%", "c" to "%vault:/default%")
        then(VaultReferencesUtil.hasReferences(map, namespaces)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, namespaces, keys)
        then(keys).containsOnly("c")
        then(refs).containsOnly("vault:/default")
    }

    @Test
    fun testReferencesIgnoredInDepParameters() {
        val namespaces = listOf("")
        val map = mapOf("a" to "%vault:/test%", "dep.type.a" to "%vault:/test-dep%")
        then(VaultReferencesUtil.hasReferences(map, namespaces)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, namespaces, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/test")
    }

    @Test
    fun testPathExtractedCorrectly() {
        doVaultPathTest("first", "vault:first:/test", "/test")
        doVaultPathTest("second", "vault:second:/test", "/test")
        doVaultPathTest("", "vault:/test", "/test")
        doVaultPathTest("", "vault:/test!/inner", "/test!/inner")

        // Adds leading slash
        doVaultPathTest("", "vault:test", "/test")
        doVaultPathTest("", "vault:test!/inner", "/test!/inner")
    }

    private fun doVaultPathTest(namespace: String, text: String, expected: String) {
        then(VaultReferencesUtil.getPath(text, namespace)).isEqualTo(expected)
        then(VaultReferencesUtil.getPath(text, VaultReferencesUtil.getNamespace(text))).isEqualTo(expected)
    }

    @Test
    fun testVaultNamespaceDetection() {
        doNamespaceTest("vault:/path", "")
        doNamespaceTest("vault:ns:/path", "ns")

        doNamespaceTest("vault:/path:with:colons", "")
        doNamespaceTest("vault:ns:path:with:colons", "ns")
    }

    private fun doNamespaceTest(string: String, expected: String) {
        then(VaultReferencesUtil.getNamespace(string)).isEqualTo(expected)
    }
}