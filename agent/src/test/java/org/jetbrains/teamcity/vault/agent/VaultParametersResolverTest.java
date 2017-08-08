package org.jetbrains.teamcity.vault.agent;


import jetbrains.buildServer.util.CollectionsUtil;
import org.jetbrains.teamcity.vault.VaultDevContainer;
import org.jetbrains.teamcity.vault.VaultFeatureSettings;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultResponse;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;

public class VaultParametersResolverTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();

    private VaultParametersResolver resolver;
    private VaultFeatureSettings feature;
    private VaultTemplate template;
    private final List<String> myRequestedURIs = new ArrayList<String>();

    @Before
    public void setUp() throws Exception {
        ClientHttpRequestFactory factory = new AbstractClientHttpRequestFactoryWrapper(ClientHttpRequestFactoryFactory.create(new ClientOptions(), SslConfiguration.NONE)) {
            @Override
            protected ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) throws IOException {
                myRequestedURIs.add(uri.getPath());
                return requestFactory.createRequest(uri, httpMethod);
            }
        };
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

    @Test
    public void testComplexValueParameterCallVaultAPIOnlyOnce() throws Exception {
        final String path = "secret/test-read-once";
        template.write(path, CollectionsUtil.asMap("first", "TestValueA", "second", "TestValueB"));

        final List<VaultParameter> parameters = Arrays.asList(
                VaultParameter.extract("/" + path + "!/first"),
                VaultParameter.extract("/" + path + "!/second")
        );

        myRequestedURIs.clear();
        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(template, parameters);
        then(replacements).hasSize(2).contains(entry("/" + path + "!/first", "TestValueA"), entry("/" + path + "!/second", "TestValueB"));

        then(myRequestedURIs).hasSize(1).containsOnlyOnce("/v1/" + path);
    }
}
