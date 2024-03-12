package org.jetbrains.teamcity.vault.agent

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.util.Pair
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.http.SimpleCredentials
import jetbrains.buildServer.util.HTTPRequestBuilder
import jetbrains.buildServer.util.http.HttpMethod
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.retrier.Retrier
import org.jetbrains.teamcity.vault.retrier.TeamCityHttpCodeListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

class VaultFeatureSettingsFetcher(private val sslTrustStoreProvider: SSLTrustStoreProvider, private val requestHandler: HTTPRequestBuilder.RequestHandler) {

    private val retrier: Retrier<HTTPRequestBuilder.Response> = Retrier(responseListeners = listOf(TeamCityHttpCodeListener()))

    @Autowired
    constructor(sslTrustStoreProvider: SSLTrustStoreProvider): this(sslTrustStoreProvider, HTTPRequestBuilder.DelegatingRequestHandler())


    private val objectMapper by lazy {
        jacksonObjectMapper()
    }

    fun getVaultFeatureSettings(namespace: String, build: AgentRunningBuild): VaultFeatureSettings? {
        val logger = build.buildLogger
        val errorPrefix = "Failed to get HashiCorp Vault wrapped token from TeamCity server for the project connection with ID '$namespace':"

        return try {
            val configuration = build.agentConfiguration as BuildAgentConfigurationEx
            val requestBuilder = HTTPRequestBuilder("${configuration.serverUrl}/app/${VaultConstants.ControllerSettings.URL}/${VaultConstants.ControllerSettings.WRAP_TOKEN_PATH}")
                .withMethod(HttpMethod.GET)
                .addParameters(
                    Pair("buildId", build.buildId.toString()),
                    Pair("namespace", namespace)
                )
                .withCredentials(SimpleCredentials(build.accessUser, build.accessCode))
                .withTimeout(configuration.serverConnectionTimeout * 1000)
                .allowNonSecureConnection(true)
                .withTrustStore(sslTrustStoreProvider.trustStore)

            if (configuration.serverProxyHost != null) {
                requestBuilder.withProxyHost(URIBuilder(configuration.serverProxyHost).setPort(configuration.serverProxyPort).build())

                val serverProxyCredentials = configuration.serverProxyCredentials
                if (serverProxyCredentials != null) {
                    requestBuilder.withProxyCredentials(serverProxyCredentials)
                }
            }


            val response = retrier.run {
                HTTPRequestBuilder.DelegatingRequestHandler().doSyncRequest(requestBuilder.build())
            }

            response.use {
                if (response == null || response.statusCode != HttpStatus.OK.value()) {
                    val errorMessage = "$errorPrefix ${response?.bodyAsString.orEmpty()}"
                    VaultBuildFeature.LOG.error(errorMessage)
                    logger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${namespace}_A", "VaultConnection", errorMessage))
                    build.interruptBuild(errorMessage, false)
                    return null
                }

                val contentStream = response.contentStream
                if (contentStream == null) {
                    val errorMessage = "$errorPrefix empty response from server"
                    VaultBuildFeature.LOG.error(errorMessage)
                    logger.logBuildProblem(BuildProblemData.createBuildProblem("VC_${build.buildTypeId}_${namespace}_A", "VaultConnection", errorMessage))
                    build.interruptBuild(errorMessage, false)
                    return null
                }

                val featureSettingsParams = objectMapper.readValue<Map<String, String>>(contentStream)

                VaultFeatureSettings.getAgentFeatureFromProperties(featureSettingsParams)
            }
        } catch (e: Throwable) {
            val errorMessage = "$errorPrefix internal error"
            VaultBuildFeature.LOG.error(errorMessage, e)
            logger.internalError(VaultConstants.FeatureSettings.FEATURE_TYPE, errorMessage, e)
            build.stopBuild(errorMessage)
            null
        }
    }
}