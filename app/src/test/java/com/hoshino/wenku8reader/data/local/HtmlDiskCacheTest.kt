package com.hoshino.wenku8reader.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 磁盘 HTML 缓存的单测：TTL、旧格式迁移、按类别清理与淘汰。
 *
 * 这些分支此前完全没有测试，而它们直接操作**持久化在私有目录里的用户数据**：
 * - TTL 判错 → 要么缓存永不更新（读到旧章节），要么等于没有缓存；
 * - 迁移判错 → 旧缓存文件变成孤儿（占空间且再也命中不了）；
 * - 淘汰/按类别清理判错 → 删掉不该删的文件（用户重开章节要重新下载）。
 */
class HtmlDiskCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun cache(maxBytes: Long = 8L * 1024 * 1024) =
        HtmlDiskCache(temp.newFolder("html_cache"), maxBytes)

    private fun url(n: Int) = "https://wenku8.cc/book/$n.htm"

    @Test
    fun `写入后可命中`() {
        val c = cache()
        c.put(url(1), "<html>1</html>", category = "book")
        assertEquals("<html>1</html>", c.get(url(1), ttlMs = 60_000, category = "book"))
    }

    @Test
    fun `未写入的地址返回 null`() {
        assertNull(cache().get(url(9), ttlMs = 60_000, category = "book"))
    }

    @Test
    fun `过期后返回 null 并删除文件`() {
        val c = cache()
        c.put(url(2), "<html>2</html>", category = "chapter")
        // ttl = -1 保证必然过期（判定是 elapsed > ttl）
        assertNull(c.get(url(2), ttlMs = -1, category = "chapter"))
        assertEquals("过期文件应被删除", 0L, c.totalSize())
    }

    @Test
    fun `空内容不写盘`() {
        val c = cache()
        c.put(url(3), "   ", category = "book")
        assertEquals(0L, c.totalSize())
    }

    @Test
    fun `旧格式文件仍可命中_重新写入时迁移为新格式`() {
        val c = cache()
        val u = url(4)
        c.put(u, "<html>old</html>", category = "book")
        // 把新格式文件名改成旧格式（去掉 category 前缀），模拟升级前的存量缓存
        val created = c.directory.listFiles()!!.single()
        val legacyName = created.name.substringAfter('_')
        val legacy = File(c.directory, legacyName)
        assertTrue(created.renameTo(legacy))

        // 旧格式仍应命中
        assertEquals("<html>old</html>", c.get(u, ttlMs = 60_000, category = "book"))

        // 再次写入：旧文件被删除、只剩新格式一份（避免同一 URL 两份副本长期占空间）
        c.put(u, "<html>new</html>", category = "book")
        assertEquals(1, c.directory.listFiles()!!.count { it.isFile })
        assertFalse("旧格式文件应被迁移掉", File(c.directory, legacyName).exists())
        assertEquals("<html>new</html>", c.get(u, ttlMs = 60_000, category = "book"))
    }

    @Test
    fun `按类别清理只删该类别`() {
        val c = cache()
        c.put(url(5), "<html>5</html>", category = "book")
        c.put(url(6), "<html>6</html>", category = "chapter")
        c.clear(category = "book")

        assertNull("book 类应被清掉", c.get(url(5), ttlMs = 60_000, category = "book"))
        assertEquals(
            "chapter 类必须保留",
            "<html>6</html>",
            c.get(url(6), ttlMs = 60_000, category = "chapter"),
        )
    }

    @Test
    fun `clear 无参数清空全部`() {
        val c = cache()
        c.put(url(7), "<html>7</html>", category = "book")
        c.put(url(8), "<html>8</html>", category = "chapter")
        c.clear()
        assertEquals(0L, c.totalSize())
    }

    @Test
    fun `分组统计按前缀归类_旧格式归入 legacy`() {
        val c = cache()
        c.put(url(10), "<html>10</html>", category = "book")
        c.put(url(11), "<html>11</html>", category = "chapter")
        // 造一个旧格式文件
        File(c.directory, "deadbeefdeadbeefdeadbeefdeadbeef.html").writeText("legacy")

        val byCategory = c.sizeByCategory()
        assertTrue("应包含 book", byCategory.containsKey("book"))
        assertTrue("应包含 chapter", byCategory.containsKey("chapter"))
        assertTrue("旧格式应归入 legacy", byCategory.containsKey("legacy"))
        assertEquals("legacy 内容为 6 字节", 6L, byCategory["legacy"])
    }

    @Test
    fun `超过上限时按最旧优先淘汰到上限的七成`() {
        // 先用大上限写入：若一开始就用 1000，第三次写入当场就会触发淘汰，
        // 那就没机会在淘汰前把 mtime 钉死了。
        val c = cache(maxBytes = 100_000)
        // 三个条目，共 3*400=1200 字节
        c.put(url(20), "a".repeat(400), category = "book")
        c.put(url(21), "b".repeat(400), category = "book")
        c.put(url(22), "c".repeat(400), category = "book")

        // 不能依赖"写入先后 = mtime 先后"：三次写入常落在同一毫秒内，mtime 相同就让
        // 「谁最旧」变得取决于文件系统枚举顺序（CI 上确实因此偶发失败过）。
        // 这里显式把 mtime 拉开，直接钉住排序依据本身。
        val files = (c.directory.listFiles() ?: error("缓存目录不可读")).sortedBy { it.name }
        assertEquals(3, files.size)
        files.forEachIndexed { i, f ->
            assertTrue("设置 mtime 失败：${f.name}", f.setLastModified(1_000_000L + i * 10_000L))
        }
        val oldest = files.first()
        val newest = files.last()

        // 调低上限触发淘汰：1200 > 1000 → 按 mtime 删到 ≤ 700（删掉两个，留下最新的）
        c.setMaxBytes(1000)

        // 淘汰目标：降到 1000*0.7 = 700 以下 → 至少删掉一个
        assertTrue("总量应被压到上限以下", c.totalSize() < 1000)
        // 注意不要用 get() 判定存活：那些 mtime 被改到了 1970 年，get 会按"已过期"删掉文件。
        assertFalse("最旧的条目应先被淘汰", oldest.exists())
        assertEquals("最新的条目应保留", 400L, newest.length())
    }

    @Test
    fun `调低上限会立即收缩`() {
        val c = cache(maxBytes = 100_000)
        c.put(url(30), "x".repeat(5_000), category = "book")
        assertTrue(c.totalSize() > 0)
        c.setMaxBytes(1_000)
        assertTrue("调低上限后应立即收缩", c.totalSize() <= 1_000)
    }
}
