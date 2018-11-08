/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.support;

import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.vault.support.ClientOptions;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.net.ProxySelector;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Simplified copy of {@link org.springframework.vault.config.ClientHttpRequestFactoryFactory}
 * With proper redirect handling for POST requests
 * <p>
 * Factory for {@link ClientHttpRequestFactory} that supports Apache HTTP Components,
 * OkHttp, Netty and the JDK HTTP client (in that order). This factory configures a
 * {@link ClientHttpRequestFactory} depending on the available dependencies.
 *
 * @author Mark Paluch
 * @author Vladislav Rassokhin
 */
public class ClientHttpRequestFactoryFactory {

    /**
     * Create a {@link ClientHttpRequestFactory} for the given {@link ClientOptions} and
     * {@link SSLTrustStoreProvider}.
     *
     * @param options
     * @param trustStoreProvider
     * @return a new {@link ClientHttpRequestFactory}. Lifecycle beans must be initialized
     * after obtaining.
     */
    public static ClientHttpRequestFactory create(@NotNull ClientOptions options,
                                                  @Nullable SSLTrustStoreProvider trustStoreProvider) {
        try {
            return HttpComponents.usingHttpComponents(options, trustStoreProvider);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static SSLContext getSSLContext(@NotNull KeyStore trustStore)
            throws GeneralSecurityException {

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);

        return sslContext;
    }

    /**
     * {@link ClientHttpRequestFactory} for Apache Http Components.
     *
     * @author Mark Paluch
     */
    static class HttpComponents {

        static ClientHttpRequestFactory usingHttpComponents(@NotNull ClientOptions options,
                                                            @Nullable SSLTrustStoreProvider trustStoreProvider) throws GeneralSecurityException {

            HttpClientBuilder httpClientBuilder = HttpClients.custom();

            httpClientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(
                    DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault()));

            KeyStore trustStore = trustStoreProvider != null ? trustStoreProvider.getTrustStore() : null;

            if (trustStore != null) {
                SSLContext sslContext = getSSLContext(trustStore);
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
                httpClientBuilder.setSSLSocketFactory(sslSocketFactory);
                httpClientBuilder.setSslcontext(sslContext);
            }

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(options.getConnectionTimeout())
                    .setSocketTimeout(options.getReadTimeout())
                    .setAuthenticationEnabled(true)
                    .build();

            httpClientBuilder.setDefaultRequestConfig(requestConfig);

            // Support redirects
            httpClientBuilder.setRedirectStrategy(new LaxRedirectStrategy());

            // Fix weird problem `ProtocolException: Content-Length header already present` from `org.apache.http.protocol.RequestContent`
            httpClientBuilder.addInterceptorFirst(new HttpRequestInterceptor() {
                @Override
                public void process(HttpRequest request, HttpContext context) {
                    if (request instanceof HttpEntityEnclosingRequest) {
                        request.removeHeaders(HTTP.TRANSFER_ENCODING);
                        request.removeHeaders(HTTP.CONTENT_LEN);
                    }
                }
            });

            return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
        }
    }

}
