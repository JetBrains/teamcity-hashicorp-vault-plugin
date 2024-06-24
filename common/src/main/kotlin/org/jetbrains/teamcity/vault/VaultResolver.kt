package org.jetbrains.teamcity.vault

import com.intellij.openapi.diagnostic.Logger
import com.jayway.jsonpath.JsonPath
import jetbrains.buildServer.serverSide.TeamCityProperties
import org.jetbrains.teamcity.vault.retrier.VaultRetrier
import org.jetbrains.teamcity.vault.retrier.SpringHttpErrorCodeListener
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultResponse
import org.springframework.vault.support.VaultToken
import java.net.URI
import java.util.concurrent.Callable


open class VaultResolver(private val trustStoreProvider: SSLTrustStoreProvider) {
    companion object {
        private val LOG = Logger.getInstance(VaultResolver::class.java)
        private val retrier = VaultRetrier.getRetrier()
        const val DATA_KEY = "data"
    }

    data class ResolvingResult(val replacements: Map<String, String>, val errors: Map<String, String>)

    fun doFetchAndPrepareReplacements(
        settings: VaultFeatureSettings,
        token: String,
        parameters: Collection<VaultQuery>
    ): ResolvingResult {
        val endpoint = VaultEndpoint.from(URI.create(settings.url))
        val factory = createClientHttpRequestFactory(trustStoreProvider)
        val client = VaultTemplate(endpoint, settings.vaultNamespace, factory, SimpleSessionManager({ VaultToken.of(token) }))

        return doFetchAndPrepareReplacements(client, parameters)
    }

    fun doFetchAndPrepareReplacements(client: VaultTemplate, parameters: Collection<VaultQuery>): ResolvingResult {
        return VaultParametersFetcher(client).doFetchAndPrepareReplacements(parameters)
    }

    class VaultParametersFetcher(
        private val client: VaultTemplate,
    ) {
        fun doFetchAndPrepareReplacements(parameters: Collection<VaultQuery>): ResolvingResult {
            val responses = fetch(client, parameters.mapTo(HashSet()) { it.vaultPath })

            return getReplacements(parameters, responses)
        }

        private class ResolvingError(message: String) : Exception(message)

        private fun fetch(client: VaultTemplate, paths: Collection<String>): HashMap<String, HashiCorpVaultResponse<Exception, VaultResponse>> {
            val responses = HashMap<String, HashiCorpVaultResponse<Exception, VaultResponse>>(paths.size)

            for (path in paths.toSet()) {
                try {

                    val response = retrier.execute(Callable {
                        client.read(path.removePrefix("/"))
                    })

                    if (response == null) {
                        val errorMessage = getErrorMessage(path)
                        LOG.warn(errorMessage)
                        responses[path] = Error(errorMessage)
                    } else {
                        responses[path] = Response(response)
                    }
                } catch (e: Exception) {
                    LOG.warn(getErrorMessage(path), e)
                    responses[path] = Error(e)
                }
            }
            return responses
        }

        private fun getErrorMessage(path: String) = "Failed to fetch data for path '$path'"

        private fun getReplacements(parameters: Collection<VaultQuery>, responses: HashMap<String, HashiCorpVaultResponse<Exception, VaultResponse>>): ResolvingResult {
            val replacements = HashMap<String, String>()
            val errors = HashMap<String, String>()

            for (parameter in parameters) {
                val response = responses[parameter.vaultPath]
                when (response) {
                    is Response -> {
                        try {
                            val value = extract(response.value, parameter)
                            replacements[parameter.full] = value
                        } catch (e: ResolvingError) {
                            errors[parameter.full] = e.message!!
                        }
                    }

                    is Error -> errors[parameter.full] = "Failed to fetch data for path ${parameter.full}: ${response.value.message}"
                    else -> errors[parameter.full] = "Failed to fetch data for path ${parameter.full}"
                }
            }
            return ResolvingResult(replacements, errors)
        }

        @Throws(ResolvingError::class)
        private fun extract(response: VaultResponse, parameter: VaultQuery): String {
            val jsonPath = parameter.jsonPath
            val data = unwrapKV2IfNeeded(response.data)
            if (jsonPath == null) {
                if (data.isEmpty()) {
                    throw ResolvingError("There's no data in HashiCorp Vault response for '${parameter.vaultPath}'")
                }
                var key = "value"
                if (data.size == 1) {
                    key = data.keys.first()
                }
                val value = data[key]
                if (value == null) {
                    throw ResolvingError("'$key' is missing in HashiCorp Vault response for '${parameter.vaultPath}'")
                }
                if (value !is String) {
                    throw ResolvingError("Cannot extract data from non-string '$key'. Actual type is ${value.javaClass.simpleName} for '${parameter.vaultPath}'")
                }
                return value
            }

            val pattern: JsonPath?
            val updated = jsonPath.ensureHasPrefix("$.")
            try {
                pattern = JsonPath.compile(updated)
            } catch (e: Throwable) {
                LOG.warn("JsonPath compilation failed for '$updated'")
                throw ResolvingError("JsonPath compilation failed for '$updated' for '${parameter.vaultPath}'")
            }
            try {
                val value: Any? = pattern.read(data)
                if (value == null) {
                    throw ResolvingError("'$jsonPath' found nothing for '${parameter.vaultPath}'")
                }
                if (value !is String) {
                    throw ResolvingError("Cannot extract data from non-string '$jsonPath'. Actual type is ${value.javaClass.simpleName} for '${parameter.vaultPath}'")
                }
                return value
            } catch (e: Exception) {
                LOG.warn("Cannot extract '$jsonPath' data from '${parameter.vaultPath}', full reference: ${parameter.full}", e)
                throw ResolvingError("Cannot extract '$jsonPath' data from '${parameter.vaultPath}' for '${parameter.vaultPath}'")
            }
        }

        private fun unwrapKV2IfNeeded(data: Map<String, Any>): Map<String, Any> {
            if (isKV2Data(data)) {
                @Suppress("UNCHECKED_CAST")
                return data[DATA_KEY] as Map<String, Any>
            }
            return data
        }

        private fun isKV2Data(map: Map<String, Any>): Boolean {
            // Some heuristics to understand whether it's KV2 data
            val data = map[DATA_KEY]
            val metadata = map["metadata"]
            if (data == null || metadata == null) return false
            if (data !is Map<*, *>) return false
            if (metadata !is Map<*, *>) return false
            try {
                if (!metadata.keys.containsAll(listOf("created_time", "deletion_time", "destroyed", "version"))) return false
            } catch (ignore: Throwable) {
                return false
            }
            return true
        }
    }
}