package com.hoshino.wenku8reader.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志脱敏：日志会被导出并交给别人看，所以"密码 / 令牌不能出现在其中"必须由单测锁住。
 */
class LogSanitizerTest {

    @Test
    fun `敏感查询参数一律打码_普通参数保留`() {
        val url = "https://www.wenku8.cc/modules/article/bookcase.php?password=p%40ss&delid=123&token=abc"
        val sanitized = LogSanitizer.sanitizeUrl(url)
        assertFalse(sanitized.contains("p%40ss"))
        assertFalse(sanitized.contains("abc"))
        assertTrue(sanitized.contains("password=***"))
        assertTrue(sanitized.contains("token=***"))
        // 业务流程需要的 id 必须留着，否则日志失去诊断价值
        assertTrue(sanitized.contains("delid=123"))
    }

    @Test
    fun `参数名大小写不敏感`() {
        val sanitized = LogSanitizer.sanitizeUrl("https://x/y?PASSWORD=secret&Session=s1")
        assertTrue(sanitized.contains("PASSWORD=***"))
        assertTrue(sanitized.contains("Session=***"))
    }

    @Test
    fun `URL 里的 userinfo 被打码`() {
        assertEquals(
            "https://***@example.com/a?b=1",
            LogSanitizer.sanitizeUrl("https://user:secret@example.com/a?b=1"),
        )
    }

    @Test
    fun `fragment 被丢弃`() {
        assertEquals(
            "https://example.com/a?b=1",
            LogSanitizer.sanitizeUrl("https://example.com/a?b=1#access_token=leak"),
        )
    }

    @Test
    fun `超长 URL 截断并带省略号`() {
        val long = "https://example.com/" + "a".repeat(600)
        val sanitized = LogSanitizer.sanitizeUrl(long)
        assertEquals(400, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
    }

    @Test
    fun `消息单行化并合并空白`() {
        assertEquals(
            "java.io.IOException: timeout at line 3",
            LogSanitizer.sanitizeMessage("java.io.IOException: timeout\n\tat line 3"),
        )
        assertEquals("a b", LogSanitizer.sanitizeMessage("  a   b  "))
    }

    @Test
    fun `超长消息截断`() {
        val sanitized = LogSanitizer.sanitizeMessage("x".repeat(800))
        assertEquals(500, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
    }

    @Test
    fun `空 URL 与空消息不抛异常`() {
        assertEquals("", LogSanitizer.sanitizeUrl("   "))
        assertEquals("", LogSanitizer.sanitizeMessage("\n\t"))
    }
}
