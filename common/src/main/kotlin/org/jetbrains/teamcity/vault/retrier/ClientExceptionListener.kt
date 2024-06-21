package org.jetbrains.teamcity.vault.retrier

import jetbrains.buildServer.util.ExceptionUtil
import kotlin.reflect.KClass

abstract class ClientExceptionListener<T : Throwable>(val type: KClass<out T>) : Retrier.ExceptionListener {
    abstract fun isNonRecoverableKubernetesException(exception: T): Boolean

    override fun isNonRecoverable(exception: Throwable): Boolean {
        val cause = ExceptionUtil.getCause(exception, type.java)
        return if (cause != null) {
            isNonRecoverableKubernetesException(cause)
        } else {
            false
        }
    }
}