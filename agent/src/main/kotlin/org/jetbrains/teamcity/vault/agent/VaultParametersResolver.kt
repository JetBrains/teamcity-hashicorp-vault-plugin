package org.jetbrains.teamcity.vault.agent

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.jayway.jsonpath.JsonPath
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.Constants
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.resolveVaultReferences
import org.jetbrains.teamcity.vault.collectRerefences
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.config.ClientHttpRequestFactoryFactory
import org.springframework.vault.support.ClientOptions
import org.springframework.vault.support.SslConfiguration
import org.springframework.vault.support.VaultResponse
import org.springframework.vault.support.VaultToken
import java.net.URI
import java.util.*
import kotlin.collections.HashSet

class VaultParametersResolver {
    companion object {
        val LOG = Logger.getInstance(VaultParametersResolver::class.java.name)!!
    }

    fun resolve(build: AgentRunningBuild, settings: VaultFeatureSettings, token: String) {
        val values = getReleatedParameterValues(build)
        val (referenceKeys, references) = getReleatedParameterReferences(build)
        if (values.isEmpty() && references.isEmpty()) return

        LOG.info("${values.size} Values to resolve: $values")
        LOG.info("${references.size} References to resolve: $references")

        val parameters = (values + references).map { VaultParameter.extract(it.removePrefix(VaultConstants.VAULT_PARAMETER_PREFIX).ensureHasPrefix("/")) }

        val replacements = doFetchAndPrepareReplacements(settings, token, parameters)

        replaceParametersValues(build, replacements)
        replaceParametersReferences(build, referenceKeys, replacements)

        replacements.values.forEach { build.passwordReplacer.addPassword(it) }
    }

    fun doFetchAndPrepareReplacements(settings: VaultFeatureSettings, token: String, parameters: List<VaultParameter>): HashMap<String, String> {
        val endpoint = VaultEndpoint.from(URI.create(settings.url))
        val factory = ClientHttpRequestFactoryFactory.create(ClientOptions(), SslConfiguration.NONE)
        val client = VaultTemplate(endpoint, factory, SimpleSessionManager({ VaultToken.of(token) }))

        return doFetchAndPrepareReplacements(client, parameters)
    }

    fun doFetchAndPrepareReplacements(client: VaultTemplate, parameters: List<VaultParameter>): HashMap<String, String> {
        val responses = fetch(client, parameters.mapTo(HashSet()) { it.vaultPath })

        val replacements = getReplacements(parameters, responses)
        return replacements
    }

    private fun getReplacements(parameters: List<VaultParameter>, responses: Map<String, VaultResponse?>): HashMap<String, String> {
        val replacements = HashMap<String, String>()

        for (parameter in parameters) {
            val response = responses[parameter.vaultPath]
            if (response == null) {
                LOG.warn("Cannot resolve '${parameter.full}': data wasn't received from Vault")
                continue
            }
            val value = extract(response, parameter.jsonPath)
            if (value == null) {
                LOG.warn("Cannot extract '${parameter.full}' from Vault result")
                continue
            }
            replacements[parameter.full] = value
        }
        return replacements
    }

