package com.hoshino.wenku8reader.data

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal EPUB3 writer (mimetype + container + opf + nav + ncx + chapters). */
object EpubBuilder {

    /** 已下载的章节插图。mediaType 为空时按文件头自动识别。 */
    data class EpubImage(
        val bytes: ByteArray,
        val mediaType: String = "",
    )

    /**
     * EPUB3 规定 `dcterms:modified` 必须是 UTC 的 `CCYY-MM-DDThh:mm:ssZ`。
     * 旧实现把它写死成 `2026-01-01T00:00:00Z`（一个固定的未来时间），
     * 每本书的修改时间都相同且不随构建变化；改为取构建时刻。
     * 格式化器本身线程安全，minSdk 26 起 `java.time` 原生可用。
     */
    private val MODIFIED_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    fun build(
        title: String,
        author: String?,
        description: String?,
        chapters: List<ChapterContent>,
        chapterImages: List<List<EpubImage>> = emptyList(),
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        build(title, author, description, chapters, bos, chapterImages)
        return bos.toByteArray()
    }

    /**
     * 流式重载：直接把 EPUB 写进 [out]（方法内部会关闭它）。
     * 长篇 EPUB 若先整本拼成 `ByteArray` 再交给 `FileSaver`，内存里会同时存在两份数据，
     * 因此提供该入口供调用方边写边落盘；上面的 [build] 委托到这里，公开 API 保持兼容。
     */
    fun build(
        title: String,
        author: String?,
        description: String?,
        chapters: List<ChapterContent>,
        out: OutputStream,
        chapterImages: List<List<EpubImage>> = emptyList(),
    ) {
        // uid 在 opf 与 ncx 中必须是同一个值，这里算一次后向下传递，
        // 避免像旧实现那样两处各写一遍（还会各自漂移）。
        val uid = bookUid(title, author)
        val modified = MODIFIED_FORMAT.format(Instant.now())
        ZipOutputStream(out).use { z ->
            write(z, "mimetype", "application/epub+zip", stored = true)
            write(z, "META-INF/container.xml", container())
            write(z, "OEBPS/style.css", css())
            write(z, "OEBPS/cover.xhtml", coverXhtml(title, author, description))
            write(
                z,
                "OEBPS/content.opf",
                opf(title, author, chapters.size, uid, modified, chapterImages),
            )
            write(z, "OEBPS/nav.xhtml", nav(chapters))
            write(z, "OEBPS/toc.ncx", ncx(title, chapters, uid))
            chapters.forEachIndexed { i, ch ->
                val images = chapterImages.getOrElse(i) { emptyList() }
                write(
                    z,
                    "OEBPS/${chapterHref(i + 1)}",
                    chapterXhtml(ch, imageHrefs(i + 1, images)),
                )
                images.forEachIndexed { imageIndex, image ->
                    writeBytes(
                        z,
                        "OEBPS/${imageHref(i + 1, imageIndex + 1, image)}",
                        image.bytes,
                    )
                }
            }
        }
    }

