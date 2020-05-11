package org.jetbrains.teamcity.vault.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import java.io.IOException;

public class VaultInterceptors {
    public static final String VAULT_NAMESPACE = "X-Vault-Namespace";

	public static ClientHttpRequestInterceptor createNamespaceInterceptor(final String namespace) {

        final ClientHttpRequestInterceptor interceptor = new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
			HttpHeaders headers = request.getHeaders();

			if (!headers.containsKey(VaultInterceptors.VAULT_NAMESPACE)) {
				headers.add(VaultInterceptors.VAULT_NAMESPACE, namespace);
			}

			return execution.execute(request, body);
            }
        };
        return interceptor;
	}
}
