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
package org.jetbrains.teamcity.vault.agent

import org.jetbrains.teamcity.vault.ensureHasPrefix

data class VaultParameter constructor(val vaultPath: String, val jsonPath: String? = null) {
    companion object {
        @JvmStatic fun extract(path: String): VaultParameter {
            val split = path.split("!/", limit = 2)
            if (split.size == 1) {
                return VaultParameter(split[0], null)
            }
            return VaultParameter(split[0], split[1])
        }
    }

    val full: String get() = (if (jsonPath == null) vaultPath else vaultPath + "!/" + jsonPath).ensureHasPrefix("/")
}