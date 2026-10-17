package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 测试版通道「怎么挑最新一条发布」的单测。
 *
 * 背景（2026-09-25 实测）：GitHub 的 `releases` 列表**不保证**按时间倒序返回——最新发布的
 * `v0.8.0-dev.100` 被排在了 `dev.94` 之后（id 与 created_at 都是最新，位置却是第 5；
 * 新建的探针 release 也落到第 12 位）。所以实现不能"取第一条带 APK 的"，
 * 否则会把旧包当成最新，用户会一直收到已经过期好几个版本的更新提示。
 *
 * 这里把顺序、缺失字段、无 APK 三种情况钉死（用与线上同构的 JSON 片段）。
 */
class UpdateCheckerPickLatestTest {

    private fun release(tag: String, publishedAt: String?, hasApk: Boolean = true): String {
        val published = if (publishedAt == null) "" else ""","published_at":"$publishedAt""""
        val assets = if (hasApk) {
            """[{"name":"app-release.apk","browser_download_url":"https://example.com/$tag.apk"}]"""
        } else {
            """[{"name":"SHA256SUMS","browser_download_url":"https://example.com/$tag.txt"}]"""
        }
        return """{"tag_name":"$tag"$published,"assets":$assets}"""
    }

    @Test
    fun `接口把最新一条排在中间时仍取到最新的一条`() {
        // 顺序与线上实测一致：最新的 dev.100 排在 dev.94 之后
        val json = """
            [
              ${release("v0.8.0-dev.99", "2026-09-25T09:56:16Z")},
              ${release("v0.8.0-dev.98", "2026-09-25T09:39:51Z")},
              ${release("v0.8.0-dev.97", "2026-09-25T09:19:11Z")},
              ${release("v0.8.0-dev.94", "2026-09-25T09:03:28Z")},
              ${release("v0.8.0-dev.100", "2026-09-25T11:10:05Z")}
            ]
        """.trimIndent()

        assertEquals("v0.8.0-dev.100", pickLatestReleaseWithApk(json)?.optString("tag_name"))
    }

    @Test
    fun `跳过没有 APK 的发布`() {
        val json = """
            [
              ${release("v0.9.0", "2026-09-26T00:00:00Z", hasApk = false)},
              ${release("v0.8.0-dev.100", "2026-09-25T11:10:05Z")}
            ]
        """.trimIndent()

        assertEquals("v0.8.0-dev.100", pickLatestReleaseWithApk(json)?.optString("tag_name"))
    }

    @Test
    fun `缺 published_at 的条目排在最后`() {
        val json = """
            [
              ${release("v0.8.0-dev.100", null)},
              ${release("v0.8.0-dev.99", "2026-09-25T09:56:16Z")}
            ]
        """.trimIndent()

        assertEquals("v0.8.0-dev.99", pickLatestReleaseWithApk(json)?.optString("tag_name"))
    }

    @Test
    fun `空列表与坏 JSON 都返回 null`() {
        assertNull(pickLatestReleaseWithApk("[]"))
        assertNull(pickLatestReleaseWithApk("{不是数组"))
    }
}
