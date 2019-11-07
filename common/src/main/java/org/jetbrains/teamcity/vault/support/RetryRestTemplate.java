/*
 * Copyright 2002-2016 the original author or authors.
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

import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.*;

import java.net.URI;
import java.util.List;
import java.util.Map;


public class RetryRestTemplate extends RestTemplate {

    private RetryTemplate retryTemplate = new RetryTemplate();

    public RetryRestTemplate(List<HttpMessageConverter<?>> messageConverters) {
        super(messageConverters);
    }

    @Override
    public <T> T execute(final String url, final HttpMethod method, final RequestCallback requestCallback,
                         final ResponseExtractor<T> responseExtractor, final Map<String, ?> urlVariables) throws RestClientException {

        return this.retryTemplate.execute(new RetryCallback<T, RestClientException>() {
            @Override
            public T doWithRetry(RetryContext context) throws RestClientException {
                return RetryRestTemplate.super.execute(url, method, requestCallback, responseExtractor, urlVariables);
            }
        });
    }

    @Override
    public <T> T execute(final URI url, final HttpMethod method, final RequestCallback requestCallback,
                         final ResponseExtractor<T> responseExtractor) throws RestClientException {

        return this.retryTemplate.execute(new RetryCallback<T, RestClientException>() {
            @Override
            public T doWithRetry(RetryContext context) throws RestClientException {
                return RetryRestTemplate.super.execute(url, method, requestCallback, responseExtractor);
            }
        });
    }

    @Override
    public <T> T execute(final String url, final HttpMethod method, final RequestCallback requestCallback,
                         final ResponseExtractor<T> responseExtractor, final Object... urlVariables) throws RestClientException {

        return this.retryTemplate.execute(new RetryCallback<T, RestClientException>() {
            @Override
            public T doWithRetry(RetryContext context) throws RestClientException {
                return RetryRestTemplate.super.execute(url, method, requestCallback, responseExtractor, urlVariables);
            }
        });
    }

    public void setRetryTemplate(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }
}
