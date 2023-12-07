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
package org.jetbrains.teamcity.vault.server;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.NullBuildProgressLogger;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.teamcity.vault.retrier.Retrier;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.vault.*;
import org.jetbrains.teamcity.vault.support.LifecycleAwareSessionManager;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.jetbrains.teamcity.vault.UtilKt.createClientHttpRequestFactory;

public class VaultConnectorTest {
  public static final VaultDevContainer vault = new VaultDevContainer();
  private static final SSLTrustStoreProvider SSL_TRUST_STORE_PROVIDER = () -> null;

  @BeforeClass
  public void startContainer() {
    vault.start();
  }

  @AfterClass
  public void endContainer() {
    vault.stop();
  }

  @NotNull
  protected VaultDevEnvironment getVault() {
    return vault;
  }

  @DataProvider(name = "namespaces")
  public static Object[][] data() {
    return new Object[][]{{"ns1"}, {""}, {"ns2"}};
  }

  @Test
  public void testVaultIsUpAndRunning() {
    final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
    // Do not use namespace for system endpoints
    final VaultTemplate template = VaultTestUtil.createNamespaceAndTemplate(getVault(), factory, "");

    final VaultHealth health = template.opsForSys().health();
    then(health.isInitialized()).isTrue();
    then(health.isSealed()).isFalse();
    then(health.isStandby()).isFalse();
    then(health.getVersion()).isEqualTo(vault.getVersion() + "+ent");
  }

  @Test(dataProvider = "namespaces")
  public void testWrappedTokenCreated(String vaultNamespace) {
    doTestWrapperTokenCreated("approle", vaultNamespace);
  }

  @Test(dataProvider = "namespaces")
  public void testWrappedTokenCreatedNonStandardAuthPath(String vaultNamespace) {
    doTestWrapperTokenCreated("teamcity/auth", vaultNamespace);
  }

  private void doTestWrapperTokenCreated(String authMountPath, String vaultNamespace) {
    final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
    final VaultTemplate template = VaultTestUtil.createNamespaceAndTemplate(getVault(), factory, vaultNamespace);

    // Ensure approle auth enabled
    assertDefaultApproleExists(authMountPath, template);
    Pair<String, String> credentials = getAppRoleCredentials(template, "auth/" + authMountPath + "/role/testrole");


    final Pair<String, String> wrapped = VaultConnector.doRequestWrappedToken(
      new VaultFeatureSettings("vault", getVault().getUrl(), vaultNamespace, authMountPath, credentials.getFirst(), credentials.getSecond(), false), SSL_TRUST_STORE_PROVIDER);

    then(wrapped.getFirst()).isNotNull();
    then(wrapped.getSecond()).isNotNull();


    final CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
                                                                                 .wrapped()
                                                                                 .initialToken(VaultToken.of(wrapped.getFirst()))
                                                                                 .build();
    final RestTemplate simpleTemplate =
      UtilKt.createRestTemplate(new VaultFeatureSettings("vault", getVault().getUrl(), vaultNamespace, authMountPath, "", "", false), SSL_TRUST_STORE_PROVIDER);
    final CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, simpleTemplate);
    final TaskScheduler scheduler = new ConcurrentTaskScheduler();

    final MyLifecycleAwareSessionManager sessionManager =
      new MyLifecycleAwareSessionManager(authentication, scheduler, simpleTemplate, new LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(1L, TimeUnit.SECONDS),
                                         new NullBuildProgressLogger());

    then(sessionManager.getSessionToken()).isNotNull();

    sessionManager.renewToken();

