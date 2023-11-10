package org.jetbrains.teamcity.vault

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.support.LifecycleAwareSessionManager
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler
import org.springframework.vault.authentication.*
import org.springframework.vault.support.VaultToken
import org.springframework.web.client.RestTemplate
import java.util.concurrent.TimeUnit

class SessionManagerBuilder(
    private val trustStoreProvider: SSLTrustStoreProvider,
) {
    private val scheduler: TaskScheduler = ConcurrentTaskScheduler()

    private fun buildRestTemplate(settings: VaultFeatureSettings) = createRestTemplate(settings, trustStoreProvider)

    private fun buildClientAuthentication(settings: VaultFeatureSettings, template: RestTemplate): ClientAuthentication = when (settings.auth.method) {
        AuthMethod.APPROLE,
        AuthMethod.LDAP -> {
            val wrapped = when (val auth = settings.auth) {
                is Auth.AppRoleAuthAgent -> auth.wrappedToken
                is Auth.LdapAgent -> auth.wrappedToken
                else -> error("Unsupported auth method: ${settings.auth.method}, class: ${settings.auth::class.qualifiedName}")
            }
            if (wrapped.isBlank()) {
                throw RuntimeException("Wrapped HashiCorp Vault token for url ${settings.url} not found")
            }
            if (VaultConstants.SPECIAL_VALUES.contains(wrapped)) {
                throw RuntimeException("Wrapped HashiCorp Vault token value for url ${settings.url} is incorrect, seems there was error fetching token on TeamCity server side")
            }
            createCubbyholeAuthentication(wrapped, template)
        }
    }

    private fun getTimeoutSeconds(settings: VaultFeatureSettings) = TeamCityProperties.getLong(getVaultParameterName(settings.namespace, VaultConstants.TOKEN_REFRESH_TIMEOUT_PROPERTY_SUFFIX), 15)

    private fun createAwsIamAuthentication(restTemplate: RestTemplate): AwsIamAuthentication {
        val options = AwsIamAuthenticationOptions.builder()
            .credentialsProvider(InstanceProfileCredentialsProvider.getInstance()).build()

        return AwsIamAuthentication(options, restTemplate)
    }

    private fun createCubbyholeAuthentication(wrapped: String, restTemplate: RestTemplate): CubbyholeAuthentication {
        val options = CubbyholeAuthenticationOptions.builder()
            .wrapped()
            .initialToken(VaultToken.of(wrapped))
            .build()
        return CubbyholeAuthentication(options, restTemplate)
    }

    fun buildWithImprovedLogging(settings: VaultFeatureSettings, logger: BuildProgressLogger): LifecycleAwareSessionManager {
        val template = buildRestTemplate(settings)
        val authentication = buildClientAuthentication(settings, template)


        return LifecycleAwareSessionManager(
            authentication, scheduler, template,
            LifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(getTimeoutSeconds(settings), TimeUnit.SECONDS), logger
        )
    }

    fun build(settings: VaultFeatureSettings): org.springframework.vault.authentication.LifecycleAwareSessionManager {
        val template = buildRestTemplate(settings)
        val authentication = buildClientAuthentication(settings, template)


        return org.springframework.vault.authentication.LifecycleAwareSessionManager(
            authentication, scheduler, template,
            LifecycleAwareSessionManagerSupport.FixedTimeoutRefreshTrigger(getTimeoutSeconds(settings), TimeUnit.SECONDS)
        )
    }
}