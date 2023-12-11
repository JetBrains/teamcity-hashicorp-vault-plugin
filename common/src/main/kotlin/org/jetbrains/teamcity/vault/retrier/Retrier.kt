package org.jetbrains.teamcity.vault.retrier

import jetbrains.buildServer.serverSide.TeamCityProperties

class Retrier<T : Any>(private val exceptionListeners: List<ExceptionListener> = emptyList(), private val responseListeners: List<ResponseErrorListener<T>> = emptyList()) {

    private fun getMaxRetries() = TeamCityProperties.getInteger(MAX_RETRIES, 3)
    private fun getRetryDelay() = TeamCityProperties.getLong(RETRY_DELAY, 3)

    fun run(runnable: () -> T): T? {
        var throwable: Throwable? = null
        var response: T? = null
        for (i in 0 until getMaxRetries()) {
            try {
                response = runnable.invoke()
                if (isResponseError(response)) {
                    retryWait()
                    continue
                }

                return response
            } catch (e: Throwable) {
                if (exceptionListeners.any { it.isNonRecoverable(e) }) {
                    throw e
                }
                throwable = e
                retryWait()
            }
        }

        if (throwable != null){
            throw throwable
        } else {
            return response
        }
    }

    private fun retryWait() {
        Thread.sleep(getRetryDelay() * 1000)
    }

    private fun isResponseError(response: T) =
        responseListeners.any {
            it.isError(response)
        }


    interface ExceptionListener {
        fun isNonRecoverable(exception: Throwable): Boolean
    }

    interface ResponseErrorListener<T: Any> {
        fun isError(response: T): Boolean
    }

    companion object {
        const val MAX_RETRIES = "teamcity.hashicorp.vault.maxRetries"
        const val RETRY_DELAY = "teamcity.hashicorp.vault.retryDelaySeconds"
    }
}