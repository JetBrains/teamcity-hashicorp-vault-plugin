package org.jetbrains.teamcity.vault.retrier

import assertk.Assert
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.util.retry.Retrier
import org.apache.http.conn.ConnectTimeoutException
import org.assertj.core.api.Assertions
import org.springframework.vault.authentication.VaultLoginException
import org.springframework.web.client.ResourceAccessException
import org.testng.Assert.*
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch

@Test
class VaultRetrierTest : BaseTestCase() {
    private lateinit var client: Retrier

    @BeforeMethod
    override fun setUp() {
        super.setUp()
        client = VaultRetrier.getRetrier("test")
    }


    @Test
    fun testWithConnectTimeoutException() {
        val exception = ConnectTimeoutException("Connection refused: connect")
        val countDownLatch = CountDownLatch(1)
        client.execute {
            if (countDownLatch.count != 0L) {
                countDownLatch.countDown()
                throw exception
            }
        }

        Assertions.assertThat(countDownLatch.count).isEqualTo(0)
    }


    @Test
    fun testWithNestedCause() {
        val exception = VaultLoginException(
            "vault login esception",
            ResourceAccessException(
                "Resource access exception",
                ConnectTimeoutException(
                    SocketTimeoutException("Socket timeout exception"), null))
            )
        val countDownLatch = CountDownLatch(1)
        client.execute {
            if (countDownLatch.count != 0L) {
                countDownLatch.countDown()
                throw exception
            }
        }

        Assertions.assertThat(countDownLatch.count).isEqualTo(0)
    }
}