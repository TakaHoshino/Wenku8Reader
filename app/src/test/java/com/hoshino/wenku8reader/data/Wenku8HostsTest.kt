package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 图片地址归一化的回归测试。
 *
 * 背景：站点页面给出的封面/插图是**明文 http** 地址
 * （实测 `https://www.wenku8.cc/book/1191.htm` → `http://img.wenku8.com/image/1/1191/1191s.jpg`），
 * 而应用按安全评估收紧了 `networkSecurityConfig`（默认禁止明文流量）。
 * 曾经因为把 http 地址原样透传给 Coil，导致 Android 拦截请求、**封面全部加载失败**。
 * 这里锁定"图片域名必须升级为 https、其它地址不得被改写"的行为。
 */
class Wenku8HostsTest {

    @Test
    fun upgradesHttpImageHostsToHttps() {
        assertEquals(
            "https://img.wenku8.com/image/1/1191/1191s.jpg",
            Wenku8Hosts.normalizeImageUrl("http://img.wenku8.com/image/1/1191/1191s.jpg"),
        )
        assertEquals(
            "https://pic.777743.xyz/1/1191/177419/218990.jpg",
            Wenku8Hosts.normalizeImageUrl("http://pic.777743.xyz/1/1191/177419/218990.jpg"),
        )
    }

    @Test
    fun keepsAlreadyHttpsUrlsUnchanged() {
        val https = "https://img.wenku8.com/image/1/1191/1191s.jpg"
        assertEquals(https, Wenku8Hosts.normalizeImageUrl(https))
    }

    @Test
    fun doesNotRewriteThirdPartyHttpHosts() {
        // 不擅自改写非本站图片地址（避免把用户/第三方的 http 资源指到错误位置）
        val other = "http://example.com/a.jpg"
        assertEquals(other, Wenku8Hosts.normalizeImageUrl(other))
    }

    @Test
    fun leavesRelativeAndEmptyValuesAlone() {
        // 空白输入被裁剪为空串（结果都会交给 Coil，裁剪是防御性的）
        assertEquals("", Wenku8Hosts.normalizeImageUrl(""))
        assertEquals("", Wenku8Hosts.normalizeImageUrl("   "))
        // 相对路径与 data: 地址不属于 http 图片域名，原样返回
        assertEquals("/image/1/2/2s.jpg", Wenku8Hosts.normalizeImageUrl("/image/1/2/2s.jpg"))
        assertEquals("data:image/png;base64,AAA", Wenku8Hosts.normalizeImageUrl("data:image/png;base64,AAA"))
    }

    @Test
    fun doesNotMatchHostByPrefixOrSuffix() {
        // 仅精确匹配图片域名，避免 img.wenku8.com.evil.com 之类的相似域名被一起升级
        val lookalike = "http://img.wenku8.com.evil.com/a.jpg"
        assertEquals(lookalike, Wenku8Hosts.normalizeImageUrl(lookalike))
        val notImage = "http://app.wenku8.com/android.php"
        assertEquals(notImage, Wenku8Hosts.normalizeImageUrl(notImage))
    }
}
