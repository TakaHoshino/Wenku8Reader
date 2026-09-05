package com.hoshino.wenku8reader.data.local

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * URL → HTML 的本地磁盘缓存（`filesDir/html_cache`，卸载前持久存在）。
 * 用途：书籍详情/目录/章节正文/首页/标签书单抓取后落盘，二次打开直接读本地，避免重复网络加载。
 *
 * - TTL 由调用方按内容类型传入（如章节 30 天、首页 1 小时），以文件修改时间判断；
 * - 文件名格式 `{category}_{md5}.html`（如 `book_a1b2c3d4.html`），便于按类型分组统计/清理；
 *   旧格式（无前缀 `{md5}.html`）读取时兼容，重新写入时自动迁移为新格式；
 * - 总大小超过上限时按「最旧优先」删除（LRU 简化版），上限可通过 [setMaxBytes] 动态调整。
 */
class HtmlDiskCache(
    context: Context,
    private var maxBytes: Long = 30L * 1024 * 1024,
) {
    private val dir = File(context.filesDir, "html_cache").apply { mkdirs() }

    /** 命中且未过期返回缓存内容；过期自动删除并返回 null。 */
    @Synchronized
    fun get(url: String, ttlMs: Long): String? {
        val f = fileFor(url) ?: return null
        if (System.currentTimeMillis() - f.lastModified() > ttlMs) {
            f.delete()
            return null
        }
        return runCatching { f.readText() }.getOrNull()
    }

    /** 写入缓存；[category] 决定文件名前缀（用于分组统计与清理）。 */
    @Synchronized
    fun put(url: String, html: String, category: String = "other") {
        if (html.isBlank()) return
        runCatching {
            val md5 = md5(url)
            // 旧格式同名文件迁移：删除后以新格式写入
            val legacy = File(dir, "$md5.html")
            if (legacy.exists()) legacy.delete()
            File(dir, "${sanitize(category)}_$md5.html").writeText(html)
            evictIfNeeded()
        }
    }

    /** 清理缓存：[category] = null 清全部；否则只清该类型（按文件名前缀匹配）。 */
    @Synchronized
    fun clear(category: String? = null) {
        val files = dir.listFiles() ?: return
        if (category == null) {
            files.forEach { it.delete() }
        } else {
            val prefix = "${sanitize(category)}_"
            files.filter { it.name.startsWith(prefix) }.forEach { it.delete() }
        }
    }

    /** 按类型分组统计大小（字节）。key = 文件名前缀；旧格式无前缀文件归入 "legacy"。 */
    @Synchronized
    fun sizeByCategory(): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        dir.listFiles()?.forEach { f ->
            val cat = f.name.substringBefore('_', "legacy").takeIf { f.name.contains('_') } ?: "legacy"
            result[cat] = (result[cat] ?: 0L) + f.length()
        }
        return result
    }

    /** 缓存总大小（字节）。 */
    @Synchronized
    fun totalSize(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** 动态调整上限（字节）；超限立即收缩。 */
    @Synchronized
    fun setMaxBytes(bytes: Long) {
        maxBytes = bytes.coerceAtLeast(0L)
        evictIfNeeded()
    }

    private fun fileFor(url: String): File? {
        val md5 = md5(url)
        // 新格式（category_md5.html）优先；找不到再查旧格式（md5.html），兼容存量缓存
        val legacy = File(dir, "$md5.html")
        if (legacy.exists()) return legacy
        return dir.listFiles { _, name -> name.endsWith("_$md5.html") }?.firstOrNull()
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

    private fun md5(url: String): String =
        MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** category 只保留安全字符，避免路径穿越/非法文件名。 */
    private fun sanitize(category: String): String =
        category.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { "other" }
}
