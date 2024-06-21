package org.jetbrains.teamcity.vault.retrier

import jetbrains.buildServer.util.ExceptionUtil
import jetbrains.buildServer.util.retry.AbortRetriesException
import jetbrains.buildServer.util.retry.RetrierEventListener
import java.lang.Exception
import java.util.concurrent.Callable
import kotlin.reflect.KClass

abstract class ClientExceptionListener<T : Throwable>(val type: KClass<out T>): RetrierEventListener {
    abstract fun isNonRecoverableKubernetesException(exception: T): Boolean

    override fun <T : Any?> onFailure(callable: Callable<T>, retry: Int, e: Exception) {
        if (isNonRecoverable(e)){
            throw AbortRetriesException(e)
        }
    }

    private fun isNonRecoverable(exception: Throwable): Boolean {
        val cause = ExceptionUtil.getCause(exception, type.java)
        return if (cause != null) {
            isNonRecoverableKubernetesException(cause)
        } else {
            false
        }
    }
}