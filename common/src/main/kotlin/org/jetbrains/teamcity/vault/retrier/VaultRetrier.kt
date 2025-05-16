package org.jetbrains.teamcity.vault.retrier

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.retry.Retrier
import jetbrains.buildServer.util.retry.RetrierEventListener
import org.apache.http.conn.ConnectTimeoutException
import java.util.concurrent.Callable

object VaultRetrier {
    const val MAX_RETRIES = "teamcity.hashicorp.vault.maxRetries"
    const val RETRY_DELAY = "teamcity.hashicorp.vault.retryDelaySeconds"
    private val LOG = Logger.getInstance(VaultRetrier::class.java)

    fun getRetrier(retrierPurpose: String): Retrier {
        val timeoutExceptionListener = object : ClientExceptionListener<ConnectTimeoutException>(ConnectTimeoutException::class) {
            override fun isNonRecoverableKubernetesException(exception: ConnectTimeoutException): Boolean = false
        }

        val loggerRetrierListener = object : RetrierEventListener {
            override fun <T : Any?> beforeRetry(callable: Callable<T?>, retry: Int) {
                if (retry > 0) {
                    LOG.warn("Execution for $retrierPurpose has failed, retrier is retrying execution (attempt $retry)")
                }
            }
        }

        return jetbrains.buildServer.util.retry.Retrier.withRetries(
            TeamCityProperties.getInteger(VaultRetrier.MAX_RETRIES, 3),
            jetbrains.buildServer.util.retry.Retrier.DelayStrategy.linearBackOff(TeamCityProperties.getInteger(VaultRetrier.RETRY_DELAY, 3) * 1000)
        )
            .registerListener(SpringHttpErrorCodeListener())
            .registerListener(timeoutExceptionListener)
            .registerListener(loggerRetrierListener)
    }
}