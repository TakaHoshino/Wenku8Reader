package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Parsers` 的本地单元测试。
 *
 * 该文件是纯 JVM 逻辑（只依赖 `java.util.regex`），历史上是 bug 最密集的区域
 * （水印剥离、嵌套 div 边界、章节切分、封面补全），却在本次评估时**没有任何测试**。
 * 这里覆盖的是最容易在重构中被悄悄改坏的行为契约。
 */
class ParsersTest {

    // ------------------------------------------------------------------ //
    // parseChapter
    // ------------------------------------------------------------------ //

    @Test
    fun parseChapter_stripsWatermarksAndConvertsLineBreaks() {
        val html = """
            <div id="title">第一卷 游戏规则</div>
            <div id="content">
            <ul id="contentdp">本文来自 轻小说文库(http://www.wenku8.com)</ul>
            &nbsp;&nbsp;&nbsp;&nbsp;第一行<br />
            <br />
            &nbsp;&nbsp;&nbsp;&nbsp;第二行<br />
            <ul id="contentdp">最新最全的日本动漫轻小说 轻小说文库 为你一网打尽！</ul>
            </div>
            <div id="footlink">上一章 下一章</div>
        """.trimIndent()

        val ch = Parsers.parseChapter(html)

        assertEquals("第一卷 游戏规则", ch.title)
        assertTrue("首尾 <ul id=contentdp> 水印必须被整体剥离", !ch.text.contains("contentdp"))
        assertTrue("头部水印文本不应残留", !ch.text.contains("本文来自"))
        assertTrue("尾部水印文本不应残留", !ch.text.contains("为你一网打尽"))
        assertTrue(ch.text.contains("第一行"))
        assertTrue(ch.text.contains("第二行"))
        assertTrue("连续空行应折叠为最多一个空行", !ch.text.contains("\n\n\n"))
        assertTrue("footlink 之后的内容不应进入正文", !ch.text.contains("上一章"))
    }

    @Test
    fun parseChapter_handlesNestedDivForIllustrations() {
        // 正文里出现嵌套 <div class="divimage">：用 indexOf 定界的实现必须不崩且不漏文本
        val html = """
            <div id="title">插图</div>
            <div id="content">
            正文前段<br />
            <div class="divimage"><a href="https://pic.777743.xyz/1/1191/177419/218990.jpg" target="_blank">
            <img src="https://pic.777743.xyz/1/1191/177419/218990.jpg" border="0" class="imagecontent"></a></div>
            正文后段<br />
            </div>
        """.trimIndent()

        val ch = Parsers.parseChapter(html)

        assertEquals("插图", ch.title)
        assertEquals(
            listOf("https://pic.777743.xyz/1/1191/177419/218990.jpg"),
            ch.images,
        )
        assertTrue(ch.text.contains("正文前段"))
        assertTrue(ch.text.contains("正文后段"))
    }

    @Test
    fun parseChapter_withoutContentRegionReturnsEmptyText() {
        val ch = Parsers.parseChapter("<div id=\"title\">空章</div>")

        assertEquals("空章", ch.title)
        assertEquals("", ch.text)
        assertTrue(ch.images.isEmpty())
    }

    // ------------------------------------------------------------------ //
    // parseChapterIndex
    // ------------------------------------------------------------------ //

    @Test
    fun parseChapterIndex_groupsChaptersUnderTheirVolume() {
        val html = """
            <tr><td class="vcss" colspan="4" vid="36586">第一卷</td></tr>
            <tr><td class="ccss"><a href="36587.htm">游戏规则</a></td>
            <td class="ccss"><a href="36588.htm">班级点名簿</a></td></tr>
            <tr><td class="vcss" colspan="4" vid="36589">第二卷</td></tr>
            <tr><td class="ccss"><a href="36590.htm">新的一章</a></td></tr>
        """.trimIndent()

        val vols = Parsers.parseChapterIndex(html)

        assertEquals(2, vols.size)
        assertEquals("第一卷", vols[0].name)
        assertEquals(listOf("36587", "36588"), vols[0].chapters.map { it.cid })
        assertEquals(listOf("游戏规则", "班级点名簿"), vols[0].chapters.map { it.name })
        assertEquals("第二卷", vols[1].name)
        assertEquals(listOf("36590"), vols[1].chapters.map { it.cid })
    }

    // ------------------------------------------------------------------ //
    // 书单解析与封面补全
    // ------------------------------------------------------------------ //

    @Test
    fun parseBookList_absolutizesRelativeCoverPath() {
        // 站点在标签/书单页给相对路径；不补全域名时按页面地址解析会 404
        val html = """
            <ul class="ultop">
              <li><a href="/book/4340.htm" title="在史莱姆地下城里夺取天下">
                <img src="/image/4/4340/4340s.jpg" width="90" />
              </a></li>
            </ul>
        """.trimIndent()

        val books = Parsers.parseBookList(html)

        assertEquals(1, books.size)
        assertEquals(4340, books[0].id)
        assertEquals("在史莱姆地下城里夺取天下", books[0].name)
        assertEquals("https://img.wenku8.com/image/4/4340/4340s.jpg", books[0].coverUrl)
    }

    @Test
    fun parseBookList_keepsAbsoluteAndProtocolRelativeCovers() {
        val absolute = Parsers.parseBookList(
            "<li><a href=\"/book/1.htm\" title=\"A\">" +
                "<img src=\"https://img.wenku8.com/image/1/1/1s.jpg\" /></a></li>",
        )
        assertEquals("https://img.wenku8.com/image/1/1/1s.jpg", absolute[0].coverUrl)

        val protocolRelative = Parsers.parseBookList(
            "<li><a href=\"/book/2.htm\" title=\"B\">" +
                "<img src=\"//img.wenku8.com/image/2/2/2s.jpg\" /></a></li>",
        )
        assertEquals("https://img.wenku8.com/image/2/2/2s.jpg", protocolRelative[0].coverUrl)
    }

    @Test
    fun parseBookList_withoutCoverLeavesCoverNull() {
        val books = Parsers.parseBookList(
            "<ul class=\"ultop\"><li><a href=\"/book/3988.htm\" title=\"书名A\">书名A</a></li></ul>",
        )

        assertEquals(1, books.size)
        assertEquals("书名A", books[0].name)
        assertNull(books[0].coverUrl)
    }

    // ------------------------------------------------------------------ //
    // splitFullTxt（EPUB 章节切分的核心）
    // ------------------------------------------------------------------ //

    @Test
    fun splitFullTxt_splitsByVolumeChapterHeadersAndSkipsSiteWatermarks() {
        val txt = """
            ★☆★☆★☆轻小说文库(Www.WenKu8.Com)☆★☆★☆★

            <国王游戏>

            第一卷 游戏规则
                台版 转自 Lafrente

            第一卷 班级点名簿
                第二条正文

            ----------------------------------
        """.trimIndent()

        val volumes = listOf(
            Volume(
                "第一卷",
                listOf(Chapter("1", "游戏规则"), Chapter("2", "班级点名簿")),
            ),
        )

        val chapters = Parsers.splitFullTxt(txt, volumes)

        assertEquals(2, chapters.size)
        assertEquals("游戏规则", chapters[0].title)
        assertTrue("章节正文应落在正确的章节里", chapters[0].text.contains("台版 转自 Lafrente"))
        assertEquals("班级点名簿", chapters[1].title)
        assertTrue(chapters[1].text.contains("第二条正文"))

        // 站点水印/分隔线必须被过滤掉
        val all = chapters.joinToString("\n") { it.text }
        assertTrue("水印行不应进入正文", !all.contains("轻小说文库"))
        assertTrue("★ 开头行不应进入正文", !all.contains("★"))
        assertTrue("纯分隔线不应进入正文", !all.contains("-----"))
    }

    @Test
    fun splitFullTxt_missingChapterYieldsEmptyTextButKeepsSlot() {
        val volumes = listOf(
            Volume("第一卷", listOf(Chapter("1", "存在的章"), Chapter("2", "缺失的章"))),
        )
        val txt = "第一卷 存在的章\n正文\n"

        val chapters = Parsers.splitFullTxt(txt, volumes)

        // 缺失章节仍占位（保持与目录一一对应），只是正文为空
        assertEquals(2, chapters.size)
        assertEquals("存在的章", chapters[0].title)
        assertEquals("缺失的章", chapters[1].title)
        assertEquals("", chapters[1].text)
    }

    @Test
    fun splitFullTxt_normalizesCrLf() {
        val volumes = listOf(Volume("第一卷", listOf(Chapter("1", "章一"))))
        val txt = "第一卷 章一\r\n第一行\r\n第二行\r\n"

        val chapters = Parsers.splitFullTxt(txt, volumes)

        assertEquals(1, chapters.size)
        assertEquals("第一行\n第二行", chapters[0].text)
    }

    // ------------------------------------------------------------------ //
    // parseBookInfo / parseHomepage 基础契约
    // ------------------------------------------------------------------ //

    @Test
    fun parseBookInfo_readsTitleMetaAndGidFromNovelLink() {
        // 站点把「标签：值」放在同一个 td 内（parser 用 substringAfter("：") 取值），
        // 因此下面的表格结构必须与真实页面一致，否则断言会失真。
        val html = """
            <title>国王游戏 - 金泽伸明 - 其他文库 - 轻小说文库</title>
            <tr><td class="hottext">文库分类：其他文库</td></tr>
            <tr><td class="hottext">小说作者：金泽伸明</td></tr>
            <tr><td class="hottext">文章状态：连载中</td></tr>
            <tr><td class="hottext">最后更新：2026-08-22</td></tr>
            <tr><td class="hottext">全文长度：390K</td></tr>
            <img src="https://img.wenku8.com/image/1/1191/1191s.jpg" />
            <a href="/novel/1/1191/index.htm">小说目录</a>
        """.trimIndent()

        val info = Parsers.parseBookInfo(html, 1191)

        assertEquals("国王游戏", info.title)
        assertEquals("金泽伸明", info.author)
        assertEquals("其他文库", info.category)
        assertEquals("连载中", info.status)
        assertEquals("2026-08-22", info.lastUpdate)
        assertEquals("390K", info.wordCount)
        assertEquals(1, info.groupId)
        assertEquals("https://img.wenku8.com/image/1/1191/1191s.jpg", info.coverUrl)
    }

    @Test
    fun parseHomepage_parsesSectionsAndSkipsBooklessBlocks() {
        val html = """
            <div class="block">
              <div class="blocktitle">用户登录</div>
              <form action="login.php"></form>
            </div>
            <div class="block">
              <div class="blocktitle">今日热榜</div>
              <ul class="ultop">
                <li><a href="/book/3988.htm" target="_blank" title="书名甲">书名甲</a></li>
              </ul>
            </div>
        """.trimIndent()

        val sections = Parsers.parseHomepage(html)

        // 无书籍的栏目（登录/公告等）必须被过滤掉
        assertEquals(1, sections.size)
        assertEquals("今日热榜", sections[0].title)
        assertEquals(3988, sections[0].books[0].id)
    }
}
