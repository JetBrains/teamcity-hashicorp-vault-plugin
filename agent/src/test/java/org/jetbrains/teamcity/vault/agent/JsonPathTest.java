
package org.jetbrains.teamcity.vault.agent;

import com.jayway.jsonpath.JsonPath;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

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