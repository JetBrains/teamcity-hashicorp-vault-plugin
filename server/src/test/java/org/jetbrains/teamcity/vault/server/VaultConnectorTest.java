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

import jetbrains.buildServer.util.CollectionsUtil;
import kotlin.Pair;
import org.jetbrains.teamcity.vault.UtilKt;
import org.jetbrains.teamcity.vault.VaultDevContainer;
import org.jetbrains.teamcity.vault.VaultFeatureSettings;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.jetbrains.teamcity.vault.UtilKt.createClientHttpRequestFactory;

public class VaultConnectorTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();

    @Test
    public void testVaultIsUpAndRunning() throws Exception {
        final ClientHttpRequestFactory factory = createClientHttpRequestFactory();

        final VaultTemplate template = new VaultTemplate(vault.getEndpoint(), factory, () -> VaultToken.of(vault.getToken()));

        final VaultHealth health = template.opsForSys().health();
        then(health.isInitialized()).isTrue();
        then(health.isSealed()).isFalse();
        then(health.isStandby()).isFalse();
        then(health.getVersion()).isEqualTo("0.7.3");
    }

    @Test
    public void testWrappedTokenCreated() throws Exception {
        final ClientHttpRequestFactory factory = createClientHttpRequestFactory();
        final VaultTemplate template = new VaultTemplate(vault.getEndpoint(), factory, () -> VaultToken.of(vault.getToken()));

        // Ensure approle auth enabled
        template.opsForSys().authMount("approle", VaultMount.create("approle"));
        then(template.opsForSys().getAuthMounts()).containsKey("approle/");
        template.write("auth/approle/role/testrole", CollectionsUtil.asMap(
                "secret_id_ttl", "10m",
                "token_num_uses", "10",
                "token_ttl", "20m",
                "token_max_ttl", "30m",
                "secret_id_num_uses", "40"
        ));
        Pair<String, String> credentials = getAppRoleCredentials(template, "auth/approle/role/testrole");


        final Pair<String, String> wrapped = VaultConnector.doRequestWrappedToken(new VaultFeatureSettings(vault.getUrl(), credentials.getFirst(), credentials.getSecond()));

        then(wrapped.getFirst()).isNotNull();
        then(wrapped.getSecond()).isNotNull();


        final CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder()
                .wrapped()
                .initialToken(VaultToken.of(wrapped.getFirst()))
                .build();
        final RestTemplate simpleTemplate = UtilKt.createRestTemplate(new VaultFeatureSettings(vault.getUrl(), "", ""));
        final CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, simpleTemplate);
        final TaskScheduler scheduler = new ConcurrentTaskScheduler();

        final MyLifecycleAwareSessionManager sessionManager = new MyLifecycleAwareSessionManager(authentication, simpleTemplate, scheduler);

        then(sessionManager.getSessionToken()).isNotNull();

        sessionManager.renewToken();

        then(sessionManager.getSessionToken()).isNotNull();
    }

    private Pair<String, String> getAppRoleCredentials(VaultTemplate template, String path) {
        final String roleId = (String) template.read(path + "/role-id").getData().get("role_id");
        final String secretId = (String) template.write(path + "/secret-id", null).getData().get("secret_id");
        return new Pair<>(roleId, secretId);
    }

    private static class MyLifecycleAwareSessionManager extends LifecycleAwareSessionManager {
        private MyLifecycleAwareSessionManager(CubbyholeAuthentication authentication, RestTemplate simpleTemplate, TaskScheduler scheduler) {
            super(authentication, scheduler, simpleTemplate);
        }

        @Override
        public boolean renewToken() {
            return super.renewToken();
        }
    }
}
