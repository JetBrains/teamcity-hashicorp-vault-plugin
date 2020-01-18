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
package org.jetbrains.teamcity.vault.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.vault.VaultDevEnvironment;
import org.jetbrains.teamcity.vault.VaultSemiClusterDevContainer;
import org.junit.After;
import org.junit.ClassRule;

import static org.assertj.core.api.BDDAssertions.then;

public class VaultClusterConnectorTest extends VaultConnectorTest {
    @ClassRule
    public static final VaultSemiClusterDevContainer jetty = new VaultSemiClusterDevContainer(VaultConnectorTest.vault);

    @NotNull
    @Override
    protected VaultDevEnvironment getVault() {
        return jetty;
    }

    @After
    public void tearDown() throws Exception {
        then(jetty.getUsed()).overridingErrorMessage("Jetty redirector should be used in tests").isTrue();
    }
}
