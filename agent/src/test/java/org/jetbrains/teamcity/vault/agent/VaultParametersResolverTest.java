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
package org.jetbrains.teamcity.vault.agent;


import jetbrains.buildServer.agent.NullBuildProgressLogger;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.VersionComparatorUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
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
import org.springframework.vault.support.VaultResponse;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;
import static org.jetbrains.teamcity.vault.UtilKt.createClientHttpRequestFactory;

public class VaultParametersResolverTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();

    private VaultParametersResolver resolver;
    private VaultFeatureSettings feature;
    private VaultTemplate template;
    private final List<String> myRequestedURIs = new ArrayList<String>();

    @Before
    public void setUp() throws Exception {
        SSLTrustStoreProvider emptyTrustStoreProvider = new EmtpySSLTrustStoreProvider();
        ClientHttpRequestFactory factory = new AbstractClientHttpRequestFactoryWrapper(createClientHttpRequestFactory(emptyTrustStoreProvider)) {
            @Override
            protected ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) throws IOException {
                myRequestedURIs.add(uri.getPath());
                return requestFactory.createRequest(uri, httpMethod);
            }
        };
        template = vault.getTemplate(factory);
        resolver = new VaultParametersResolver(emptyTrustStoreProvider);
        feature = new VaultFeatureSettings(vault.getUrl(), "", "", 0L, 1);
    }

    @Test
    public void testSimpleParameterResolvedFromVault() throws Exception {
        writeSecret("test", Collections.singletonMap("value", "TestValue"));
        {
            // Check secret created
            Map<String, Object> data = readSecret("test");
            then(data).contains(entry("value", "TestValue"));
        }


        final String path = getKVPath("test");

        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), Collections.singletonList(new VaultParameter("/" + path, null)), new NullBuildProgressLogger()).getReplacements();
        then(replacements).hasSize(1).contains(entry("/" + path, "TestValue"));
    }

    @Test
    public void testSingleValueParameterResolvedFromVault() throws Exception {
        final String path = getKVPath("test");
        writeSecret(path, Collections.singletonMap("data", "TestValue"));

        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), Collections.singletonList(new VaultParameter("/" + path, null)), new NullBuildProgressLogger()).getReplacements();
        then(replacements).hasSize(1).contains(entry("/" + path, "TestValue"));
    }

    @Test
    public void testComplexValueParameterResolvedFromVault() throws Exception {
        final String path = getKVPath("test-complex");
        writeSecret(path, CollectionsUtil.asMap("first", "TestValueA", "second", "TestValueB"));
        then(readSecret(path)).contains(entry("first", "TestValueA"));

        final List<VaultParameter> parameters = Arrays.asList(
                new VaultParameter("/" + path, "first"),
                new VaultParameter("/" + path, "second")
        );

        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), parameters, new NullBuildProgressLogger()).getReplacements();
        then(replacements).hasSize(2).contains(entry("/" + path + "!/first", "TestValueA"), entry("/" + path + "!/second", "TestValueB"));
    }

    @Test
    public void testComplexValueParameterCallVaultAPIOnlyOnce() throws Exception {
        final String path = getKVPath("test-read-once");
        writeSecret(path, CollectionsUtil.asMap("first", "TestValueA", "second", "TestValueB"));

        final List<VaultParameter> parameters = Arrays.asList(
                VaultParameter.extract("/" + path + "!/first"),
                VaultParameter.extract("/" + path + "!/second")
        );

        myRequestedURIs.clear();
        final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(template, parameters, new NullBuildProgressLogger()).getReplacements();
        then(replacements).hasSize(2).contains(entry("/" + path + "!/first", "TestValueA"), entry("/" + path + "!/second", "TestValueB"));

        then(myRequestedURIs).hasSize(1).containsOnlyOnce("/v1/" + path);
    }

    private boolean isKV2() {
        return VersionComparatorUtil.compare("0.10", vault.getVersion()) <= 0;
    }

    private String getKVPath(String path) {
        return isKV2() ? "secret/data/" + path : "secret/" + path;
    }

    private VaultResponse writeSecret(String path, Object payload) {
        if (isKV2()) {
            String prefix = "secret/data/";
            if (!path.startsWith(prefix)) {
                //noinspection AssignmentToMethodParameter
                path = prefix + path;
            }
            return template.write(path, Collections.singletonMap("data", payload));
        } else {
            String prefix = "secret/";
            if (!path.startsWith(prefix)) {
                //noinspection AssignmentToMethodParameter
                path = prefix + path;
            }
            return template.write(path, payload);
        }
    }

    private Map<String, Object> readSecret(String path) {
        if (isKV2()) {
            String prefix = "secret/data/";
            if (!path.startsWith(prefix)) {
                //noinspection AssignmentToMethodParameter
                path = prefix + path;
            }
            VaultResponse response = template.read(path);
            //noinspection unchecked
            return (Map<String, Object>) response.getData().get("data");
        } else {
            String prefix = "secret/";
            if (!path.startsWith(prefix)) {
                //noinspection AssignmentToMethodParameter
                path = prefix + path;
            }
            VaultResponse response = template.read(path);
            return response.getData();
        }
    }

}
