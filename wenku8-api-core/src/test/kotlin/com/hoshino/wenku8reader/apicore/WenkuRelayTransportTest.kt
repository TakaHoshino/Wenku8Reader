package com.hoshino.wenku8reader.apicore

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WenkuRelayTransportTest {
    @Test
    fun `提取 PHPSESSID 并忽略其他 Cookie 属性`() {
        assertEquals(
            "abc123",
            WenkuRelayTransport.extractPhpSessionId(
                listOf("path=/; PHPSESSID=abc123; HttpOnly; Secure")
            ),
        )
        assertNull(WenkuRelayTransport.extractPhpSessionId(listOf("foo=bar")))
    }

    @Test
    fun `解析 CLOCK_SKEW 服务端时间`() {
        assertEquals(
            1_765_000_000_000L,
            WenkuRelayTransport.parseClockSkewServerTimeMs(
                """{"error":"CLOCK_SKEW","serverTimeMs":1765000000000}"""
            ),
        )
    }

    @Test
    fun `解析 426 升级字段`() {
        assertEquals(
            105 to "2.0.5",
            WenkuRelayTransport.parseUpgradeRequiredPayload(
                """{"minVersionCode":105,"minVersionName":"2.0.5"}"""
            ),
        )
    }

    @Test
    fun `封包 POST 使用精确头体和会话 Cookie`() = runBlocking {
        val captured = mutableListOf<WenkuHttpRequest>()
        val executor = WenkuHttpExecutor { request ->
            captured += request
            WenkuHttpResponse(
                url = request.url,
                statusCode = 200,
                headers = emptyList(),
                body = "ok".toByteArray(),
            )
        }
        val transport = WenkuRelayTransport(
            signer = fixedSigner(),
            httpExecutor = executor,
            sessionCookieStore = InMemorySessionCookieStore().apply {
                setPhpSessionId("session-1")
            },
        )

        val result = transport.postSealedAction("action=test")

        assertEquals(RelayPostResult.Success("ok".toByteArray()), result)
        assertEquals(1, captured.size)
        assertEquals("POST", captured.single().method)
        assertEquals("gzip", captured.single().headers["Accept-Encoding"])
        assertEquals("2", captured.single().headers["X-WK8-Proto"])
        assertEquals("PHPSESSID=session-1", captured.single().headers["Cookie"])
        assertEquals("application/octet-stream", captured.single().headers["Content-Type"])
        assertEquals(false, captured.single().useCookies)
        assertEquals(0, captured.single().maxRedirects)
    }

    @Test
    fun `401 CLOCK_SKEW 更新偏移并使用新封包重试`() = runBlocking {
        var sealCount = 0
        var offset = 0L
        val responses = ArrayDeque(
            listOf(
                WenkuHttpResponse(
                    "https://mewx-android.wenku8.app/v2/api",
                    401,
                    emptyList(),
                    """{"error":"CLOCK_SKEW","serverTimeMs":123456}""".toByteArray(),
                ),
                WenkuHttpResponse(
                    "https://mewx-android.wenku8.app/v2/api",
                    200,
                    emptyList(),
                    "retried".toByteArray(),
                ),
            )
        )
        val transport = WenkuRelayTransport(
            signer = WenkuEnvelopeSigner { action, cookie ->
                sealCount++
                SealedRequest(
                    "https://mewx-android.wenku8.app/v2/api",
                    "application/octet-stream",
                    mapOf(
                        "X-WK8-Proto" to "2",
                        "X-Attempt" to sealCount.toString(),
                    ),
                    "$action:$sealCount".toByteArray(),
                )
            },
            httpExecutor = WenkuHttpExecutor { responses.removeFirst() },
            serverClockOffsetUpdater = { offset = it },
        )

        val result = transport.postSealedAction("action=test")

        assertEquals(RelayPostResult.Success("retried".toByteArray()), result)
        assertEquals(2, sealCount)
        assertEquals(123456L, offset)
    }

    @Test
    fun `403 426 429 被明确分类`() = runBlocking {
        suspend fun resultFor(code: Int): RelayPostResult {
            val transport = WenkuRelayTransport(
                signer = fixedSigner(),
                httpExecutor = WenkuHttpExecutor {
                    WenkuHttpResponse(
                        it.url,
                        code,
                        emptyList(),
                        """{"minVersionCode":105,"minVersionName":"2.0.5"}""".toByteArray(),
                    )
                },
            )
            return transport.postSealedAction("action=test")
        }

        assertEquals(RelayPostResult.AuthRejected, resultFor(403))
        assertEquals(RelayPostResult.RateLimited, resultFor(429))
        assertEquals(
            RelayPostResult.UpgradeRequired(105, "2.0.5"),
            resultFor(426),
        )
    }

    @Test
    fun `未配置签名器时不调用网络执行器`() = runBlocking {
        var called = false
        val transport = WenkuRelayTransport(
            signer = UnavailableEnvelopeSigner,
            httpExecutor = WenkuHttpExecutor {
                called = true
                null
            },
        )

        assertEquals(
            RelayPostResult.NotConfigured,
            transport.postSealedAction("action=test"),
        )
        assertTrue(called.not())
    }

    private fun fixedSigner() = WenkuEnvelopeSigner { action, _ ->
        SealedRequest(
            endpointUrl = "https://mewx-android.wenku8.app/v2/api",
            contentType = "application/octet-stream",
            headers = mapOf("X-WK8-Proto" to "2"),
            body = action.toByteArray(),
        )
    }
}