    /**
     * 书籍的全局唯一标识（`dc:identifier` 与 ncx 的 `dtb:uid`）。
     *
     * 旧实现 `"wenku8-" + title.replace(Regex("[^0-9A-Za-z]"), "")` 会把中文书名整串清空，
     * 于是所有中文书的 uid 都退化成 `"wenku8-"`（标识符不唯一，阅读器可能串书）。
     * 现在取「书名 + 作者」UTF-8 字节的 SHA-256 前 8 字节（16 位十六进制）：
     * 书名不同即不冲突，同一本书每次构建结果稳定。
     * 用哈希而非书名原文，是为了让标识符在任何书名（空格/引号/中文/emoji）下都安全可移植。
     */
    private fun bookUid(title: String, author: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$title\u0000${author ?: ""}".toByteArray(Charsets.UTF_8))
        val hex = digest.take(8).joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xFF) }
        return "wenku8-$hex"
    }

    /**
     * 章节 id 与文件名统一用同一套 `%04d` 序号（`c0001` ↔ `chapter_0001.xhtml`）。
     * 旧实现 id 用 `c%04d`、文件名用 `chapter_$i.xhtml`，两套编号并存容易对不上。
     * 显式指定 [Locale.US]：`%d` 会按默认 Locale 输出本地化数字（如阿拉伯语数字），
     * 那样会生成非 ASCII 的包内文件名。
     */
    private fun chapterNumber(i: Int): String = "%04d".format(Locale.US, i)

    private fun chapterId(i: Int): String = "c" + chapterNumber(i)

    private fun chapterHref(i: Int): String = "chapter_${chapterNumber(i)}.xhtml"

    private fun imageHref(chapter: Int, image: Int, data: EpubImage): String {
        val mediaType = imageMediaType(data)
        val extension = when (mediaType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "bin"
        }
        return "images/chapter_${chapterNumber(chapter)}_${chapterNumber(image)}.$extension"
    }

    private fun imageHrefs(chapter: Int, images: List<EpubImage>): List<String> =
        images.mapIndexed { index, image -> imageHref(chapter, index + 1, image) }

    private fun imageMediaType(image: EpubImage): String {
        image.mediaType.takeIf { it.startsWith("image/") }?.let { return it }
        val b = image.bytes
        return when {
            b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() ->
                "image/jpeg"
            b.size >= 8 &&
                b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
                b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() ->
                "image/png"
            b.size >= 6 && String(b, 0, 3, Charsets.US_ASCII) == "GIF" ->
                "image/gif"
            b.size >= 12 &&
                String(b, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(b, 8, 4, Charsets.US_ASCII) == "WEBP" ->
                "image/webp"
            else -> "application/octet-stream"
        }
    }

    // ------------------------------------------------------------------ //
    private fun write(z: ZipOutputStream, name: String, data: String,
                      stored: Boolean = false) {
        writeBytes(z, name, data.toByteArray(Charsets.UTF_8), stored)
    }

    private fun writeBytes(
        z: ZipOutputStream,
        name: String,
        bytes: ByteArray,
        stored: Boolean = false,
    ) {
        val entry = ZipEntry(name).apply {
            if (stored) {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                crc = crc32(bytes)
            }
        }
        z.putNextEntry(entry)
        z.write(bytes)
        z.closeEntry()
    }

    private fun crc32(bytes: ByteArray): Long {
        val c = CRC32()
        c.update(bytes)
        return c.value
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun container() = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private fun css() = "body{font-family:serif;line-height:1.8;margin:5% 8%;}\n" +
        "h1{text-align:center;}h2{text-align:center;margin:2em 0;}\n" +
        "p{text-indent:2em;margin:0.6em 0;}\n" +
        "img{display:block;max-width:100%;height:auto;margin:1em auto;}\n"

    private fun opf(title: String, author: String?, count: Int,
                    uid: String, modified: String,
                    chapterImages: List<List<EpubImage>>): String {
        val manifest = StringBuilder()
        val spine = StringBuilder()
        manifest.append("<item id=\"style\" href=\"style.css\" media-type=\"text/css\"/>\n")
        manifest.append("<item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        spine.append("<itemref idref=\"cover\"/>\n")
        for (i in 1..count) {
            // id 与 href 用同一个序号，保证 manifest 项与包内文件一一对应
            manifest.append("<item id=\"${chapterId(i)}\" href=\"${chapterHref(i)}\" media-type=\"application/xhtml+xml\"/>\n")
            spine.append("<itemref idref=\"${chapterId(i)}\"/>\n")
            chapterImages.getOrElse(i - 1) { emptyList() }.forEachIndexed { imageIndex, image ->
                val id = "img_${chapterId(i)}_${chapterNumber(imageIndex + 1)}"
                val href = imageHref(i, imageIndex + 1, image)
                manifest.append(
                    "<item id=\"$id\" href=\"$href\" " +
                        "media-type=\"${imageMediaType(image)}\"/>\n"
                )
            }
        }
        manifest.append(
            "<item id=\"nav\" href=\"nav.xhtml\" " +
                "media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n"
        )
        manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">$uid</dc:identifier>
    <dc:title>${esc(title)}</dc:title>
    <dc:language>zh-CN</dc:language>
    ${author?.let { "<dc:creator>${esc(it)}</dc:creator>" } ?: ""}
    <meta property="dcterms:modified">$modified</meta>
  </metadata>
  <manifest>
$manifest  </manifest>
  <spine toc="ncx">
$spine  </spine>
</package>"""
    }

    private fun nav(chapters: List<ChapterContent>): String {
        val points = chapters.withIndex().joinToString("\n") { (i, ch) ->
            "<li><a href=\"${chapterHref(i + 1)}\">${esc(ch.title)}</a></li>"
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>目录</title></head>
<body><nav epub:type="toc" id="toc"><h1>目录</h1><ol>
$points
</ol></nav></body></html>"""
    }

    private fun ncx(title: String, chapters: List<ChapterContent>, uid: String): String {
        val points = chapters.withIndex().joinToString("\n") { (i, ch) ->
            val n = i + 1
            "<navPoint id=\"n$n\" playOrder=\"$n\"><navLabel><text>${esc(ch.title)}</text></navLabel>" +
                "<content src=\"${chapterHref(n)}\"/></navPoint>"
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="$uid"/></head>
  <docTitle><text>${esc(title)}</text></docTitle>
  <navMap>
$points
  </navMap>
</ncx>"""
    }

    private fun coverXhtml(title: String, author: String?, description: String?): String {
        val body = StringBuilder("<h1>${esc(title)}</h1>")
        if (!author.isNullOrEmpty()) body.append("<p style=\"text-indent:0;text-align:center\">作者：${esc(author)}</p>")
        if (!description.isNullOrEmpty()) body.append("<p>${esc(description)}</p>")
        return xhtml(title.ifEmpty { "Cover" }, body.toString())
    }

    /** 生成单章 XHTML；插图由调用方预先下载并通过 [imageHrefs] 传入。 */
    private fun chapterXhtml(ch: ChapterContent, imageHrefs: List<String>): String {
        val body = StringBuilder("<h2>${esc(ch.title)}</h2>")
        imageHrefs.forEachIndexed { index, href ->
            body.append("<img src=\"")
                .append(esc(href))
                .append("\" alt=\"插图 ")
                .append(index + 1)
                .append("\"/>")
        }
        for (p in ch.text.split("\n")) {
            val t = p.trim()
            if (t.isNotEmpty()) body.append("<p>").append(esc(t)).append("</p>")
        }
        return xhtml(ch.title, body.toString())
    }

    private fun xhtml(title: String, body: String): String = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>${esc(title)}</title>
<link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>$body</body></html>"""
}
