package org.jetbrains.teamcity.vault.server;

import org.jetbrains.teamcity.vault.VaultDevContainer;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultToken;

import static org.assertj.core.api.BDDAssertions.then;

public class VaultContainerTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();

    @Test
    public void testVaultIsUpAndRunning() throws Exception {
        final ClientHttpRequestFactory factory = ClientHttpRequestFactoryFactory.create(new ClientOptions(), SslConfiguration.NONE);

        final VaultTemplate template = new VaultTemplate(vault.getEndpoint(), factory, () -> VaultToken.of(vault.getToken()));

        final VaultHealth health = template.opsForSys().health();
        then(health.isInitialized()).isTrue();
        then(health.isSealed()).isFalse();
        then(health.isStandby()).isFalse();
        then(health.getVersion()).isEqualTo("0.7.3");
    }
}
