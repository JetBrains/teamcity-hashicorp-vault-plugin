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
package org.jetbrains.teamcity.vault.server;

import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.NullBuildProgressLogger;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.vault.UtilKt;
import org.jetbrains.teamcity.vault.VaultDevContainer;
import org.jetbrains.teamcity.vault.VaultDevEnvironment;
import org.jetbrains.teamcity.vault.VaultFeatureSettings;
import org.jetbrains.teamcity.vault.support.LifecycleAwareSessionManager;
import org.jetbrains.teamcity.vault.support.RetryRestTemplate;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;
import static org.jetbrains.teamcity.vault.UtilKt.createClientHttpRequestFactory;

public class VaultConnectorTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();
    private static final SSLTrustStoreProvider SSL_TRUST_STORE_PROVIDER = () -> null;

    @NotNull
    protected VaultDevEnvironment getVault() {
        return vault;
    }

    @Test
    public void testVaultIsUpAndRunning() throws Exception {
        final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);

        final VaultTemplate template = getVault().getTemplate(factory);

        final VaultHealth health = template.opsForSys().health();
        then(health.isInitialized()).isTrue();
        then(health.isSealed()).isFalse();
        then(health.isStandby()).isFalse();
        then(health.getVersion()).isEqualTo(vault.getVersion());
    }

    @Test
    public void testWrappedTokenCreated() throws Exception {
        doTestWrapperTokenCreated("approle", 0L, 1);
    }

    @Test
    public void testWrappedTokenCreatedNonStandardAuthPath() throws Exception {
        doTestWrapperTokenCreated("teamcity/auth", 0L, 1);
    }

    private void doTestWrapperTokenCreated(String authMountPath, long backoffPeriod, int maxAttempts) {
        final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
        final VaultTemplate template = getVault().getTemplate(factory);

        // Ensure approle auth enabled
        template.opsForSys().authMount(authMountPath, VaultMount.create("approle"));
        then(template.opsForSys().getAuthMounts()).containsKey(authMountPath + "/");
        template.write("auth/" + authMountPath + "/role/testrole", CollectionsUtil.asMap(
                "secret_id_ttl", "10m",
                "token_num_uses", "10",
                "token_ttl", "20m",
                "token_max_ttl", "30m",
                "secret_id_num_uses", "40"
        ));
        Pair<String, String> credentials = getAppRoleCredentials(template, "auth/" + authMountPath + "/role/testrole");


        final Pair<String, String> wrapped = VaultConnector.doRequestWrappedToken(new VaultFeatureSettings("vault", getVault().getUrl(), authMountPath, credentials.getFirst(), credentials.getSecond(), 0L, 1, false), SSL_TRUST_STORE_PROVIDER);

        then(wrapped.getFirst()).isNotNull();
        then(wrapped.getSecond()).isNotNull();


        final CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
                .wrapped()
                .initialToken(VaultToken.of(wrapped.getFirst()))
                .build();
        final RetryRestTemplate simpleTemplate = UtilKt.createRetryRestTemplate(new VaultFeatureSettings("vault", getVault().getUrl(), authMountPath, "", "", 0L, 1, false), SSL_TRUST_STORE_PROVIDER);
        final CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, simpleTemplate);
        final TaskScheduler scheduler = new ConcurrentTaskScheduler();

        final MyLifecycleAwareSessionManager sessionManager = new MyLifecycleAwareSessionManager(authentication, scheduler, simpleTemplate, new LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(1L, TimeUnit.SECONDS), new NullBuildProgressLogger());

        then(sessionManager.getSessionToken()).isNotNull();

        sessionManager.renewToken();

        then(sessionManager.getSessionToken()).isNotNull();
    }

    @Test
    public void testWrappedTokenAutoUpdates() {
        doTestWrapperTokenAutoUpdates("approle-renew");
    }

    private void doTestWrapperTokenAutoUpdates(String authMountPath) {
        final ClientHttpRequestFactory factory = createClientHttpRequestFactory(SSL_TRUST_STORE_PROVIDER);
        final VaultTemplate template = new VaultTemplate(getVault().getEndpoint(), factory, () -> VaultToken.of(getVault().getToken()));

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


        final Pair<String, String> wrapped = VaultConnector.doRequestWrappedToken(new VaultFeatureSettings("", getVault().getUrl(), authMountPath, credentials.getFirst(), credentials.getSecond(), 0L, 1, true), SSL_TRUST_STORE_PROVIDER);

        then(wrapped.getFirst()).isNotNull();
        then(wrapped.getSecond()).isNotNull();


        final CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
                .wrapped()
                .initialToken(VaultToken.of(wrapped.getFirst()))
                .build();
        final RetryRestTemplate simpleTemplate = UtilKt.createRetryRestTemplate(new VaultFeatureSettings("", getVault().getUrl(), authMountPath, "", "", 0L, 1, true), SSL_TRUST_STORE_PROVIDER);
        final CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, simpleTemplate);
        final TaskScheduler scheduler = new ConcurrentTaskScheduler();

        final MyLifecycleAwareSessionManager sessionManager = new MyLifecycleAwareSessionManager(authentication, scheduler, simpleTemplate, new LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(1L, TimeUnit.SECONDS), new NullBuildProgressLogger());

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

    private Pair<String, String> getAppRoleCredentials(VaultTemplate template, String path) {
        final String roleId = (String) template.read(path + "/role-id").getData().get("role_id");
        final String secretId = (String) template.write(path + "/secret-id", null).getData().get("secret_id");
        return new Pair<>(roleId, secretId);
    }

    private static class MyLifecycleAwareSessionManager extends LifecycleAwareSessionManager {
        List<Boolean> myRenewResults = new ArrayList<>();

        public MyLifecycleAwareSessionManager(@NotNull ClientAuthentication clientAuthentication, @NotNull TaskScheduler taskScheduler, @NotNull RestOperations restOperations, @NotNull LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger refreshTrigger, @NotNull BuildProgressLogger logger) {
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
