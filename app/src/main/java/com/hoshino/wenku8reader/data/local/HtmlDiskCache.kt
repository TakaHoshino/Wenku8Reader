package com.hoshino.wenku8reader.data.local

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * URL → HTML 的本地磁盘缓存（`filesDir/html_cache`，卸载前持久存在）。
 * 用途：书籍详情/目录/章节正文/首页/标签书单抓取后落盘，二次打开直接读本地，避免重复网络加载。
 *
 * - TTL 由调用方按内容类型传入（如章节 30 天、首页 1 小时），以文件修改时间判断；
 * - 总大小超过上限时按「最旧优先」删除（LRU 简化版），防止无限增长。
 */
class HtmlDiskCache(
    context: Context,
    private val maxBytes: Long = 30L * 1024 * 1024,
) {
    private val dir = File(context.filesDir, "html_cache").apply { mkdirs() }

    /** 命中且未过期返回缓存内容；过期自动删除并返回 null。 */
    @Synchronized
    fun get(url: String, ttlMs: Long): String? {
        val f = fileFor(url)
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > ttlMs) {
            f.delete()
            return null
        }
        return runCatching { f.readText() }.getOrNull()
    }

    @Synchronized
    fun put(url: String, html: String) {
        if (html.isBlank()) return
        runCatching {
            fileFor(url).writeText(html)
            evictIfNeeded()
        }
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(url: String): File {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$md5.html")
    }

    private fun evictIfNeeded() {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        // 超限：从最旧开始删，直到降到上限的 70%
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= maxBytes * 0.7) return@forEach
            total -= f.length()
            f.delete()
        }
    }
}
