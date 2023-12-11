package org.jetbrains.teamcity.vault.retrier

import assertk.assertions.isEqualTo
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.serverSide.TeamCityProperties
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

@Test
class RetrierTest : BaseTestCase(){
    private val mockResponseListener = object : Retrier.ResponseErrorListener<Int> {
        override fun isError(response: Int): Boolean = when (response) {
            in 500..599 -> true
            else -> false
        }
    }
    private val mockExceptionListener = object : Retrier.ExceptionListener {
        override fun isNonRecoverable(exception: Throwable): Boolean =
            if (exception is IllegalStateException) {
                false
            } else {
                true
            }

    }

    private val retrier: Retrier<Int> = Retrier(exceptionListeners = listOf(mockExceptionListener), responseListeners = listOf(mockResponseListener))

    @BeforeMethod
    override fun setUp() {
        super.setUp()
        TeamCityProperties.getModel().storeDefaultValue(Retrier.RETRY_DELAY, "0")
    }

    private fun getMultipleResponses(responseGetters: List<() -> Int>) : Int? {
        var currentGetter = 0
        return retrier.run {
            responseGetters[currentGetter++].invoke()
        }
    }

    private fun getRetryableError() = {
        throw IllegalStateException()
    }

    private fun getNonRetryableError() = {
        throw RuntimeException()
    }

    private fun getSuccessReturn() = {
        SUCCESS
    }

    private fun getErrorReturn() = {
        ERROR
    }

    fun test() {
        val response = retrier.run(getSuccessReturn())

        assertk.assertThat(response).isEqualTo(SUCCESS)
    }

    fun `test error response is returned`() {
        val response = retrier.run(getErrorReturn())

        assertk.assertThat(response).isEqualTo(ERROR)
    }

    @Test(expectedExceptions = [RuntimeException::class])
    fun `test throw exception is thrown`(){
        retrier.run(getNonRetryableError())
    }

    @Test(expectedExceptions = [RuntimeException::class])
    fun `test throw retryable exception is thrown`(){
        retrier.run(getRetryableError())
    }

    fun `test throw retryable exception with later successful return will work`(){
        val response = getMultipleResponses(listOf(getRetryableError(), getSuccessReturn()))
        assertk.assertThat(response).isEqualTo(200)
    }

    @Test(expectedExceptions = [RuntimeException::class])
    fun `test throw non retryable exception with later successful return will fail`(){
        getMultipleResponses(listOf(getNonRetryableError(), getSuccessReturn()))
    }

    fun `test throw retryable exception followed by error response with later successful return will work`(){
        val response = getMultipleResponses(listOf(getRetryableError(), getErrorReturn(), getSuccessReturn()))
        assertk.assertThat(response).isEqualTo(200)
    }


    companion object {
        const val SUCCESS = 200
        const val ERROR = 500
    }
}