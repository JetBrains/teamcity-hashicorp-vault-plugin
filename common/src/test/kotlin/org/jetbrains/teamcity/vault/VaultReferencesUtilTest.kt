
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.parameters.ReferencesResolverUtil
import org.assertj.core.api.BDDAssertions.then
import org.testng.annotations.Test
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

    @Test
    fun testMakeVaultReference(){
        doVaultPathTest(
            "ns",
            getReference("ns", "path"),
            "/path")
        doVaultPathTest(
            "",
            getReference(VaultConstants.FeatureSettings.DEFAULT_ID, "path"),
            "/path")
    }

    private fun getReference(namespace: String, query: String): String =
        ReferencesResolverUtil.getReferences(VaultReferencesUtil.makeVaultReference(namespace, query), emptyArray(), true).first()

    private fun doNamespaceTest(string: String, expected: String) {
        then(VaultReferencesUtil.getNamespace(string)).isEqualTo(expected)
    }
}