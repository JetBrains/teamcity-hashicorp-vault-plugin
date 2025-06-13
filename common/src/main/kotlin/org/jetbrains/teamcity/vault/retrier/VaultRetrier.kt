package org.jetbrains.teamcity.vault.retrier

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.retry.Retrier
import jetbrains.buildServer.util.retry.RetrierEventListener
import org.apache.http.conn.ConnectTimeoutException
import java.lang.Exception
import java.util.concurrent.Callable

object VaultRetrier {
    const val MAX_ATTEMPTS_PARAM = "teamcity.internal.hashicorp.vault.retry.maxAttempts"
    const val RETRY_DELAY_PARAM = "teamcity.internal.hashicorp.vault.retry.delayMillis"
    private val LOG = Logger.getInstance(VaultRetrier::class.java)

    private fun getIntParameter(paramName: String, defaultValue: Int, params: Map<String, String>?): Int {
        return params?.get(paramName)?.toIntOrNull() ?: TeamCityProperties.getInteger(paramName, defaultValue)
    }

     fun getRetrier(
         retrierPurpose: String,
         additionalListeners: List<RetrierEventListener> = emptyList(),
         params: Map<String, String>? = null
    ): Retrier {
        val maxAttempts = getIntParameter(MAX_ATTEMPTS_PARAM, 5, params)
        val retryDelayMillis = getIntParameter(RETRY_DELAY_PARAM, 200, params)

         val timeoutExceptionListener =
            object : ClientExceptionListener<ConnectTimeoutException>(ConnectTimeoutException::class) {
                override fun isNonRecoverableKubernetesException(exception: ConnectTimeoutException): Boolean = false
            }

        val loggerRetrierListener = object : RetrierEventListener {
            override fun <T : Any?> onFailure(callable: Callable<T?>, attempt: Int, e: Exception) {
                LOG.warn("Attempt $attempt for $retrierPurpose has failed, retrier is retrying execution. Error: $e")
            }
        }
        val retrier = Retrier.withRetries(
            maxAttempts - 1,
            Retrier.DelayStrategy.exponentialBackoff(retryDelayMillis),
        )
        retrier.registerListener(SpringHttpErrorCodeListener())
        retrier.registerListener(timeoutExceptionListener)
        retrier.registerListener(loggerRetrierListener)

        for (listener in additionalListeners) {
            retrier.registerListener(listener)
        }
        return retrier
    }
}