    then(sessionManager.getSessionToken()).isNotNull();
  }

  @Test(dataProvider = "namespaces")
  public void testWrappedTokenAutoUpdates(String vaultNamespace) {
    doTestWrapperTokenAutoUpdates("approle-renew", vaultNamespace);
  }

  private void doTestWrapperTokenAutoUpdates(String authMountPath, String vaultNamespace) {
    final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
    VaultTestUtil.createNamespaceAndTemplate(getVault(), factory, vaultNamespace);
    final VaultTemplate template = new VaultTemplate(getVault().getEndpoint(), vaultNamespace, factory, () -> VaultToken.of(getVault().getToken()));

    // Ensure approle auth enabled
    template.opsForSys().authMount(authMountPath, VaultMount.create("approle"));
    //myAuthMounted.add(authMountPath);
    then(template.opsForSys().getAuthMounts()).containsKey(authMountPath + "/");
    Map<String, Object> config = new HashMap<>();
    config.put("secret_id_ttl", "10m");
    config.put("token_num_uses", "10");
    config.put("token_ttl", "5s"); // should be at least check_interval + 2 seconds
    config.put("token_max_ttl", "30m");
    config.put("secret_id_num_uses", "40");
    config.put("policies", new String[]{"default"});
    template.write("auth/" + authMountPath + "/role/testrole", config);
    Pair<String, String> credentials = getAppRoleCredentials(template, "auth/" + authMountPath + "/role/testrole");


    final Pair<String, String> wrapped =
      VaultConnector.doRequestWrappedToken(new VaultFeatureSettings("", getVault().getUrl(), vaultNamespace, authMountPath, credentials.getFirst(), credentials.getSecond(), true),
                                           SSL_TRUST_STORE_PROVIDER);

    then(wrapped.getFirst()).isNotNull();
    then(wrapped.getSecond()).isNotNull();


    final CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
                                                                                 .wrapped()
                                                                                 .initialToken(VaultToken.of(wrapped.getFirst()))
                                                                                 .build();
    final RestTemplate simpleTemplate =
      UtilKt.createRestTemplate(new VaultFeatureSettings("", getVault().getUrl(), vaultNamespace, authMountPath, "", "", true), SSL_TRUST_STORE_PROVIDER);
    final CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, simpleTemplate);
    final TaskScheduler scheduler = new ConcurrentTaskScheduler();

    final MyLifecycleAwareSessionManager sessionManager =
      new MyLifecycleAwareSessionManager(authentication, scheduler, simpleTemplate, new LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(1L, TimeUnit.SECONDS),
                                         new NullBuildProgressLogger());

    System.out.println("Once initialized = " + sessionManager.myRenewResults);
    then(sessionManager.getSessionToken()).isNotNull();
    System.out.println("After one check = " + sessionManager.myRenewResults);

    for (int i = 0; i < 5; i++) {
      ThreadUtil.sleep(1000L);
      System.out.println("After one second sleep = " + sessionManager.myRenewResults);
    }

    then(sessionManager.getSessionToken()).isNotNull();
    then(sessionManager.myRenewResults).isNotEmpty().doesNotContain(false);
  }

  @Test(dataProvider = "namespaces")
  public void testRevokeTokenTestConnection(String vaultNamespace) {
    final String authMountPath = "approle";
    final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
    final VaultTemplate template = VaultTestUtil.createNamespaceAndTemplate(getVault(), factory, vaultNamespace);

    // Ensure approle auth enabled
    assertDefaultApproleExists(authMountPath, template);
    Pair<String, String> credentials = getAppRoleCredentials(template, "auth/" + authMountPath + "/role/testrole");

    VaultFeatureSettings serverSettings =
      new VaultFeatureSettings("vault", getVault().getUrl(), vaultNamespace, authMountPath, credentials.getFirst(), credentials.getSecond(), false);

    Pair<String, String> pair = VaultConnector.doRequestToken(serverSettings, SSL_TRUST_STORE_PROVIDER);
    VaultConnector.revoke(new LeasedTokenInfo(pair.getFirst(), pair.getSecond(), serverSettings), SSL_TRUST_STORE_PROVIDER);
  }

  @Test(dataProvider = "namespaces")
  public void testTokenRevokedWhenAgentForgotToRevoke(String vaultNamespace) {
    doTestRevokeTokenAfterBuildFinish(false, vaultNamespace);
  }

  @Test(dataProvider = "namespaces")
  public void testNoErrorOnTokenRevocationWhenAgentAlreadyRevokedIt(String vaultNamespace) {
    doTestRevokeTokenAfterBuildFinish(true, vaultNamespace);
  }

  @Test
  public void testRetrierIsCalledFor500Error() throws JsonProcessingException {
    TeamCityProperties.getModel().storeDefaultValue(Retrier.RETRY_DELAY, "0");
    final RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
    final String path = "/path";
    final Map<String, String> body = Collections.emptyMap();

    final VaultResponse response = new VaultResponse();
    String key = "key1";
    String value = "value";
    response.setData(ImmutableMap.of(key, value));
    Mockito.when(restTemplate.postForObject(path, new ObjectMapper().writeValueAsString(body), VaultResponse.class))
           .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Mock error", HttpHeaders.EMPTY, "".getBytes(), Charset.defaultCharset()))
           .thenReturn(response);

    Pair<String, String> pair =
      VaultConnector.Companion.performLoginRequest(restTemplate, AuthMethod.APPROLE, path, body, "", vaultResponse -> new Pair<>(key, (String)vaultResponse.getData().get(key)));

    Assert.assertEquals(value, pair.getSecond());
  }

  private void doTestRevokeTokenAfterBuildFinish(Boolean revokeFromAgent, String vaultNamespace) {
    final String authMountPath = "approle";
    final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
    final VaultTemplate template = VaultTestUtil.createNamespaceAndTemplate(getVault(), factory, vaultNamespace);

    // Ensure approle auth enabled
    assertDefaultApproleExists(authMountPath, template);

    Pair<String, String> credentials = getAppRoleCredentials(template, "auth/" + authMountPath + "/role/testrole");

    VaultFeatureSettings serverSettings =
      new VaultFeatureSettings("vault", getVault().getUrl(), vaultNamespace, authMountPath, credentials.getFirst(), credentials.getSecond(), false);

    final Pair<String, String> wrapped = VaultConnector.doRequestWrappedToken(serverSettings, SSL_TRUST_STORE_PROVIDER);

    String wrappedToken = wrapped.getFirst();
    String accessor = wrapped.getSecond();

    then(wrappedToken).isNotNull();
    then(accessor).isNotNull();

    final VaultFeatureSettings agentSettings = new VaultFeatureSettings("vault", getVault().getUrl(), vaultNamespace, authMountPath, "", "", false);

    final CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
                                                                                 .wrapped()
                                                                                 .initialToken(VaultToken.of(wrappedToken))
                                                                                 .build();
    final RestTemplate simpleTemplate = UtilKt.createRestTemplate(agentSettings, SSL_TRUST_STORE_PROVIDER);
    final CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, simpleTemplate);
    final TaskScheduler scheduler = new ConcurrentTaskScheduler();

    final MyLifecycleAwareSessionManager sessionManager =
      new MyLifecycleAwareSessionManager(authentication, scheduler, simpleTemplate, new LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(1L, TimeUnit.SECONDS),
                                         new NullBuildProgressLogger());
    then(sessionManager.getSessionToken()).isNotNull();
    // Check token is OK
    sessionManager.renewToken();
    if (revokeFromAgent) {
      // Revoke token
      sessionManager.destroy();
    }

    // Server side revoke via
    VaultConnector.revoke(new LeasedWrappedTokenInfo(wrappedToken, accessor, serverSettings), SSL_TRUST_STORE_PROVIDER, false);
  }

  private void assertDefaultApproleExists(String authMountPath, VaultTemplate template) {
    if (template.opsForSys().getAuthMounts().containsKey(authMountPath + "/")) {
      return;
    }
    template.opsForSys().authMount(authMountPath, VaultMount.create("approle"));
    then(template.opsForSys().getAuthMounts()).containsKey(authMountPath + "/");
    template.write("auth/" + authMountPath + "/role/testrole", CollectionsUtil.asMap(
      "secret_id_ttl", "10m",
      "token_num_uses", "10",
      "token_ttl", "20m",
      "token_max_ttl", "30m",
      "secret_id_num_uses", "40"
    ));
  }

  private Pair<String, String> getAppRoleCredentials(VaultTemplate template, String path) {
    final String roleId = (String)template.read(path + "/role-id").getData().get("role_id");
    final String secretId = (String)template.write(path + "/secret-id", null).getData().get("secret_id");
    return new Pair<>(roleId, secretId);
  }

  private static class MyLifecycleAwareSessionManager extends LifecycleAwareSessionManager {
    List<Boolean> myRenewResults = new ArrayList<>();

    private MyLifecycleAwareSessionManager(@NotNull ClientAuthentication clientAuthentication,
                                           @NotNull TaskScheduler taskScheduler,
                                           @NotNull RestOperations restOperations,
                                           @NotNull LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger refreshTrigger,
                                           @NotNull BuildProgressLogger logger) {
      super(clientAuthentication, taskScheduler, restOperations, refreshTrigger, logger);
    }

    @Override
    public boolean renewToken() {
      final boolean result = super.renewToken();
      myRenewResults.add(result);
      return result;
    }
  }
}
