package com.hoshino.wenku8reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重试策略：403 必须快速失败（Cloudflare 硬拦截），429/5xx 才退避重试。
 */
class HttpRetryPolicyTest {

    @Test
    fun `403 不重试`() {
        assertFalse(HttpRetryPolicy.shouldRetry(403))
        assertFalse(HttpRetryPolicy.shouldRetry(200))
        assertFalse(HttpRetryPolicy.shouldRetry(404))
    }

    @Test
    fun `限流与临时故障仍然重试`() {
        listOf(429, 500, 502, 503, 504).forEach { code ->
            assertTrue("HTTP $code 应当重试", HttpRetryPolicy.shouldRetry(code))
        }
    }
}
