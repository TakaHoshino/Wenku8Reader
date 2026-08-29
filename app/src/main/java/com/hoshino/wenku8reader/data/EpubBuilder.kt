package com.hoshino.wenku8reader.data

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal EPUB3 writer (mimetype + container + opf + nav + ncx + chapters). */
object EpubBuilder {

    fun build(title: String, author: String?, description: String?,
              chapters: List<ChapterContent>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            write(z, "mimetype", "application/epub+zip", stored = true)
            write(z, "META-INF/container.xml", container())
            write(z, "OEBPS/style.css", css())
            write(z, "OEBPS/cover.xhtml", coverXhtml(title, author, description))
            write(z, "OEBPS/content.opf", opf(title, author, chapters.size))
            write(z, "OEBPS/nav.xhtml", nav(chapters))
            write(z, "OEBPS/toc.ncx", ncx(title, chapters))
            chapters.forEachIndexed { i, ch ->
                write(z, "OEBPS/chapter_${i + 1}.xhtml", chapterXhtml(ch))
            }
        }
        return bos.toByteArray()
    }

    // ------------------------------------------------------------------ //
    private fun write(z: ZipOutputStream, name: String, data: String,
                      stored: Boolean = false) {
        val bytes = data.toByteArray(Charsets.UTF_8)
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
        "p{text-indent:2em;margin:0.6em 0;}\n"

    private fun opf(title: String, author: String?, count: Int): String {
        val manifest = StringBuilder()
        val spine = StringBuilder()
        manifest.append("<item id=\"style\" href=\"style.css\" media-type=\"text/css\"/>\n")
        manifest.append("<item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        spine.append("<itemref idref=\"cover\"/>\n")
        for (i in 1..count) {
            val id = "c%04d".format(i)
            manifest.append("<item id=\"$id\" href=\"chapter_$i.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            spine.append("<itemref idref=\"$id\"/>\n")
        }
        manifest.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        spine.append("<itemref idref=\"nav\" linear=\"no\"/>\n")
        manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        val uid = "wenku8-" + title.replace(Regex("[^0-9A-Za-z]"), "")
        return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">$uid</dc:identifier>
    <dc:title>${esc(title)}</dc:title>
    <dc:language>zh-CN</dc:language>
    ${author?.let { "<dc:creator>${esc(it)}</dc:creator>" } ?: ""}
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
$manifest  </manifest>
  <spine toc="ncx">
$spine  </spine>
</package>"""
    }

    private fun nav(chapters: List<ChapterContent>): String {
        val points = chapters.withIndex().joinToString("\n") { (i, ch) ->
            "<li><a href=\"chapter_${i + 1}.xhtml\">${esc(ch.title)}</a></li>"
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>目录</title></head>
<body><nav epub:type="toc" id="toc"><h1>目录</h1><ol>
$points
</ol></nav></body></html>"""
    }

    private fun ncx(title: String, chapters: List<ChapterContent>): String {
        val uid = "wenku8-" + title.replace(Regex("[^0-9A-Za-z]"), "")
        val points = chapters.withIndex().joinToString("\n") { (i, ch) ->
            val n = i + 1
            "<navPoint id=\"n$n\" playOrder=\"$n\"><navLabel><text>${esc(ch.title)}</text></navLabel>" +
                "<content src=\"chapter_$n.xhtml\"/></navPoint>"
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

    private fun chapterXhtml(ch: ChapterContent): String {
        val body = StringBuilder("<h2>${esc(ch.title)}</h2>")
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
