package org.jetbrains.teamcity.vault.retrier

import org.jetbrains.teamcity.vault.retrier.Retrier
import jetbrains.buildServer.util.HTTPRequestBuilder.Response
import kotlin.reflect.KClass

class TeamCityHttpCodeListener : Retrier.ResponseErrorListener<Response> {
    override fun isError(response: Response) = when(response.statusCode){
        in 400..499 -> true
        in 500..599 -> false
        else -> true
    }
}