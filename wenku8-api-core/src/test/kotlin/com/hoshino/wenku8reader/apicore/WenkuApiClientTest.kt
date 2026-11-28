package com.hoshino.wenku8reader.apicore

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WenkuApiClientTest {
    @Test
    fun `XML 响应交给调用方解析`() = runBlocking {
        val client = WenkuApiClient(FakeTransport(RelayPostResult.Success("<ok/>".toByteArray())))
        val result = client.fetchXmlResponse("action=test") { xml -> xml }

        assertEquals(WenkuApiResult.Success("<ok/>"), result)
    }

    @Test
    fun `短数字响应转换为业务错误码`() = runBlocking {
        val client = WenkuApiClient(FakeTransport(RelayPostResult.Success("4".toByteArray())))

        assertEquals(
            WenkuApiResult.ServerErrorCode(4),
            client.fetchXmlResponse("action=test") { it },
        )
    }

    @Test
    fun `正文空响应分类为 EmptyResponse`() = runBlocking {
        val client = WenkuApiClient(FakeTransport(RelayPostResult.Success(ByteArray(0))))

        assertEquals(
            WenkuApiResult.EmptyResponse,
            client.fetchTextResponse("action=test"),
        )
    }

    @Test
    fun `数字 1 表示修改成功`() = runBlocking {
        val client = WenkuApiClient(FakeTransport(RelayPostResult.Success("1".toByteArray())))

        assertEquals(
            WenkuApiResult.Success(Unit),
            client.executeNumericMutation("action=test"),
        )
    }

    @Test
    fun `未配置封包器时不会发网络请求`() = runBlocking {
        val client = WenkuApiClient(FakeTransport(RelayPostResult.NotConfigured))
        val result = client.fetchXmlResponse("action=test") { it }

        assertTrue(result is WenkuApiResult.ApiNotConfigured)
    }

    private class FakeTransport(
        private val result: RelayPostResult,
    ) : WenkuActionTransport {
        override suspend fun postSealedAction(
            rawActionQuery: String,
            isIdempotentReadOnly: Boolean,
        ): RelayPostResult = result
    }
}
