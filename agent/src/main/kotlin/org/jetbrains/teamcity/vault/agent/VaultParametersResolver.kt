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
package org.jetbrains.teamcity.vault.agent

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.jayway.jsonpath.JsonPath
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.log.Loggers
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultResponse
import org.springframework.vault.support.VaultToken
import java.net.URI
import java.util.*
import kotlin.collections.HashSet

class VaultParametersResolver {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultParametersResolver::class.java.name)!!
    }

    fun resolve(build: AgentRunningBuild, settings: VaultFeatureSettings, token: String) {
        val references = getReleatedParameterReferences(build, settings.prefix)
        if (references.isEmpty()) {
            LOG.info("There's nothing to resolve")
            return
        }
        val logger = build.buildLogger
        logger.message("${references.size} Vault ${"reference".pluralize(references)} to resolve: $references")

        val parameters = references.map { VaultParameter.extract(VaultReferencesUtil.getVaultPath(it, settings.prefix)) }

        val replacements = doFetchAndPrepareReplacements(settings, token, parameters, logger)

        replaceParametersReferences(build, replacements, references, settings.prefix)

        replacements.values.forEach { build.passwordReplacer.addPassword(it) }
    }

    fun doFetchAndPrepareReplacements(settings: VaultFeatureSettings, token: String, parameters: List<VaultParameter>, logger: BuildProgressLogger): HashMap<String, String> {
        val endpoint = VaultEndpoint.from(URI.create(settings.url))
        val factory = createClientHttpRequestFactory()
        val client = VaultTemplate(endpoint, factory, SimpleSessionManager({ VaultToken.of(token) }))

        return doFetchAndPrepareReplacements(client, parameters, logger)
    }

    fun doFetchAndPrepareReplacements(client: VaultTemplate, parameters: List<VaultParameter>, logger: BuildProgressLogger): HashMap<String, String> {
        return VaultParametersFetcher(client, logger).doFetchAndPrepareReplacements(parameters)
    }

    class VaultParametersFetcher(private val client: VaultTemplate,
                                 private val logger: BuildProgressLogger) {
        fun doFetchAndPrepareReplacements(parameters: List<VaultParameter>): HashMap<String, String> {
            val responses = fetch(client, parameters.mapTo(HashSet()) { it.vaultPath })

            return getReplacements(parameters, responses)
        }

        private fun fetch(client: VaultTemplate, paths: Collection<String>): HashMap<String, VaultResponse?> {
            val responses = HashMap<String, VaultResponse?>(paths.size)

            for (path in paths.toSet()) {
                try {
                    val response = client.read(path.removePrefix("/"))
                    responses[path] = response
                } catch (e: Exception) {
                    logger.warning("Failed to fetch data for path '$path'")
                    LOG.warn("Failed to fetch data for path '$path'", e)
                    responses[path] = null
                }
            }
            return responses
        }

        private fun getReplacements(parameters: List<VaultParameter>, responses: Map<String, VaultResponse?>): HashMap<String, String> {
            val replacements = HashMap<String, String>()

            for (parameter in parameters) {
                val response = responses[parameter.vaultPath]
                if (response == null) {
                    logger.warning("Cannot resolve '${parameter.full}': data wasn't received from HashiCorp Vault")
                    LOG.warn("Cannot resolve '${parameter.full}': data wasn't received from HashiCorp Vault")
                    continue
                }
                val value = extract(response, parameter) ?: continue
                replacements[parameter.full] = value
            }
            return replacements
        }

        private fun extract(response: VaultResponse, parameter: VaultParameter): String? {
            val jsonPath = parameter.jsonPath
            if (jsonPath == null) {
                val data = response.data
                if (data.isEmpty()) {
                    logger.warning("There's no data in HashiCorp Vault response for '${parameter.vaultPath}'")
                    return null
                }
                var key = "value"
                if (data.size == 1) {
                    key = data.keys.first()
                }
                val value = data[key]
                if (value == null) {
                    logger.warning("'$key' is missing in HashiCorp Vault response for '${parameter.vaultPath}'")
                    return null
                }
                if (value !is String) {
                    logger.warning("From '${parameter.vaultPath}' cannot extract data from non-string '$key'. Actual type is ${value.javaClass.simpleName}")
                    return null
                }
                return value
            }

            val pattern: JsonPath?
            val updated = jsonPath.ensureHasPrefix("$.")
            try {
                pattern = JsonPath.compile(updated)
            } catch (e: Throwable) {
                logger.warning("JsonPath compilation failed for '$updated'")
                LOG.warn("JsonPath compilation failed for '$updated'")
                return null
            }
            try {
                val value: Any? = pattern.read(Gson().toJson(response.data))
                if (value == null) {
                    logger.warning("'$jsonPath' is missing result structure for '${parameter.vaultPath}'")
                    return null
                }
                if (value !is String) {
                    logger.warning("From '${parameter.vaultPath}' cannot extract data from non-string '$jsonPath'. Actual type is ${value.javaClass.simpleName}")
                    return null
                }
                return value
            } catch (e: Exception) {
                logger.warning("Cannot extract '$jsonPath' data from '${parameter.vaultPath}', full reference: ${parameter.full}")
                LOG.warn("Cannot extract '$jsonPath' data from '${parameter.vaultPath}', full reference: ${parameter.full}", e)
                return null
            }
        }
    }


    private fun getReleatedParameterReferences(build: AgentRunningBuild, prefix: String): Collection<String> {
        val references = HashSet<String>()
        VaultReferencesUtil.collect(build.sharedConfigParameters, references, prefix)
        VaultReferencesUtil.collect(build.sharedBuildParameters.allParameters, references, prefix)
        return references.sorted()
    }

    private fun replaceParametersReferences(build: AgentRunningBuild, replacements: HashMap<String, String>, usages: Collection<String>, prefix: String) {
        // usage may not have leading slash
        for (usage in usages) {
            val replacement = replacements[usage.removePrefix(prefix + ":").ensureHasPrefix("/")]
            if (replacement != null) {
                build.addSharedConfigParameter(usage, replacement)
            }
        }
    }

}