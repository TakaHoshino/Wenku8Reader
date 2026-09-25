package com.hoshino.wenku8reader.data

// TTL + LRU 内存缓存：从 Wenku8Client.kt 拆出（纯数据结构，与网络无关）。

/**
 * 简单 TTL + LRU 内存缓存（参考 LightNovelReader 的 Cache）。
 * 只缓存成功结果；超时后下次访问自动重取。
 *
 * 容量上限说明：原实现只有 TTL、没有容量上限——用户长读一本书时
 * `chapterCache`（30 分钟 TTL）会把全部已读章节正文（每章数十 KB）
 * 一直累积在内存里。这里补上 LRU 上限（按访问顺序淘汰最久未用），
 * 把内存占用钳制在可预期范围内。
 */
internal class TimedCache(
    private val ttlMs: Long,
    private val maxEntries: Int,
) {
    /** accessOrder = true：读取即刷新 LRU 顺序，供 [removeEldestEntry] 淘汰最久未用项。 */
    private val map = object : LinkedHashMap<String, Pair<Long, Any>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Long, Any>>,
        ): Boolean = size > maxEntries
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> get(key: String): T? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.first > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.second as T
    }

    @Synchronized
    fun put(key: String, value: Any) {
        map[key] = System.currentTimeMillis() to value
    }

    @Synchronized
    fun clear() = map.clear()
}
