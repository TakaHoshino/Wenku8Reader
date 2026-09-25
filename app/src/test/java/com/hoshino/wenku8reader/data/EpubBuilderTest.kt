package com.hoshino.wenku8reader.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EPUB 产物结构单测。
 *
 * 此前 `EpubBuilder` 完全没有测试，而它的输出是**交给第三方阅读器解析的 XML/ZIP**：
 * - 转义漏了 `&`/`<`/`>` → 整本 EPUB 在阅读器里报"文件损坏"；
 * - `mimetype` 不是第一个条目、或被压缩 → 违反 EPUB3 规范，部分阅读器直接拒绝打开；
 * - opf 与 ncx 的 uid 不一致 → 书在书架里会出现两份/书签失效。
 * 这里不比对整份文本（会因时间戳等无关内容而脆弱），而是校验结构、规范约束与 XML 良构性。
 */
class EpubBuilderTest {

    private fun chapters(vararg pairs: Pair<String, String>) = pairs.map { (title, text) ->
        ChapterContent(title = title, text = text)
    }

    private fun readEntries(bytes: ByteArray): List<Pair<ZipEntry, ByteArray>> {
        val out = mutableListOf<Pair<ZipEntry, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out += entry to zip.readBytes()
            }
        }
        return out
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    @Test
    fun `mimetype 必须是第一个条目且不压缩`() {
        val entries = readEntries(EpubBuilder.build("书名", "作者", "简介", chapters("第一章" to "正文")))
        val first = entries.first()
        assertEquals("mimetype", first.first.name)
        assertEquals(
            "method=STORED 才是符合 EPUB3 的未压缩存储",
            ZipEntry.STORED,
            first.first.method,
        )
        assertEquals("application/epub+zip", String(first.second, Charsets.UTF_8))
    }

    @Test
    fun `包含全部必需条目`() {
        val names = readEntries(
            EpubBuilder.build("书名", null, null, chapters("第一章" to "正文")),
        ).map { it.first.name }
        listOf(
            "mimetype",
            "META-INF/container.xml",
            "OEBPS/content.opf",
            "OEBPS/nav.xhtml",
            "OEBPS/toc.ncx",
        ).forEach { assertTrue("缺少条目 $it", it in names) }
    }

    @Test
    fun `标题作者简介含 XML 特殊字符时仍良构且内容可还原`() {
        val title = "A & B <测试> \"引号\""
        val author = "作者 & 合作者"
        val description = "简介：1 < 2 && 3 > 2"
        val bytes = EpubBuilder.build(title, author, description, chapters("第一章" to "正文 & 更多"))
        val entries = readEntries(bytes)

        // 良构性：转义漏掉任何一个特殊字符，这里都会抛 SAXException
        val opf = entries.first { it.first.name == "OEBPS/content.opf" }.second
        val doc = parseXml(opf)
        val texts = doc.getElementsByTagName("dc:title").item(0).textContent
        assertEquals("转义后再解析应还原原文", title, texts)
        assertNotNull(parseXml(entries.first { it.first.name == "OEBPS/nav.xhtml" }.second))
        assertNotNull(parseXml(entries.first { it.first.name == "OEBPS/cover.xhtml" }.second))
        assertNotNull(parseXml(entries.first { it.first.name == "OEBPS/toc.ncx" }.second))
    }

    @Test
    fun `opf 与 ncx 使用同一个 uid`() {
        val entries = readEntries(
            EpubBuilder.build("书名", "作者", null, chapters("第一章" to "正文")),
        )
        val opfText = String(entries.first { it.first.name == "OEBPS/content.opf" }.second)
        val ncxText = String(entries.first { it.first.name == "OEBPS/toc.ncx" }.second)
        // uid 由「书名 + 作者」的 SHA-256 派生，形如 wenku8-<16 位十六进制>
        val uid = Regex("<dc:identifier id=\"uid\">([^<]+)</dc:identifier>")
            .find(opfText)?.groupValues?.get(1)
        assertNotNull("opf 里应写入 dc:identifier", uid)
        assertTrue("uid 应形如 wenku8-<hex>", uid!!.startsWith("wenku8-"))
        assertTrue("ncx 应引用同一个 uid", ncxText.contains(uid))
    }

    @Test
    fun `每章各有一个 xhtml 条目`() {
        val names = readEntries(
            EpubBuilder.build("书名", null, null, chapters("第一章" to "一", "第二章" to "二")),
        ).map { it.first.name }
        assertEquals(2, names.count { it.startsWith("OEBPS/chapter") && it.endsWith(".xhtml") })
    }

    @Test
    fun `没有章节时仍产出可解析的容器`() {
        val entries = readEntries(EpubBuilder.build("书名", null, null, emptyList()))
        assertNotNull(parseXml(entries.first { it.first.name == "META-INF/container.xml" }.second))
        assertNotNull(parseXml(entries.first { it.first.name == "OEBPS/content.opf" }.second))
    }
}
