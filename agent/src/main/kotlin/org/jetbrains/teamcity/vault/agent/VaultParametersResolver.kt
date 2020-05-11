/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger
import com.jayway.jsonpath.JsonPath
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.*
import org.jetbrains.teamcity.vault.support.VaultTemplate
import org.springframework.vault.authentication.SimpleSessionManager
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.support.VaultResponse
import org.springframework.vault.support.VaultToken
import java.net.URI
import java.util.*
import kotlin.collections.HashSet

class VaultParametersResolver(private val trustStoreProvider: SSLTrustStoreProvider) {
    companion object {
        val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + VaultParametersResolver::class.java.name)!!
    }

    fun resolve(build: AgentRunningBuild, settings: VaultFeatureSettings, token: String) {
        val references = getReleatedParameterReferences(build, settings.namespace)
        if (references.isEmpty()) {
            LOG.info("There's nothing to resolve")
            return
        }
        val logger = build.buildLogger
        logger.message("${references.size} Vault ${"reference".pluralize(references)} to resolve: $references")

        val parameters = references.map { VaultParameter.extract(VaultReferencesUtil.getPath(it, settings.namespace)) }

        val replacements = doFetchAndPrepareReplacements(settings, token, parameters, logger)

        if (settings.failOnError && replacements.errors.isNotEmpty()) {
            val ns = if (isDefault(settings.namespace)) "" else "('${settings.namespace}' namespace)"
            val message = "${"Error".pluralize(replacements.errors.size)} while fetching data from HashiCorp Vault $ns"
            build.buildLogger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${settings.namespace}_A", "VaultConnection", message))
            build.stopBuild(message)
        }

        replaceParametersReferences(build, replacements.replacements, references, settings.namespace)

        replacements.replacements.values.forEach { build.passwordReplacer.addPassword(it) }
    }

    fun doFetchAndPrepareReplacements(settings: VaultFeatureSettings, token: String, parameters: List<VaultParameter>, logger: BuildProgressLogger): ResolvingResult {
        val endpoint = VaultEndpoint.from(URI.create(settings.url))
        val factory = createClientHttpRequestFactory(trustStoreProvider)
        val client = VaultTemplate(endpoint, settings.vaultNamespace, factory, SimpleSessionManager({ VaultToken.of(token) }))

        return doFetchAndPrepareReplacements(client, parameters, logger)
    }

    fun doFetchAndPrepareReplacements(client: VaultTemplate, parameters: List<VaultParameter>, logger: BuildProgressLogger): ResolvingResult {
        return VaultParametersFetcher(client, logger).doFetchAndPrepareReplacements(parameters)
    }

    data class ResolvingResult(val replacements: Map<String, String>, val errors: Map<String, String>)

    class VaultParametersFetcher(private val client: VaultTemplate,
                                 private val logger: BuildProgressLogger) {
        fun doFetchAndPrepareReplacements(parameters: List<VaultParameter>): ResolvingResult {
            val responses = fetch(client, parameters.mapTo(HashSet()) { it.vaultPath })

            return getReplacements(parameters, responses)
        }

        private class ResolvingError(message: String) : Exception(message)

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

        private fun getReplacements(parameters: List<VaultParameter>, responses: Map<String, VaultResponse?>): ResolvingResult {
            val replacements = HashMap<String, String>()
            val errors = HashMap<String, String>()

            for (parameter in parameters) {
                try {
                    val response = responses[parameter.vaultPath]
                    if (response == null) {
                        logger.warning("Cannot resolve '${parameter.full}': data wasn't received from HashiCorp Vault")
                        LOG.warn("Cannot resolve '${parameter.full}': data wasn't received from HashiCorp Vault")
                        throw ResolvingError("Data wasn't received from HashiCorp Vault")
                    }
                    val value = extract(response, parameter)
                    replacements[parameter.full] = value
                } catch (e: ResolvingError) {
                    errors[parameter.full] = e.message!!
                }
            }
            return ResolvingResult(replacements, errors)
        }

        @Throws(ResolvingError::class)
        private fun extract(response: VaultResponse, parameter: VaultParameter): String {
            val jsonPath = parameter.jsonPath
            val data = unwrapKV2IfNeeded(response.data)
            if (jsonPath == null) {
                if (data.isEmpty()) {
                    logger.warning("There's no data in HashiCorp Vault response for '${parameter.vaultPath}'")
                    throw ResolvingError("There's no data in HashiCorp Vault response")
                }
                var key = "value"
                if (data.size == 1) {
                    key = data.keys.first()
                }
                val value = data[key]
                if (value == null) {
                    logger.warning("'$key' is missing in HashiCorp Vault response for '${parameter.vaultPath}'")
                    throw ResolvingError("'$key' is missing in HashiCorp Vault response")
                }
                if (value !is String) {
                    logger.warning("From '${parameter.vaultPath}' cannot extract data from non-string '$key'. Actual type is ${value.javaClass.simpleName}")
                    throw ResolvingError("Cannot extract data from non-string '$key'. Actual type is ${value.javaClass.simpleName}")
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
                throw ResolvingError("JsonPath compilation failed for '$updated'")
            }
            try {
                val value: Any? = pattern.read(data)
                if (value == null) {
                    logger.warning("'$jsonPath' found nothing in result of '${parameter.vaultPath}'")
                    throw ResolvingError("'$jsonPath' found nothing")
                }
                if (value !is String) {
                    logger.warning("From '${parameter.vaultPath}' cannot extract data from non-string '$jsonPath'. Actual type is ${value.javaClass.simpleName}")
                    throw ResolvingError("Cannot extract data from non-string '$jsonPath'. Actual type is ${value.javaClass.simpleName}")
                }
                return value
            } catch (e: Exception) {
                logger.warning("Cannot extract '$jsonPath' data from '${parameter.vaultPath}', full reference: ${parameter.full}")
                LOG.warn("Cannot extract '$jsonPath' data from '${parameter.vaultPath}', full reference: ${parameter.full}", e)
                throw ResolvingError("Cannot extract '$jsonPath' data from '${parameter.vaultPath}'")
            }
        }

        private fun unwrapKV2IfNeeded(data: Map<String, Any>): Map<String, Any> {
            if (isKV2Data(data)) {
                @Suppress("UNCHECKED_CAST")
                return data["data"] as Map<String, Any>
            }
            return data
        }

        private fun isKV2Data(map: Map<String, Any>): Boolean {
            // Some heuristics to understand whether it's KV2 data
            val data = map["data"]
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


    private fun getReleatedParameterReferences(build: AgentRunningBuild, namespace: String): Collection<String> {
        val references = HashSet<String>()
        VaultReferencesUtil.collect(build.sharedConfigParameters, references, namespace)
        VaultReferencesUtil.collect(build.sharedBuildParameters.allParameters, references, namespace)
        return references.sorted()
    }

    private fun replaceParametersReferences(build: AgentRunningBuild, replacements: Map<String, String>, usages: Collection<String>, namespace: String) {
        // usage may not have leading slash
        for (usage in usages) {
            val replacement = replacements[VaultReferencesUtil.getPath(usage, namespace)]
            if (replacement != null) {
                build.addSharedConfigParameter(usage, replacement)
            }
        }
    }

}