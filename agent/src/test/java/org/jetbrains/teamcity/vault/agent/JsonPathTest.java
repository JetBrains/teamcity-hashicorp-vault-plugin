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
package org.jetbrains.teamcity.vault.agent;

import com.jayway.jsonpath.JsonPath;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.BDDAssertions.then;

public class JsonPathTest {
    @Test
    public void EnsureSimplePatternCompiles() throws Exception {
        final JsonPath pattern = JsonPath.compile("$.test");
        final Boolean read = pattern.<Boolean>read("{\"test\":true}");
        then(read).isTrue();

        // Investigate classpath problems (CNFE) in runtime
        LoggerFactory.getLogger(JsonPathTest.class);
        then(Class.forName("com.jayway.jsonpath.internal.path.CompiledPath")).isNotNull();
    }
}
