package com.hoshino.wenku8reader.ui.bookcase

import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.local.WENKU8_SHELF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站方条目 → 书架卡片的字段映射。
 *
 * 站点只给书名与最新章，映射错了不会抛异常——顶多是卡片显示得像另一本书，
 * 所以这里把"哪个字段进哪一格"钉死。
 */
class SiteShelfEntryTest {

    @Test
    fun `书 id 取 aid_而不是书架记录 id`() {
        // aid 与 bid 不相等（实测 aid=2896 / bid=12786589）。卡片上的 bookId 一旦错用 bid，
        // 点进详情会跳到别人的书，且长按移出会传错参数。
        val entry = BookcaseItem(aid = 2896, name = "某书", bid = 12786589).toSiteEntry()
        assertEquals(2896, entry.bookId)
    }

    @Test
    fun `书名进标题_最新章进副标题与更新时间位`() {
        val entry = BookcaseItem(
            aid = 1,
            name = "转生成为史莱姆",
            latestName = "第 12 章 魔王降临",
            latestCid = "12345",
        ).toSiteEntry()
        assertEquals("转生成为史莱姆", entry.title)
        assertEquals("第 12 章 魔王降临", entry.author)
        assertEquals("第 12 章 魔王降临", entry.lastUpdate)
    }

    @Test
    fun `没有最新章时留空而不是显示 null`() {
        val entry = BookcaseItem(aid = 2, name = "无更新书", latestName = null).toSiteEntry()
        assertEquals("", entry.author)
        assertEquals("", entry.lastUpdate)
    }

    @Test
    fun `条目归属标记为虚拟书架`() {
        val entry = BookcaseItem(aid = 3, name = "书").toSiteEntry()
        assertEquals(setOf(WENKU8_SHELF), entry.shelves)
        assertTrue(entry.shelves.contains(WENKU8_SHELF))
    }

    @Test
    fun `卡片不携带本地进度与封面`() {
        // 站方条目没有本地进度，也不该为每本并发拉 bookInfo（请求风暴）。
        val entry = BookcaseItem(aid = 4, name = "书").toSiteEntry()
        assertEquals(null, entry.coverUrl)
        assertEquals(0, entry.progressTotal)
        assertEquals(0, entry.readCount)
        assertEquals(0f, entry.progress, 0f)
    }
}
