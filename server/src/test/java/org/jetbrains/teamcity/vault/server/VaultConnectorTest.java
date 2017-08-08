package org.jetbrains.teamcity.vault.server;

import jetbrains.buildServer.util.CollectionsUtil;
import kotlin.Pair;
import org.jetbrains.teamcity.vault.VaultDevContainer;
import org.jetbrains.teamcity.vault.VaultFeatureSettings;
import org.jetbrains.teamcity.vault.support.VaultTemplate;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.config.ClientHttpRequestFactoryFactory;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultToken;

import static org.assertj.core.api.BDDAssertions.then;

public class VaultConnectorTest {
    @ClassRule
    public static final VaultDevContainer vault = new VaultDevContainer();

    @Test
    public void testWrappedTokenCreated() throws Exception {
        final ClientHttpRequestFactory factory = ClientHttpRequestFactoryFactory.create(new ClientOptions(), SslConfiguration.NONE);
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


        final Pair<String, String> wrapped = VaultConnector.doRequestWrappedToken(new VaultFeatureSettings(vault.getUrl(), true, credentials.getFirst(), credentials.getSecond()));

        then(wrapped.getFirst()).isNotNull();
        then(wrapped.getSecond()).isNotNull();
    }

    private Pair<String, String> getAppRoleCredentials(VaultTemplate template, String path) {
        final String roleId = (String) template.read(path + "/role-id").getData().get("role_id");
        final String secretId = (String) template.write(path + "/secret-id", null).getData().get("secret_id");
        return new Pair<>(roleId, secretId);
    }
}
