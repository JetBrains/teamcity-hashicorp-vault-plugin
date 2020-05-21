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

import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultToken
import java.net.URI


interface VaultDevEnvironment {
    val token: String
    val url: String
    val endpoint: VaultEndpoint
        get() = VaultEndpoint.from(URI.create(url))
    val simpleSessionManager: SimpleSessionManager
        get() = SimpleSessionManager { VaultToken.of(token) }
}

