package com.hoshino.wenku8reader

import com.hoshino.wenku8reader.ui.reader.splitReaderParagraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [splitReaderParagraphs] 的单元测试：切段口径直接决定滚动模式的渲染结果。 */
class ReaderParagraphsTest {

    @Test
    fun `按换行切段`() {
        assertEquals(
            listOf("第一段", "第二段", "第三段"),
            splitReaderParagraphs("第一段\n第二段\n第三段"),
        )
    }

    @Test
    fun `丢弃空白行`() {
        assertEquals(
            listOf("第一段", "第二段"),
            splitReaderParagraphs("第一段\n\n   \n第二段\n"),
        )
    }

    @Test
    fun `保留段首缩进不做 trim`() {
        // 中文正文常用全角空格缩进，trim 会把它吃掉、段落看起来就"顶格"了
        val text = "\u3000\u3000第一段正文\n\u3000\u3000第二段正文"
        val paragraphs = splitReaderParagraphs(text)
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs.all { it.startsWith("\u3000\u3000") })
    }

    @Test
    fun `兼容 CRLF 与 CR`() {
        assertEquals(listOf("A", "B"), splitReaderParagraphs("A\r\nB"))
        assertEquals(listOf("A", "B"), splitReaderParagraphs("A\rB"))
    }

    @Test
    fun `空文本与纯空白文本得到空列表`() {
        assertTrue(splitReaderParagraphs("").isEmpty())
        assertTrue(splitReaderParagraphs("\n\n   \n").isEmpty())
    }
}
