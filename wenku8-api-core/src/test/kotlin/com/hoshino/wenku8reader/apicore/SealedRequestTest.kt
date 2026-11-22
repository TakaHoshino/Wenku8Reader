package com.hoshino.wenku8reader.apicore

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SealedRequestTest {
    @Test
    fun `请求体和头在构造时被防御性复制`() {
        val body = byteArrayOf(1, 2, 3)
        val headers = mutableMapOf("X-WK8-Proto" to "2")
        val request = SealedRequest(
            endpointUrl = "https://example.test/v2/api",
            contentType = "application/octet-stream",
            headers = headers,
            body = body,
        )

        body[0] = 9
        headers["X-WK8-Proto"] = "1"

        assertArrayEquals(byteArrayOf(1, 2, 3), request.body)
        assertEquals("2", request.headers["X-WK8-Proto"])
    }
}
