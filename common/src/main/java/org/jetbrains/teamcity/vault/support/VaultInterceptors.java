
package org.jetbrains.teamcity.vault.support;

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class VaultInterceptors {
    public static final String VAULT_NAMESPACE_HEADER = "X-Vault-Namespace";

    @Nullable
    public static ClientHttpRequestInterceptor createNamespaceInterceptor(@Nullable final String namespace) {
        if (StringUtil.isEmpty(namespace)) {
            return null;
        }

        return new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
                HttpHeaders headers = request.getHeaders();

                if (!headers.containsKey(VaultInterceptors.VAULT_NAMESPACE_HEADER)) {
                    headers.set(VaultInterceptors.VAULT_NAMESPACE_HEADER, namespace);
                }

                return execution.execute(request, body);
            }
        };
    }
}