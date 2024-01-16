
package org.jetbrains.teamcity.vault

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.support.ClientHttpRequestFactoryFactory
import org.jetbrains.teamcity.vault.support.MappingJackson2HttpMessageConverter
import org.jetbrains.teamcity.vault.support.VaultInterceptors
import org.jetbrains.teamcity.vault.support.VaultResponses
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.util.Assert
import org.springframework.vault.client.VaultClients
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.client.VaultHttpHeaders
import org.springframework.vault.support.ClientOptions
import org.springframework.vault.support.VaultResponse
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriTemplateHandler
import java.net.URI

fun isDefault(namespace: String): Boolean {
    return namespace == VaultConstants.FeatureSettings.DEFAULT_ID
}

fun getEnvPrefix(namespace: String): String {
    return if (isDefault(namespace)) ""
    else namespace.replace("[^a-zA-Z0-9_]".toRegex(), "_").toUpperCase() + "_"
}

fun getVaultParameterName(namespace: String, suffix: String): String {
    if (isDefault(namespace)) return VaultConstants.PARAMETER_PREFIX + suffix
    return VaultConstants.PARAMETER_PREFIX + ".$namespace" + suffix
}

fun isLegacyReferencesUsedParameter(value: String) =
        value.startsWith(VaultConstants.PARAMETER_PREFIX) && value.endsWith(VaultConstants.LEGACY_REFERENCES_USED_SUFFIX)

fun createClientHttpRequestFactory(trustStoreProvider: SSLTrustStoreProvider): ClientHttpRequestFactory {
    return ClientHttpRequestFactoryFactory.create(ClientOptions(), trustStoreProvider)
}

fun createRestTemplate(settings: VaultFeatureSettings, trustStoreProvider: SSLTrustStoreProvider): RestTemplate {
    val endpoint = VaultEndpoint.from(URI.create(settings.url))!!
    val factory = createClientHttpRequestFactory(trustStoreProvider)
    // HttpComponents.usingHttpComponents(options, sslConfiguration)

    return createRestTemplate(endpoint, factory).also { tempalte ->
        VaultInterceptors.createNamespaceInterceptor(settings.vaultNamespace)?.let { tempalte.interceptors.add(it) }
    }
}

fun createRestTemplate(endpoint: VaultEndpoint, factory: ClientHttpRequestFactory): RestTemplate {
    val template = createRestTemplate()

    template.requestFactory = factory
    template.uriTemplateHandler = createUriTemplateHandler(endpoint)

    return template
}

fun RestTemplate.withVaultToken(token: String): RestTemplate {
    this.interceptors.add(ClientHttpRequestInterceptor { request, body, execution ->
        request.headers.add(VaultHttpHeaders.VAULT_TOKEN, token)
        execution.execute(request, body)
    })
    return this
}


fun RestTemplate.write(path: String, body: Any?): VaultResponse? {
    Assert.hasText(path, "Path must not be empty")
    return try {
        this.postForObject(path, jacksonObjectMapper().writeValueAsString(body), VaultResponse::class.java)
    } catch (e: HttpStatusCodeException) {
        throw VaultResponses.buildException(e, path)
    }
}

private fun createRestTemplate(): RestTemplate {
    // Like in org.springframework.vault.client.VaultClients.createRestTemplate()
    // However custom Jackson2 converter is used
    val converters = listOf<HttpMessageConverter<*>>(
            ByteArrayHttpMessageConverter(),
            StringHttpMessageConverter(),
            MappingJackson2HttpMessageConverter()
    )
    return RestTemplate(converters)
}

fun isShouldSetEnvParameters(parameters: Map<String, String>, namespace: String): Boolean {
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