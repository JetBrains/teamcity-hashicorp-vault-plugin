package org.jetbrains.teamcity.vault.agent;


import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.teamcity.vault.VaultDevContainer;
import org.jetbrains.teamcity.vault.VaultFeatureSettings;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;

public class VaultParametersResolverTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();

    private VaultParametersResolver resolver;
    private VaultFeatureSettings feature;
    private VaultTemplate template;

    @Before
    public void setUp() throws Exception {
        final ClientHttpRequestFactory factory = ClientHttpRequestFactoryFactory.create(new ClientOptions(), SslConfiguration.NONE);
        template = new VaultTemplate(vault.getEndpoint(), factory, vault.getSimpleSessionManager());
        resolver = new VaultParametersResolver();
        feature = new VaultFeatureSettings(vault.getUrl(), false, "", "");
    }

    @Test
    public void testSimpleParameterResolvedFromVault() throws Exception {
        final String path = "secret/test";
        template.write(path, Collections.singletonMap("value", "TestValue"));
        {
            // Check secret created
            final VaultResponse response = template.read(path);
            then(response.getData()).contains(entry("value", "TestValue"));
        }

        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), Collections.singletonList(new VaultParameter("/" + path, null)));
        then(replacements).hasSize(1).contains(entry("/" + path, "TestValue"));
    }

    @Test
    public void testSingleValueParameterResolvedFromVault() throws Exception {
        final String path = "secret/test";
        template.write(path, Collections.singletonMap("data", "TestValue"));

        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), Collections.singletonList(new VaultParameter("/" + path, null)));
        then(replacements).hasSize(1).contains(entry("/" + path, "TestValue"));
    }

    @Test
    public void testComplexValueParameterResolvedFromVault() throws Exception {
        final String path = "secret/test-complex";
        template.write(path, CollectionsUtil.asMap("first", "TestValueA", "second", "TestValueB"));

        final List<VaultParameter> parameters = Arrays.asList(
                new VaultParameter("/" + path, "first"),
                new VaultParameter("/" + path, "second")
        );

        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), parameters);
        then(replacements).hasSize(2).contains(entry("/" + path + "!/first", "TestValueA"), entry("/" + path + "!/second", "TestValueB"));
    }
}
