package com.hoshino.wenku8reader.ui.reader

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.hoshino.wenku8reader.data.ChapterContent

/** A single page in side-swipe (page-turn) mode. */
sealed interface ReaderPage {
    data class Text(val text: String) : ReaderPage
    data class Image(val url: String) : ReaderPage
}

/**
 * Splits a chapter into pages based on a dynamically computed character count per page:
 *   charsPerLine  ≈ pageWidth / fontSize (slightly conservative to avoid clipping)
 *   linesPerPage  ≈ pageHeight / lineHeight
 * then walks the text line by line (respecting '\n' and partial lines from short
 * paragraphs) and breaks a page when its line count reaches [linesPerPage].
 *
 * This keeps the char-count-based pagination while ensuring every page's rendered
 * line count never exceeds the page capacity, so no lines get clipped.
 *
 * Runs off the main thread. Images become their own pages.
 */
fun paginateChapter(
    density: Density,
    chapter: ChapterContent,
    maxWidthPx: Int,
    maxHeightPx: Int,
    fontSizeSp: Int,
    lineSpacing: Float,
): List<ReaderPage> {
    if (maxWidthPx <= 0 || maxHeightPx <= 0) return emptyList()

    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    val lineHeightPx = with(density) { (fontSizeSp * lineSpacing).sp.toPx() }

    // 每行可容纳字数（按当前页面宽度与字号动态估算，留 5% 余量避免溢出裁切）
    val charsPerLine = (maxWidthPx.toFloat() / fontSizePx * 0.95f).toInt().coerceAtLeast(1)
    // 每页可容纳行数
    val linesPerPage = (maxHeightPx.toFloat() / lineHeightPx).toInt().coerceAtLeast(1)

    val text = buildString {
        if (chapter.title.isNotBlank()) {
            append(chapter.title)
            append('\n')
        }
        append(chapter.text)
    }

    val pages = mutableListOf<ReaderPage>()
    if (text.isNotBlank()) {
        var start = 0
        var line = 0
        var lineChars = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') {
                line++
                lineChars = 0
                i++
                if (line >= linesPerPage) {
                    val pageText = text.substring(start, i).trim('\n')
                    if (pageText.isNotEmpty()) pages.add(ReaderPage.Text(pageText))
                    start = i
                    line = 0
                }
            } else {
                lineChars++
                if (lineChars >= charsPerLine) {
                    line++
                    lineChars = 0
                    if (line >= linesPerPage) {
                        val pageText = text.substring(start, i + 1).trim('\n')
                        if (pageText.isNotEmpty()) pages.add(ReaderPage.Text(pageText))
                        start = i + 1
                        line = 0
                    }
                }
                i++
            }
        }
        val tail = text.substring(start).trim('\n')
        if (tail.isNotEmpty()) pages.add(ReaderPage.Text(tail))
    }
    if (pages.isEmpty() && chapter.images.isEmpty()) pages.add(ReaderPage.Text(""))
    chapter.images.forEach { url -> pages.add(ReaderPage.Image(url)) }
    return pages
}
