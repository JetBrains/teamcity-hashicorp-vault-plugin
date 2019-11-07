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
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.VersionComparatorUtil
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.support.ClientHttpRequestFactoryFactory
import org.jetbrains.teamcity.vault.support.MappingJackson2HttpMessageConverter
import org.jetbrains.teamcity.vault.support.RetryRestTemplate
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.retry.backoff.FixedBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.vault.client.VaultClients
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.client.VaultHttpHeaders
import org.springframework.vault.support.ClientOptions
import org.springframework.web.util.DefaultUriTemplateHandler
import java.net.URI

fun isDefault(namespace: String): Boolean {
    return namespace == VaultConstants.FeatureSettings.DEFAULT_PARAMETER_NAMESPACE
}

fun getEnvPrefix(namespace: String): String {
    return if (isDefault(namespace)) ""
    else namespace.replace("[^a-zA-Z0-9_]".toRegex(), "_").toUpperCase() + "_"
}

fun getVaultParameterName(namespace: String, suffix: String): String {
    if (isDefault(namespace)) return VaultConstants.PARAMETER_PREFIX + suffix
    return VaultConstants.PARAMETER_PREFIX + ".$namespace" + suffix
}

fun isUrlParameter(value: String): Boolean {
    return value.startsWith(VaultConstants.PARAMETER_PREFIX) && value.endsWith(VaultConstants.URL_PROPERTY_SUFFIX)
}


fun isJava8OrNewer(): Boolean {
    return VersionComparatorUtil.compare(System.getProperty("java.specification.version"), "1.8") >= 0
}

fun createClientHttpRequestFactory(trustStoreProvider: SSLTrustStoreProvider): ClientHttpRequestFactory {
    return ClientHttpRequestFactoryFactory.create(ClientOptions(), trustStoreProvider)
}

fun createRetryRestTemplate(settings: VaultFeatureSettings, trustStoreProvider: SSLTrustStoreProvider): RetryRestTemplate {
    val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
    val factory = createClientHttpRequestFactory(trustStoreProvider)
    // HttpComponents.usingHttpComponents(options, sslConfiguration)

    val retry = createRetryTemplate(settings.backoffPeriod, settings.maxAttempts)
    val template = createRetryRestTemplate(endpoint, factory)
    template.setRetryTemplate(retry)
    return template
}

fun createRetryRestTemplate(endpoint: VaultEndpoint, factory: ClientHttpRequestFactory): RetryRestTemplate {
    val template = createRetryRestTemplate()

    template.requestFactory = factory
    template.uriTemplateHandler = createUriTemplateHandler(endpoint)

    return template
}

fun RetryRestTemplate.withVaultToken(token: String): RetryRestTemplate {
    this.interceptors.add(ClientHttpRequestInterceptor { request, body, execution ->
        request.headers.add(VaultHttpHeaders.VAULT_TOKEN, token)
        execution.execute(request, body)
    })
    return this
}

private fun createRetryRestTemplate(): RetryRestTemplate {
    // Like in org.springframework.vault.client.VaultClients.createRestTemplate()
    // However custom Jackson2 converter is used
    val converters = listOf<HttpMessageConverter<*>>(
            ByteArrayHttpMessageConverter(),
            StringHttpMessageConverter(),
            MappingJackson2HttpMessageConverter()
    )
    return RetryRestTemplate(converters)
}

private fun createRetryTemplate(backoffPeriod: Long, maxAttempts: Int): RetryTemplate {
    val template = RetryTemplate()

    val backoffPolicy = FixedBackOffPolicy()
    backoffPolicy.backOffPeriod = backoffPeriod
    template.setBackOffPolicy(backoffPolicy)

    val retryPolicy = SimpleRetryPolicy()
    retryPolicy.maxAttempts = maxAttempts
    template.setRetryPolicy(retryPolicy)

    return template
}

fun isShouldSetEnvParameters(parameters: MutableMap<String, String>, namespace: String): Boolean {
    return parameters[getVaultParameterName(namespace, VaultConstants.BehaviourParameters.ExposeEnvSuffix)]
            ?.toBoolean() ?: false
}

private fun createUriTemplateHandler(endpoint: VaultEndpoint): DefaultUriTemplateHandler {
    val baseUrl = String.format("%s://%s:%s/%s/", endpoint.scheme, endpoint.host, endpoint.port, "v1")
    val handler = object : VaultClients.PrefixAwareUriTemplateHandler() {
        // For Spring 4.2 compatibility
        override fun expand(uriTemplate: String, uriVariables: MutableMap<String, *>?): URI {
            return super.expand(prepareUriTemplate(uriTemplate), uriVariables)
        }

        override fun expand(uriTemplate: String, vararg uriVariableValues: Any?): URI {
            return super.expand(prepareUriTemplate(uriTemplate), *uriVariableValues)
        }

        private fun prepareUriTemplate(uriTemplate: String): String {
            if (getBaseUrl() != null) {
                if (uriTemplate.startsWith("/") && getBaseUrl().endsWith("/")) {
                    return uriTemplate.substring(1)
                }

                if (!uriTemplate.startsWith("/") && !getBaseUrl().endsWith("/")) {
                    return "/" + uriTemplate
                }

                return uriTemplate
            }

            if (!uriTemplate.startsWith("/")) {
                return "/" + uriTemplate
            }
            return uriTemplate
        }
    }

    handler.baseUrl = baseUrl
    return handler
}

fun String?.nullIfEmpty(): String? {
    return StringUtil.nullIfEmpty(this)
}

fun String.ensureHasPrefix(prefix: String): String {
    return if (!this.startsWith(prefix)) "$prefix$this" else this
}

fun String.pluralize(size: Int) = StringUtil.pluralize(this, size)
fun String.pluralize(collection: Collection<Any>) = this.pluralize(collection.size)
fun String.sizeAndPluralize(collection: Collection<Any>) = "${collection.size} " + this.pluralize(collection)

fun <T> BuildProgressLogger.activity(activityName: String, activityType: String, body: () -> T): T {
    this.activityStarted(activityName, activityType)
    try {
        return body()
    } catch (t: Throwable) {
        this.internalError(activityType, "Exception occured: ${t.message}", t)
        throw t
    } finally {
        this.activityFinished(activityName, activityType)
    }
}