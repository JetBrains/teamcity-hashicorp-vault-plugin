package org.jetbrains.teamcity.vault

import org.assertj.core.api.BDDAssertions.then
import org.junit.Test

class VaultReferencesUtilTest {
    @Test
    fun testSimpleReference() {
        val map = mapOf("a" to "%vault:/test%")
        then(VaultReferencesUtil.hasReferences(map)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/test")
    }

    @Test
    fun testManyReferencesInOneParameter() {
        val map = mapOf("a" to "%vault:/testA% %vault:/test B%")
        then(VaultReferencesUtil.hasReferences(map)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/testA", "vault:/test B")
    }

    @Test
    fun testResolving() {
        then(VaultReferencesUtil.resolve("%vault:/test%", mapOf())).isNull()
        then(VaultReferencesUtil.resolve("%vault:/test%", mapOf("/test" to "val"))).isEqualTo("val")
        then(VaultReferencesUtil.resolve("%vault:/A% %vault:/ B%", mapOf("/A" to "AAA", "/ B" to "BBB"))).isEqualTo("AAA BBB")

        then(VaultReferencesUtil.resolve("%vault:test% %vault:/test%", mapOf("/test" to "val"))).isEqualTo("val val")
    }
}