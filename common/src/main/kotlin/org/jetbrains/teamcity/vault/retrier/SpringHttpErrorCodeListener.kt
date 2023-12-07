package org.jetbrains.teamcity.vault.retrier

import org.springframework.web.client.HttpStatusCodeException

class SpringHttpErrorCodeListener : ClientExceptionListener<HttpStatusCodeException>(type = HttpStatusCodeException::class) {
    override fun isNonRecoverableKubernetesException(exception: HttpStatusCodeException): Boolean = when {
        exception.statusCode.is4xxClientError -> true
        exception.statusCode.is5xxServerError -> false
        else -> true
    }
}