    private fun getReleatedParameterValues(build: AgentRunningBuild): Collection<String> {
        val predicate: (String) -> Boolean = { it.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX) }
        val values = HashSet<String>()
        build.sharedConfigParameters.values.filterTo(values, predicate)
        build.sharedBuildParameters.allParameters.values.filterTo(values, predicate)
        return values.sorted()
    }

    private fun getReleatedParameterReferences(build: AgentRunningBuild): Pair<Collection<String>, Collection<String>> {
        val references = HashSet<String>()
        val keys = HashSet<String>()
        collectRerefences(build.sharedConfigParameters, references, keys)
        collectRerefences(build.sharedBuildParameters.allParameters, references, keys)
        return keys to references.sorted()
    }

    private fun extract(response: VaultResponse, jsonPath: String?): String? {
        if (jsonPath == null) {
            val data = response.data
            if (data.isEmpty()) {
                LOG.warn("There's no data in Vault response")
                return null
            }
            var key: String = "value"
            if (data.size == 1) {
                key = data.keys.first()
            }
            val value = data[key]
            if (value == null) {
                LOG.warn("'$key' is missing in Vault response")
                return null
            }
            if (value !is String) {
                LOG.warn("Cannot extract data from non-string '$key'. Actual type is ${value.javaClass.simpleName}")
                return null
            }
            return value
        }

        val pattern: JsonPath?
        val updated = jsonPath.ensureHasPrefix("$.")
        try {
            pattern = JsonPath.compile(updated)
        } catch(e: Throwable) {
            LOG.warnAndDebugDetails("JsonPath compilation failed for '$updated'", e)
            return null
        }
        try {
            val value: Any? = pattern.read(Gson().toJson(response.data))
            if (value == null) {
                LOG.warn("'$jsonPath' is missing")
                return null
            }
            if (value !is String) {
                LOG.warn("Cannot extract data from non-string '$jsonPath'. Actual type is ${value.javaClass.simpleName}")
                return null
            }
            return value
        } catch(e: Exception) {
            LOG.warnAndDebugDetails("Cannot extract '$jsonPath' data from response", e)
            return null
        }
    }


    private fun replaceParametersValues(build: AgentRunningBuild, replacements: HashMap<String, String>) {
        for ((k, v) in HashMap(build.sharedConfigParameters)) {
            if (!v.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX)) continue
            val replacement = replacements[v.removePrefix(VaultConstants.VAULT_PARAMETER_PREFIX)]
            if (replacement != null) {
                build.addSharedConfigParameter(k, replacement)
            }
        }
        for ((k, v) in HashMap(build.sharedBuildParameters.allParameters)) {
            if (!v.startsWith(VaultConstants.VAULT_PARAMETER_PREFIX)) continue
            val replacement = replacements[v.removePrefix(VaultConstants.VAULT_PARAMETER_PREFIX)]
            if (replacement != null) {
                when {
                    k.startsWith(Constants.ENV_PREFIX) -> build.addSharedEnvironmentVariable(k.removePrefix(Constants.ENV_PREFIX), replacement)
                    k.startsWith(Constants.SYSTEM_PREFIX) -> build.addSharedSystemProperty(k.removePrefix(Constants.SYSTEM_PREFIX), replacement)
                    else -> build.addSharedConfigParameter(k, replacement)
                }
            }
        }
    }

    private fun replaceParametersReferences(build: AgentRunningBuild, keys: Collection<String>, replacements: HashMap<String, String>) {
        val sharedConfigParameters = build.sharedConfigParameters
        for (k in keys) {
            val value = sharedConfigParameters[k] ?: continue
            val resolved = resolveVaultReferences(value, replacements)
            if (resolved != null) {
                build.addSharedConfigParameter(k, resolved)
            }
        }
        val allParameters = build.sharedBuildParameters.allParameters
        for (k in keys) {
            val value = allParameters[k] ?: continue
            val resolved = resolveVaultReferences(value, replacements)
            if (resolved != null) {
                when {
                    k.startsWith(Constants.ENV_PREFIX) -> build.addSharedEnvironmentVariable(k.removePrefix(Constants.ENV_PREFIX), resolved)
                    k.startsWith(Constants.SYSTEM_PREFIX) -> build.addSharedSystemProperty(k.removePrefix(Constants.SYSTEM_PREFIX), resolved)
                    else -> build.addSharedConfigParameter(k, resolved)
                }
            }
        }
    }


    private fun fetch(client: VaultTemplate, paths: Collection<String>): HashMap<String, VaultResponse?> {
        val responses = HashMap<String, VaultResponse?>(paths.size)

        for (path in paths.toSet()) {
            try {
                val response = client.read(path.removePrefix("/"))
                responses[path] = response
            } catch(e: Exception) {
                LOG.warn("Failed to fetch data for path '$path'")
                responses[path] = null
            }
        }
        return responses
    }

}