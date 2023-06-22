/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.agent;


import java.io.IOException;
import java.net.URI;
import java.util.*;
import jetbrains.buildServer.agent.AgentRunningBuildEx;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.Constants;
import jetbrains.buildServer.agent.NullBuildProgressLogger;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.PasswordReplacer;
import jetbrains.buildServer.util.VersionComparatorUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.teamcity.vault.*;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.support.VaultResponse;
import org.testng.annotations.*;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;
import static org.jetbrains.teamcity.vault.UtilKt.createClientHttpRequestFactory;

public class VaultParametersResolverTest {
  public static final VaultDevContainer vault = new VaultDevContainer();

  private VaultParametersResolver resolver;
  private VaultFeatureSettings feature;
  private VaultTemplate template;
  private final List<String> myRequestedURIs = new ArrayList<String>();

  @DataProvider(name = "namespaces")
  public static Object[][] data() {
    return new Object[][]{{"ns1"}, {""}, {"ns2"}};
  }

  @BeforeClass
  public void startContainer() {
    vault.start();
  }

  @AfterClass
  public void endContainer() {
    vault.stop();
  }

  @BeforeMethod
  public void setUp(Object[] args) {
    String vaultNamespace = (String)args[0];
    SSLTrustStoreProvider emptyTrustStoreProvider = new EmtpySSLTrustStoreProvider();
    ClientHttpRequestFactory factory = new AbstractClientHttpRequestFactoryWrapper(createClientHttpRequestFactory(emptyTrustStoreProvider)) {
      @Override
      protected ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) throws IOException {
        myRequestedURIs.add(uri.getPath());
        return requestFactory.createRequest(uri, httpMethod);
      }
    };
    template = VaultTestUtil.createNamespaceAndTemplate(vault, factory, vaultNamespace);
    resolver = new VaultParametersResolver(emptyTrustStoreProvider);
    feature = new VaultFeatureSettings(vault.getUrl(), vaultNamespace);
  }
  private static final String EXPECTED_VALUE = "TestValue";

  @Test(dataProvider = "namespaces")
  public void testSimpleParameterResolvedFromRemoteParameter(String namespace) {
    String key = "key";
    AgentRunningBuildEx runningBuild = testParameterWithKey(key);
    Mockito.verify(runningBuild).addSharedConfigParameter(key, EXPECTED_VALUE);
  }

  @Test(dataProvider = "namespaces")
  public void testSimpleSystemParameterResolvedFromRemoteParameter(String namespace) {
    String key = Constants.SYSTEM_PREFIX +  "key";
    AgentRunningBuildEx runningBuild = testParameterWithKey(key);
    Mockito.verify(runningBuild).addSharedSystemProperty("key", EXPECTED_VALUE);
  }

  @Test(dataProvider = "namespaces")
  public void testSimpleEnvParameterResolvedFromRemoteParameter(String namespace) {
    String key = Constants.ENV_PREFIX +  "key";
    AgentRunningBuildEx runningBuild = testParameterWithKey(key);
    Mockito.verify(runningBuild).addSharedEnvironmentVariable("key", EXPECTED_VALUE);
  }

  private AgentRunningBuildEx testParameterWithKey(String key) {
    final String path = getKVPath("test");
    writeSecret(path, Collections.singletonMap("data", EXPECTED_VALUE));

    final AgentRunningBuildEx runningBuild = Mockito.mock(AgentRunningBuildEx.class);
    final BuildProgressLogger logger = Mockito.mock(BuildProgressLogger.class);
    final PasswordReplacer passwordReplacer = Mockito.mock(PasswordReplacer.class);

    Mockito.when(runningBuild.getBuildLogger()).thenReturn(logger);
    Mockito.when(runningBuild.getPasswordReplacer()).thenReturn(passwordReplacer);
    final VaultParameter parameter = new VaultParameter(key, new VaultParameterSettings(VaultConstants.ParameterSettings.DEFAULT_UI_PARAMETER_NAMESPACE, path));

    resolver.resolve(runningBuild, feature, Collections.singletonList(parameter), vault.getToken());
    Mockito.verify(passwordReplacer).addPassword(EXPECTED_VALUE);
    return runningBuild;
  }

  @Test(dataProvider = "namespaces")
  public void testSimpleParameterResolvedFromVault(String namespace) {
    writeSecret("test", Collections.singletonMap("value", "TestValue"));
    {
      // Check secret created
      Map<String, Object> data = readSecret("test");
      then(data).contains(entry("value", "TestValue"));
    }


    final String path = getKVPath("test");

    final Map<String, String> replacements =
      resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), Collections.singletonList(new VaultQuery("/" + path, null)), new NullBuildProgressLogger())
              .getReplacements();
    then(replacements).hasSize(1).contains(entry("/" + path, "TestValue"));
  }

  @Test(dataProvider = "namespaces")
  public void testSingleValueParameterResolvedFromVault(String namespace) {
    final String path = getKVPath("test");
    writeSecret(path, Collections.singletonMap("data", "TestValue"));

    final Map<String, String> replacements =
      resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), Collections.singletonList(new VaultQuery("/" + path, null)), new NullBuildProgressLogger())
              .getReplacements();
    then(replacements).hasSize(1).contains(entry("/" + path, "TestValue"));
  }

  @Test(dataProvider = "namespaces")
  public void testComplexValueParameterResolvedFromVault(String namespace) {
    final String path = getKVPath("test-complex");
    writeSecret(path, CollectionsUtil.asMap("first", "TestValueA", "second", "TestValueB"));
    then(readSecret(path)).contains(entry("first", "TestValueA"));

    final List<VaultQuery> parameters = Arrays.asList(
      new VaultQuery("/" + path, "first"),
      new VaultQuery("/" + path, "second")
    );

    final Map<String, String> replacements = resolver.doFetchAndPrepareReplacements(feature, vault.getToken(), parameters, new NullBuildProgressLogger()).getReplacements();
    then(replacements).hasSize(2).contains(entry("/" + path + "!/first", "TestValueA"), entry("/" + path + "!/second", "TestValueB"));
  }

  @Test(dataProvider = "namespaces")
  public void testComplexValueParameterCallVaultAPIOnlyOnce(String namespace) {
    final String path = getKVPath("test-read-once");
    writeSecret(path, CollectionsUtil.asMap("first", "TestValueA", "second", "TestValueB"));

    final List<VaultQuery> parameters = Arrays.asList(
      VaultQuery.extract("/" + path + "!/first"),
      VaultQuery.extract("/" + path + "!/second")
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
      return (Map<String, Object>)response.getData().get("data");
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
