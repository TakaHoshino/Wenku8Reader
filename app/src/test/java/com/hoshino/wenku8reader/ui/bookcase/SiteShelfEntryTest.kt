package com.hoshino.wenku8reader.ui.bookcase

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.wenku8CoverUrl
import com.hoshino.wenku8reader.data.local.LibraryBook
import com.hoshino.wenku8reader.data.local.ReadingProgress
import com.hoshino.wenku8reader.data.local.WENKU8_SHELF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站方条目 → 书架卡片的字段映射（站方书架与本地书架同等地位，卡片是同一套）。
 *
 * 站点只给书名与最新章，映射错了不会抛异常——顶多是卡片显示得像另一本书，
 * 或者封面/进度整栏空着，所以这里把"哪个字段进哪一格"钉死。
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
    fun `本地没有这本书时_书名进标题_最新章进副标题`() {
        val entry = BookcaseItem(
            aid = 1,
            name = "转生成为史莱姆",
            latestName = "第 12 章 魔王降临",
            latestCid = "12345",
        ).toSiteEntry()
        assertEquals("转生成为史莱姆", entry.title)
        assertEquals("第 12 章 魔王降临", entry.author)
    }

    @Test
    fun `没有最新章时留空而不是显示 null`() {
        val entry = BookcaseItem(aid = 2, name = "无更新书", latestName = null).toSiteEntry()
        assertEquals("", entry.author)
    }

    @Test
    fun `封面由书 id 推出_无需额外请求`() {
        // 站点书架不返回封面字段。封面靠 id 规则拼（与官方 App / 站点页面一致），
        // 绝不能为了封面逐本拉 bookInfo——几十本的书架就是一次请求风暴。
        val entry = BookcaseItem(aid = 1191, name = "书").toSiteEntry()
        assertEquals("https://img.wenku8.com/image/1/1191/1191s.jpg", entry.coverUrl)
        assertEquals(wenku8CoverUrl(1191), entry.coverUrl)
    }

    @Test
    fun `进度套用本地进度_站方不提供进度`() {
        val entry = BookcaseItem(aid = 7, name = "书").toSiteEntry(
            progress = ReadingProgress(
                bookId = 7,
                resumeCid = "9",
                totalChapters = 40,
                finishedCids = setOf("1", "2", "3"),
            ),
        )
        assertEquals(40, entry.progressTotal)
        assertEquals(3, entry.readCount)
        assertEquals(3f / 40f, entry.progress, 0.0001f)
    }

    @Test
    fun `没读过时进度为零_卡片不显示进度条`() {
        val entry = BookcaseItem(aid = 8, name = "书").toSiteEntry(progress = null)
        assertEquals(0, entry.progressTotal)
        assertEquals(0, entry.readCount)
    }

    @Test
    fun `本地书架上也有同一本书时_补全作者字数与更新时间`() {
        // 站方给不出作者/字数/更新时间；本地已有同一本书时顺带补全，
        // 这样"按字数/按更新排序"在这些书上也有意义（只读不写，不碰本地的归属）。
        val local = LibraryBook(
            book = BookInfo(
                id = 42,
                title = "本地书名",
                author = "作者甲",
                status = "连载中",
                lastUpdate = "2026-09-20",
                wordCount = "120K",
                coverUrl = "https://img.wenku8.com/image/0/42/42s.jpg",
            ),
            addedAt = 1_700_000_000_000L,
        )

        val entry = BookcaseItem(aid = 42, name = "站方书名", latestName = "第 5 章").toSiteEntry(local)

        // 书名仍以站方为准（这是用户账户里的书架）
        assertEquals("站方书名", entry.title)
        assertEquals("作者甲", entry.author)
        assertEquals("连载中", entry.status)
        assertEquals("2026-09-20", entry.lastUpdate)
        assertEquals(120_000, entry.wordCount)
        assertEquals("https://img.wenku8.com/image/0/42/42s.jpg", entry.coverUrl)
    }

    @Test
    fun `本地书名与作者为空时不会被拿来顶掉站方信息`() {
        val local = LibraryBook(book = BookInfo(id = 9, title = "", author = ""))
        val entry = BookcaseItem(aid = 9, name = "站方书名", latestName = "第 1 章").toSiteEntry(local)
        assertEquals("站方书名", entry.title)
        assertEquals("第 1 章", entry.author)
        assertEquals(wenku8CoverUrl(9), entry.coverUrl)
    }

    @Test
    fun `条目归属标记为站方书架`() {
        val entry = BookcaseItem(aid = 3, name = "书").toSiteEntry()
        assertEquals(setOf(WENKU8_SHELF), entry.shelves)
        assertTrue(entry.shelves.contains(WENKU8_SHELF))
    }

    @Test
    fun `未补全时不携带本地进度与状态`() {
        val entry = BookcaseItem(aid = 4, name = "书").toSiteEntry()
        assertEquals("", entry.status)
        assertEquals("", entry.lastUpdate)
        assertEquals(0, entry.wordCount)
        assertEquals(0, entry.progressTotal)
        assertEquals(0, entry.readCount)
        assertEquals(0f, entry.progress, 0f)
        // addedAt 是本地入架时间，站方书架按站点顺序排，用不到它
        assertEquals(0L, entry.addedAt)
    }
}
