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

import org.assertj.core.api.BDDAssertions.then
import org.junit.Test
import java.util.*

class VaultReferencesUtilTest {
    @Test
    fun testSimpleReference() {
        val prefixes = Arrays.asList("vault");
        val map = mapOf("a" to "%vault:/test%")
        then(VaultReferencesUtil.hasReferences(map,prefixes)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, prefixes, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/test")
    }

    @Test
    fun testManyReferencesInOneParameter() {
        val prefixes = Arrays.asList("vault");
        val map = mapOf("a" to "%vault:/testA% %vault:/test B%")
        then(VaultReferencesUtil.hasReferences(map,prefixes)).isTrue()
        val keys = HashSet<String>()
        val refs = HashSet<String>()
        VaultReferencesUtil.collect(map, refs, prefixes, keys)
        then(keys).containsOnly(map.keys.first())
        then(refs).containsOnly("vault:/testA", "vault:/test B")
    }
}