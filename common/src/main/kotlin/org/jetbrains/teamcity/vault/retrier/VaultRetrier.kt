package org.jetbrains.teamcity.vault.retrier

import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.retry.Retrier
import org.apache.http.conn.ConnectTimeoutException

object VaultRetrier {
    const val MAX_RETRIES = "teamcity.hashicorp.vault.maxRetries"
    const val RETRY_DELAY = "teamcity.hashicorp.vault.retryDelaySeconds"

    fun getRetrier(): Retrier {
        val timeoutExceptionListener = object : ClientExceptionListener<ConnectTimeoutException>(ConnectTimeoutException::class) {
            override fun isNonRecoverableKubernetesException(exception: ConnectTimeoutException): Boolean = false
        }
        return jetbrains.buildServer.util.retry.Retrier.withRetries(
            TeamCityProperties.getInteger(VaultRetrier.MAX_RETRIES, 3),
            jetbrains.buildServer.util.retry.Retrier.DelayStrategy.linearBackOff(TeamCityProperties.getInteger(VaultRetrier.RETRY_DELAY, 3) * 1000))
            .registerListener(SpringHttpErrorCodeListener())
            .registerListener(timeoutExceptionListener)
    }
}