package com.hoshino.wenku8reader

import androidx.compose.ui.unit.Density
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.ui.reader.ReaderPage
import com.hoshino.wenku8reader.ui.reader.paginateChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 分页算法的边界测试。这段逻辑此前没有测试，而它最容易"静默出错"：
// 少切了内容用户未必立刻发现，读到章节结尾才发现漏字。
// 固定 Density(1f, 1f) 让每页容量可预测：字号 10sp、行距 1.0 → 行高 10px；
// 容器 100x100 → 每页 10 行、每行 10 字（留 5% 余量后按 9 字算）。
class PaginationTest {

    private val density = Density(density = 1f, fontScale = 1f)
    private val fontSp = 10
    private val lineSpacing = 1f

    private fun paginate(
        text: String,
        images: List<String> = emptyList(),
        width: Int = 100,
        height: Int = 100,
    ) = paginateChapter(
        density = density,
        chapter = ChapterContent(title = "", text = text, images = images),
        maxWidthPx = width,
        maxHeightPx = height,
        fontSizeSp = fontSp,
        lineSpacing = lineSpacing,
    )

    @Test
    fun `容器尺寸非法时返回空列表`() {
        assertTrue(paginate("正文", width = 0).isEmpty())
        assertTrue(paginate("正文", height = -1).isEmpty())
    }

    @Test
    fun `短文本只有一页且不丢字`() {
        val pages = paginate("第一段\n第二段")
        assertEquals(1, pages.size)
        assertEquals("第一段\n第二段", (pages[0] as ReaderPage.Text).text)
    }

    @Test
    fun `长文本切页后内容完整不丢字`() {
        val text = (1..400).joinToString("\n") { "第${it}段" }
        val pages = paginate(text)
        assertTrue("应当切成多页，实际 ${pages.size} 页", pages.size > 1)

        // 关键断言：各页拼回来（还原被 trim 掉的换行）应包含原文每一段，且顺序不变
        val joined = pages.filterIsInstance<ReaderPage.Text>().joinToString("\n") { it.text }
        for (i in 1..400) {
            assertTrue("第 $i 段不应丢失", joined.contains("第${i}段"))
        }
        val firstIndex = joined.indexOf("第1段")
        val lastIndex = joined.indexOf("第400段")
        assertTrue("顺序必须保持", firstIndex in 0 until lastIndex)
    }

    @Test
    fun `标题并入首页正文`() {
        val pages = paginateChapter(
            density = density,
            chapter = ChapterContent(title = "第一章", text = "正文"),
            maxWidthPx = 100,
            maxHeightPx = 100,
            fontSizeSp = fontSp,
            lineSpacing = lineSpacing,
        )
        assertEquals("第一章\n正文", (pages.single() as ReaderPage.Text).text)
    }

    @Test
    fun `插图各自成为一页且排在正文之后`() {
        val pages = paginate("正文", images = listOf("a.jpg", "b.jpg"))
        assertEquals(3, pages.size)
        assertTrue(pages[0] is ReaderPage.Text)
        assertEquals("a.jpg", (pages[1] as ReaderPage.Image).url)
        assertEquals("b.jpg", (pages[2] as ReaderPage.Image).url)
    }

    @Test
    fun `空正文与无插图时保留一个空页避免分页数为零`() {
        val pages = paginate("")
        assertEquals(1, pages.size)
        assertEquals("", (pages[0] as ReaderPage.Text).text)
    }
}
