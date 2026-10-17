package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UA 必须是"真实设备 + 固定版本"：随机拼出来的 UA 是 Cloudflare 眼里的自动化特征。
 */
class UserAgentTest {

    @Test
    fun `使用真实设备信息_固定 WebKit 与 Safari 版本`() {
        val ua = UserAgent.build("17", "2211133C")
        assertEquals(
            "Mozilla/5.0 (Linux; Android 17; 2211133C) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36",
            ua,
        )
        // 真实 UA 里这两个版本是固定的 537.36（旧实现随机成 537.<0..150>）
        assertTrue(ua.contains("AppleWebKit/537.36"))
        assertTrue(ua.endsWith("Mobile Safari/537.36"))
    }

    @Test
    fun `空设备信息退化为 Chrome 缩减 UA 的写法`() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36",
            UserAgent.build("", "   "),
        )
    }

    @Test
    fun `相同输入始终得到相同结果_不再随机`() {
        assertEquals(
            UserAgent.build("14", "Pixel 8"),
            UserAgent.build("14", "Pixel 8"),
        )
    }

    @Test
    fun `Chrome 主版本可注入_但结构固定`() {
        val ua = UserAgent.build("13", "SM-S911B", chromeMajor = 140)
        assertTrue(ua.contains("Chrome/140.0.0.0 Mobile Safari/537.36"))
    }
}
