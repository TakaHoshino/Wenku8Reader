package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [TimedCache] 的单测：TTL 过期与 LRU 容量上限。
 *
 * 这是全项目的内存缓存底座（详情信息 / 目录 / 章节正文三处都用它），此前完全没有测试：
 * TTL 写错会让缓存永不失效（占内存）或立刻失效（等于没缓存）；LRU 写错则会让
 * "长读一本书"把整本书的正文常驻内存——正是当初给它加容量上限要解决的问题。
 */
class TimedCacheTest {

    @Test
    fun `命中缓存`() {
        val cache = TimedCache(ttlMs = 10_000, maxEntries = 8)
        cache.put("info_1", "v1")
        assertEquals("v1", cache.get<String>("info_1"))
    }

    @Test
    fun `未写入的 key 返回 null`() {
        val cache = TimedCache(ttlMs = 10_000, maxEntries = 8)
        assertNull(cache.get<String>("missing"))
    }

    @Test
    fun `TTL 过期后失效并自动移除`() {
        // ttl 用 -1 而不是 0：判定是 `elapsed > ttl`，同一毫秒内写入并读取时 elapsed 为 0，
        // ttl=0 仍会命中（这属于边界语义，不是 bug）；-1 保证必然过期，且无需真的 sleep。
        val cache = TimedCache(ttlMs = -1, maxEntries = 8)
        cache.put("info_1", "v1")
        assertNull(cache.get<String>("info_1"))
    }

    @Test
    fun `超过容量时淘汰最久未用的一项`() {
        val cache = TimedCache(ttlMs = 10_000, maxEntries = 2)
        cache.put("a", "A")
        cache.put("b", "B")
        // 访问 a，使 b 成为"最久未用"
        assertEquals("A", cache.get<String>("a"))
        cache.put("c", "C")

        assertEquals("A 仍应命中", "A", cache.get<String>("a"))
        assertNull("B 应被淘汰", cache.get<String>("b"))
        assertEquals("C 应命中", "C", cache.get<String>("c"))
    }

    @Test
    fun `同 key 覆盖后取到新值`() {
        val cache = TimedCache(ttlMs = 10_000, maxEntries = 4)
        cache.put("k", "old")
        cache.put("k", "new")
        assertEquals("new", cache.get<String>("k"))
    }

    @Test
    fun `clear 之后全部失效`() {
        val cache = TimedCache(ttlMs = 10_000, maxEntries = 4)
        cache.put("a", "A")
        cache.put("b", "B")
        cache.clear()
        assertNull(cache.get<String>("a"))
        assertNull(cache.get<String>("b"))
    }

    @Test
    fun `容量为 1 时只保留最后写入的一项`() {
        val cache = TimedCache(ttlMs = 10_000, maxEntries = 1)
        cache.put("a", "A")
        cache.put("b", "B")
        assertNull(cache.get<String>("a"))
        assertEquals("B", cache.get<String>("b"))
    }
}
