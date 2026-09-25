package com.hoshino.wenku8reader.data.local

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * URL → HTML 的本地磁盘缓存（`filesDir/html_cache`，卸载前持久存在）。
 * 用途：书籍详情/目录/章节正文/首页/标签书单抓取后落盘，二次打开直接读本地，避免重复网络加载。
 *
 * - TTL 由调用方按内容类型传入（如章节 30 天、首页 1 小时），以文件修改时间判断；
 * - 文件名格式 `{category}_{md5}.html`（如 `book_a1b2c3d4.html`），便于按类型分组统计/清理；
 *   旧格式（无前缀 `{md5}.html`）读取时兼容，重新写入时自动迁移为新格式；
 * - 总大小超过上限时按「最旧优先」删除（LRU 简化版），上限可通过 [setMaxBytes] 动态调整。
 */
class HtmlDiskCache internal constructor(
    private val dir: File,
    initialMaxBytes: Long,
) {
    /**
     * 生产入口：缓存目录固定为 `filesDir/html_cache`。
     *
     * 主构造改成接收 [File] 只是为了可测——淘汰/迁移/分组统计这些逻辑最容易出错，
     * 却完全依赖文件系统，只有能把目录换成临时目录才写得出单测。
     */
    constructor(
        context: Context,
        initialMaxBytes: Long = DEFAULT_MAX_BYTES,
    ) : this(File(context.filesDir, DIR_NAME), initialMaxBytes)

    init {
        dir.mkdirs()
    }

    /**
     * 缓存目录（`filesDir/html_cache`）。
     *
     * 对外暴露只读引用，供存储占用统计复用同一条路径——统计与清理必须指向同一个目录，
     * 各自拼一遍路径字符串迟早会漂移。
     */
    val directory: File get() = dir

    /** 缓存目录总大小（含子目录，正常情况下没有子目录，防御性处理）。 */
    fun totalSize(): Long = lock.read {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 读写锁（替代原先的 `@Synchronized` 互斥锁）。
     *
     * 缓存读多写少，且读写本身都要做磁盘 I/O；用互斥锁会把**所有读取串行化**，
     * 连续加载章节时彼此排队。改为读锁后并发读可并行，只在写入/清理/淘汰时独占。
     * （磁盘 I/O 仍留在锁内：删除与读写操作必须互斥，否则可能读到写了一半的文件。）
     */
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var maxBytes: Long = initialMaxBytes

    /** 命中且未过期返回缓存内容；过期自动删除并返回 null。 */
    fun get(url: String, ttlMs: Long, category: String = "other"): String? {
        val f = fileFor(url, category)
        lock.read {
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() <= ttlMs) {
                return runCatching { f.readText() }.getOrNull()
            }
        }
        // 已过期：删除属写操作，须在写锁内独占进行
        lock.write { runCatching { f.delete() } }
        return null
    }

    /** 写入缓存；[category] 决定文件名前缀（用于分组统计与清理）。 */
    fun put(url: String, html: String, category: String = "other") {
        if (html.isBlank()) return
        val md5 = md5(url)
        val legacy = File(dir, "$md5.html")
        val target = File(dir, "${sanitize(category)}_$md5.html")
        lock.write {
            runCatching {
                // 旧格式同名文件迁移：删除后以新格式写入
                if (legacy.exists()) legacy.delete()
                target.writeText(html)
            }
            evictIfNeeded()
        }
    }

    /** 清理缓存：[category] = null 清全部；否则只清该类型（按文件名前缀匹配）。 */
    fun clear(category: String? = null) {
        lock.write {
            val files = dir.listFiles() ?: return@write
            if (category == null) {
                files.forEach { it.delete() }
            } else {
                val prefix = "${sanitize(category)}_"
                files.filter { it.name.startsWith(prefix) }.forEach { it.delete() }
            }
        }
    }

    /**
     * 按类型分组统计大小（字节）。key = 文件名前缀；旧格式无前缀文件归入 [LEGACY_CATEGORY]。
     *
     * 用 `substringBeforeLast('_')` 取类别：文件名形如 `{category}_{md5}.html`，
     * 而下划线后只可能是 md5（十六进制），因此最后一个下划线就是类别边界。
     */
    fun sizeByCategory(): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        lock.read {
            dir.listFiles()?.forEach { f ->
                val cat = if ('_' in f.name) f.name.substringBeforeLast('_') else LEGACY_CATEGORY
                result[cat] = (result[cat] ?: 0L) + f.length()
            }
        }
        return result
    }

    /** 动态调整上限（字节）；超限立即收缩。 */
    fun setMaxBytes(bytes: Long) {
        maxBytes = bytes.coerceAtLeast(0L)
        lock.write { evictIfNeeded() }
    }

    /**
     * 按确定性文件名定位缓存文件。
     *
     * 新格式的 category 在写入时已知、读取时由调用方传入，因此可直接拼出文件名，
     * 无需像原实现那样对整个目录做一次 `listFiles` 过滤扫描。
     * 旧格式（无前缀 `{md5}.html`）仅做一次存在性探测以兼容存量缓存。
     */
    private fun fileFor(url: String, category: String): File {
        val md5 = md5(url)
        val legacy = File(dir, "$md5.html")
        if (legacy.exists()) return legacy
        return File(dir, "${sanitize(category)}_$md5.html")
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

    private companion object {
        /** 缓存目录名（`filesDir/html_cache`）。 */
        const val DIR_NAME = "html_cache"

        /** 旧格式（无 category 前缀）缓存的归类名。 */
        const val LEGACY_CATEGORY = "legacy"

        /** 默认上限 30MB（可在设置页动态调整）。 */
        const val DEFAULT_MAX_BYTES = 30L * 1024 * 1024
    }
}
