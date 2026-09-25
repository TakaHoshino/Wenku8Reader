package com.hoshino.wenku8reader.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 签名查询 flags 的回归测试。
 *
 * 断言看着只是两个 bit，但它是 2026-09 一个真实故障的防线：Android 9/10 的
 * `PackageManager.getPackageArchiveInfo()` 只在 flags 含 `GET_SIGNATURES` 时才收集证书，
 * 漏掉这个已废弃的 bit 会让「安装前签名校验」在这两代系统上**恒定失败**——用户永远装不上
 * 更新，而且报的是「签名校验失败」，极易被误判成"更新包被篡改"。
 *
 * 用数值字面量而不是 `PackageManager.GET_*`：本地单元测试里 android.jar 只是空壳，
 * 而这里只需要钉住这两个 bit 的含义（值来自 AOSP 的 `PackageManager`）。
 */
class UpdateCheckerSignatureFlagsTest {

    @Test
    fun `签名查询 flags 同时包含两代签名方案`() {
        val flags = UpdateChecker.SIGNATURE_QUERY_FLAGS

        // GET_SIGNATURES = 0x40：已废弃，但 Android 9/10 只认它作为"收集证书"的开关
        assertTrue(
            "缺少 GET_SIGNATURES(0x40)：Android 9/10 上证书不会被收集，校验会恒定失败",
            flags and 0x40 != 0,
        )
        // GET_SIGNING_CERTIFICATES = 0x08000000：API 28+ 的现代接口，各版本都认
        assertTrue(
            "缺少 GET_SIGNING_CERTIFICATES(0x08000000)",
            flags and 0x08000000 != 0,
        )
    }
}
