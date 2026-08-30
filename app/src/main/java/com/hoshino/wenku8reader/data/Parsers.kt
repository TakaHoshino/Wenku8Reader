package com.hoshino.wenku8reader.data

import java.util.regex.Pattern

/** HTML parsing helpers ported from the Python tool (wenku8/jieqi CMS). */
object Parsers {

    private val WHITESPACE = Regex("\\s+")
    private val ANY_TAG = Regex("<[^>]+>")

    private val SEARCH_CAPTION = Pattern.compile(
        "<caption>.*?结果.*?</caption>\\s*<tr>(.*?)</table>", Pattern.DOTALL
    )
    private val SEARCH_BOOK_LINK = Pattern.compile(
        "href=\"(?:/?)book/(\\d+)\\.htm\"[^>]*>([^<]+)</a>"
    )

    private val BOOK_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL)
    private val TABLE_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL)
    private val TABLE_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL)
    private val BOOK_DESC = Pattern.compile(
        "<span class=\"hottext\">内容简介：</span><br[^>]*?><span[^>]*>(.*?)</span>",
        Pattern.DOTALL
    )
    private val DESC_SPLIT = Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE)
    private val BOOK_COVER = Pattern.compile("<img[^>]+src=\"([^\"]+s\\.jpg)\"")
    private val BOOK_TAGS = Pattern.compile(
        "作品Tags：\\s*(?:</b>)?\\s*(.*?)(?:</b>|<br|</span>|<b\\s)",
        Pattern.DOTALL
    )
    private val GID_FROM_INDEX = Pattern.compile("href=\"/novel/(\\d+)/\\d+/index\\.htm\"")
    private val GID_FROM_CHAPTER = Pattern.compile("href=\"/novel/(\\d+)/\\d+/\\d+\\.htm\"")

    private val HOME_BLOCKTITLE = Pattern.compile(
        "<div class=\"blocktitle\"[^>]*>(.*?)</div>", Pattern.DOTALL
    )
    private val HOME_BOOK_LINK = Pattern.compile(
        "<a[^>]+href=['\"](?:/?)book/(\\d+)\\.htm['\"][^>]*>(.*?)</a>", Pattern.DOTALL
    )
    private val LINK_TITLE = Pattern.compile("title=['\"]([^'\"]*)['\"]")
    private val IMG_SRC = Pattern.compile("<img[^>]+src=['\"]([^'\"]+)['\"]")

    private val TOC_VOLUME_OR_CHAPTER = Pattern.compile(
        "<td[^>]*class=\"(vcss|ccss)\"[^>]*>\\s*(.*?)\\s*</td>", Pattern.DOTALL
    )
    private val TOC_CHAPTER_LINK = Pattern.compile(
        "<a[^>]+href=\"([^\"]+)\"[^>]*>([^<]+?)</a>", Pattern.DOTALL
    )
    private val CHAPTER_ID = Regex("(\\d+)\\.htm$")

    private val CHAPTER_TITLE = Pattern.compile("<div id=\"title\">(.*?)</div>", Pattern.DOTALL)
    private val CHAPTER_IMG = Pattern.compile("<img[^>]+src=\"([^\"]+)\"")
    private val CONTENT_WATERMARK = Regex(
        "<ul[^>]*id=\"contentdp\"[^>]*>.*?</ul>", RegexOption.DOT_MATCHES_ALL
    )
    private val LINE_BREAK = Regex("<br\\s*/?>")
    private val PARAGRAPH_END = Regex("</p>", RegexOption.IGNORE_CASE)

    private val BOOKCASE_LINK = Pattern.compile(
        "<a[^>]+href=\"([^\"]*readbookcase\\.php[^\"]*)\"[^>]*>([^<]+?)</a>", Pattern.DOTALL
    )

    private val TAG_LINK = Pattern.compile(
        "<a[^>]+href=['\"][^'\"]*tags\\.php\\?t=[^'\"]*['\"][^>]*>(.*?)</a>", Pattern.DOTALL
    )

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun clean(s: String): String =
        unescape(s).replace(WHITESPACE, " ").trim()

    private fun stripTags(s: String): String = s.replace(ANY_TAG, "")

    // ------------------------------------------------------------------ //
    fun parseSearchResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<Int>()
        val m = SEARCH_CAPTION.matcher(html)
        if (!m.find()) return results
        val scope = m.group(1)
        val bm = SEARCH_BOOK_LINK.matcher(scope)
        while (bm.find()) {
            val id = bm.group(1).toIntOrNull() ?: continue
            val name = clean(bm.group(2))
            if (name.isEmpty() || name == "我要阅读") continue
            if (!seen.add(id)) continue
            results.add(SearchResult(id, name))
        }
        return results
    }

    // ------------------------------------------------------------------ //
    fun parseBookInfo(html: String, id: Int): BookInfo {
        var title = ""
        val tm = BOOK_TITLE.matcher(html)
        if (tm.find()) {
            val parts = clean(tm.group(1)).split(" - ")
            if (parts.isNotEmpty()) title = parts[0]
        }

        var author = ""
        var category = ""
        var status = ""
        var lastUpdate = ""
        var wordCount = ""
        val rm = TABLE_ROW.matcher(html)
        while (rm.find()) {
            val cm = TABLE_CELL.matcher(rm.group(1))
            while (cm.find()) {
                val cell = clean(stripTags(cm.group(1)))
                when {
                    cell.startsWith("文库分类") && category.isEmpty() ->
                        category = cell.substringAfter("：", "")
                    cell.startsWith("小说作者") && author.isEmpty() ->
                        author = cell.substringAfter("：", "")
                    cell.startsWith("文章状态") && status.isEmpty() ->
                        status = cell.substringAfter("：", "")
                    cell.startsWith("最后更新") && lastUpdate.isEmpty() ->
                        lastUpdate = cell.substringAfter("：", "")
                    cell.startsWith("全文长度") && wordCount.isEmpty() ->
                        wordCount = cell.substringAfter("：", "")
                }
            }
        }

        var desc = ""
        val dm = BOOK_DESC.matcher(html)
        if (dm.find()) {
            val parts = dm.group(1).split(DESC_SPLIT)
            val paras = parts.mapNotNull { p ->
                val t = clean(stripTags(p))
                t.ifEmpty { null }
            }
            desc = paras.joinToString("\n")
        }

        var cover: String? = null
        val cm = BOOK_COVER.matcher(html)
        if (cm.find()) cover = cm.group(1)

        val tags = mutableListOf<String>()
        val tm2 = BOOK_TAGS.matcher(html)
        if (tm2.find()) {
            // 捕获组可能含 <a> 等标签（如 <a href="tags.php?t=穿越">穿越</a>），先剥离再按空白拆分
            ANY_TAG.replace(tm2.group(1), " ").split(WHITESPACE)
                .map { it.trim() }
                .filter { it.isNotEmpty() }.forEach { tags.add(it) }
        }

        var gid: Int? = null
        val gm = GID_FROM_INDEX.matcher(html)
        if (gm.find()) gid = gm.group(1).toIntOrNull()
        if (gid == null) {
            val g2 = GID_FROM_CHAPTER.matcher(html)
            if (g2.find()) gid = g2.group(1).toIntOrNull()
        }

        return BookInfo(id, title, author, category, status, lastUpdate,
            wordCount, desc, cover, gid, tags)
    }

    // ------------------------------------------------------------------ //
    /** Parse the wenku8 homepage (index.php) into its content blocks. */
    fun parseHomepage(html: String): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        // blocks are siblings, each wrapped in <div class="block">…</div>
        val parts = html.split("<div class=\"block\">")
        for (part in parts) {
            val tm = HOME_BLOCKTITLE.matcher(part)
            if (!tm.find()) continue
            val title = clean(stripTags(tm.group(1)))
                .substringBefore('(')
                .substringBefore('（')
                .trim()
            if (title.isEmpty()) continue

            val books = mutableListOf<HomeBook>()
            val seen = mutableSetOf<Int>()
            val bm = HOME_BOOK_LINK.matcher(part)
            while (bm.find()) {
                val whole = bm.group(0)
                val id = bm.group(1).toIntOrNull() ?: continue
                if (!seen.add(id)) continue
                val titleAttr = LINK_TITLE.matcher(whole).let { if (it.find()) it.group(1) else null }
                val name = if (titleAttr != null) clean(titleAttr)
                else clean(stripTags(bm.group(2)))
                val cover = IMG_SRC.matcher(whole).let { if (it.find()) it.group(1) else null }
                if (name.isNotEmpty()) books.add(HomeBook(id, name, cover))
            }
            if (books.isEmpty()) continue
            sections.add(HomeSection(title, books))
        }
        return sections
    }

    // ------------------------------------------------------------------ //
    /** Parse the wenku8 tags page into a list of tag names. */
    fun parseTags(html: String): List<String> {
        val seen = LinkedHashSet<String>()
        val m = TAG_LINK.matcher(html)
        while (m.find()) {
            val name = clean(stripTags(m.group(1)))
                .substringBefore('(')
                .substringBefore('（')
                .trim()
            if (name.isNotEmpty()) seen.add(name)
        }
        return seen.toList()
    }

    /** Parse a book result page (search / tag / list) into books with covers. */
    fun parseBookList(html: String): List<HomeBook> {
        val map = LinkedHashMap<Int, HomeBook>()
        val bm = HOME_BOOK_LINK.matcher(html)
        while (bm.find()) {
            val whole = bm.group(0)
            val id = bm.group(1).toIntOrNull() ?: continue
            val titleAttr = LINK_TITLE.matcher(whole)
                .let { if (it.find()) it.group(1) else null }
            val cover = IMG_SRC.matcher(whole)
                .let { if (it.find()) it.group(1) else null }
            val name = if (titleAttr != null) clean(titleAttr)
            else clean(stripTags(bm.group(2)))
            if (name.isEmpty()) continue
            val existing = map[id]
            if (existing == null) {
                map[id] = HomeBook(id, name, cover)
            } else if (existing.coverUrl == null && cover != null) {
                map[id] = existing.copy(coverUrl = cover)
            }
        }
        return map.values.toList()
    }

    // ------------------------------------------------------------------ //
    fun parseChapterIndex(html: String): List<Volume> {
        val volumes = mutableListOf<Volume>()
        var currentName = ""
        var currentChapters = mutableListOf<Chapter>()
        var hasVolume = false

        fun flush() {
            if (hasVolume || currentChapters.isNotEmpty()) {
                volumes.add(Volume(currentName, currentChapters))
                currentChapters = mutableListOf()
                hasVolume = false
            }
        }

        val m = TOC_VOLUME_OR_CHAPTER.matcher(html)
        while (m.find()) {
            val cls = m.group(1)
            val content = m.group(2)
            if (cls == "vcss") {
                flush()
                currentName = clean(stripTags(content))
                hasVolume = true
                continue
            }
            val am = TOC_CHAPTER_LINK.matcher(content)
            if (am.find()) {
                val cid = CHAPTER_ID.find(am.group(1))?.groupValues?.get(1) ?: continue
                currentChapters.add(Chapter(cid, clean(am.group(2))))
            }
        }
        flush()
        return volumes
    }

    // ------------------------------------------------------------------ //
    fun parseChapter(html: String): ChapterContent {
        var title = ""
        val tm = CHAPTER_TITLE.matcher(html)
        if (tm.find()) title = clean(tm.group(1))

        // Locate the #content region by index. This is robust against nested
        // <div> elements (e.g. <div class="divimage">…</div> for illustrations).
        val marker = "<div id=\"content\">"
        val start = html.indexOf(marker)
        var raw: String? = null
        if (start >= 0) {
            val bodyStart = start + marker.length
            val foot = html.indexOf("<div id=\"footlink\"")
            raw = if (foot > bodyStart) {
                html.substring(bodyStart, foot)
            } else {
                html.substring(bodyStart)
            }
        }
        if (raw == null) return ChapterContent(title, "", emptyList())

        // illustration image urls
        val images = mutableListOf<String>()
        val im = CHAPTER_IMG.matcher(raw)
        while (im.find()) images.add(im.group(1))

        var body = raw.replace(CONTENT_WATERMARK, "")
        body = body.replace(LINE_BREAK, "\n")
        body = body.replace(PARAGRAPH_END, "\n")
        var text = body.replace(ANY_TAG, "")
        text = unescape(text).replace("\u3000", "  ")
        val lines = text.split("\n").map { it.trim() }
        text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return ChapterContent(title, text, images)
    }

    // ------------------------------------------------------------------ //
    fun parseBookcase(html: String): List<BookcaseItem> {
        class Row {
            var aid: Int = 0
            var name: String = ""
            var latest: String? = null
            var latestCid: String? = null
        }
        val rows = LinkedHashMap<String, Row>()
        val m = BOOKCASE_LINK.matcher(html)
        while (m.find()) {
            val href = m.group(1)
            val text = clean(m.group(2))
            val qs = href.substringAfter("?", "")
            val params = qs.split("&")
                .mapNotNull { kv ->
                    val p = kv.split("=", limit = 2)
                    if (p.size == 2) p[0] to p[1] else null
                }.toMap()
            val bid = params["bid"] ?: continue
            val row = rows.getOrPut(bid) { Row().apply { aid = params["aid"]?.toIntOrNull() ?: 0 } }
            if (params.containsKey("cid")) {
                row.latest = text
                row.latestCid = params["cid"]
            } else if (row.name.isEmpty()) {
                row.name = text
            }
        }
        return rows.values.map { BookcaseItem(it.aid, it.name, it.latest, it.latestCid) }
    }

    // ------------------------------------------------------------------ //
    /** Split a full-novel TXT into chapters using the chapter index. */
    fun splitFullTxt(txt: String, volumes: List<Volume>): List<ChapterContent> {
        val text = txt.replace("\r\n", "\n").replace("\r", "\n")
        val lines = text.split("\n")

        val chapters = mutableListOf<Pair<String, String>>() // header -> name
        for (v in volumes) {
            for (ch in v.chapters) {
                chapters.add((v.name + " " + ch.name).trim() to ch.name)
            }
        }

        val headerMap = mutableMapOf<String, MutableList<Int>>()
        lines.forEachIndexed { i, line ->
            val k = line.trim()
            if (k.isNotEmpty()) headerMap.getOrPut(k) { mutableListOf() }.add(i)
        }

        val positions = IntArray(chapters.size) { -1 }
        var cursor = -1
        for ((i, pair) in chapters.withIndex()) {
            val list = headerMap[pair.first] ?: emptyList()
            var found = -1
            for (idx in list) if (idx > cursor) { found = idx; break }
            positions[i] = found
            if (found >= 0) cursor = found
        }

        val next = IntArray(positions.size) { -1 }
        var last: Int? = null
        for (i in positions.indices.reversed()) {
            next[i] = last ?: -1
            if (positions[i] >= 0) last = positions[i]
        }

        val n = lines.size
        val result = mutableListOf<ChapterContent>()
        for ((i, pair) in chapters.withIndex()) {
            val start = positions[i]
            if (start < 0) {
                result.add(ChapterContent(pair.second, ""))
                continue
            }
            val end = if (next[i] >= 0) next[i] else n
            val body = mutableListOf<String>()
            for (li in start + 1 until end) {
                val line = lines[li].trim()
                if (line.isEmpty()) continue
                if (line.contains("轻小说文库") ||
                    line.contains("wenku8", ignoreCase = true) ||
                    line.startsWith("★")) continue
                if (line.all { it == '-' || it == '―' || it == '=' }) continue
                body.add(line)
            }
            result.add(ChapterContent(pair.second, body.joinToString("\n")))
        }
        return result
    }

    // ------------------------------------------------------------------ //
    // 官方 App API（android.php）解析 —— 参考 LightNovelReader 的 Wenku8AppDataSource
    // ------------------------------------------------------------------ //

    private val APP_INPUT = Regex(
        """<input[^>]*name\s*=\s*"([^"]+)"[^>]*value\s*=\s*"([^"]*)"[^>]*/?>""",
        RegexOption.IGNORE_CASE,
    )
    private val APP_VOLUME = Regex(
        """<volume\s+vid="([^"]*)"[^>]*>([\s\S]*?)(?:</volume>|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val APP_CHAPTER = Regex(
        """<chapter\s+cid="([^"]*)"[^>]*>([\s\S]*?)</chapter>""",
        RegexOption.IGNORE_CASE,
    )

    private fun appValue(html: String, name: String): String? =
        APP_INPUT.findAll(html)
            .firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
            ?.groupValues?.get(2)?.let { unescape(stripTags(it)).trim() }

    /** App API `action=book&do=meta` 响应 → 书籍信息（封面按官方 App 规则拼接）。 */
    fun parseAppBookInfo(html: String, id: Int): BookInfo? {
        val title = appValue(html, "Title")?.takeIf { it.isNotBlank() } ?: return null
        val status = appValue(html, "BookStatus") ?: ""
        return BookInfo(
            id = id,
            title = title,
            author = appValue(html, "Author") ?: "",
            category = "",
            status = when (status) {
                "已完成" -> "已完结"
                else -> status
            },
            lastUpdate = appValue(html, "LastUpdate") ?: "",
            wordCount = appValue(html, "BookLength") ?: "",
            description = "",
            coverUrl = "https://img.wenku8.com/image/${id / 1000}/$id/${id}s.jpg",
            groupId = id / 1000,
            tags = (appValue(html, "Tags") ?: "")
                .split(" ")
                .map { it.trim() }
                .filter { it.isNotBlank() },
        )
    }

    /** App API `action=book&do=list` 响应 → 分卷章节树。 */
    fun parseAppVolumes(html: String): List<Volume>? {
        if (!html.contains("<volume", ignoreCase = true)) return null
        val volumes = APP_VOLUME.findAll(html).mapIndexedNotNull { index, vm ->
            val inner = vm.groupValues[2]
            val chapters = APP_CHAPTER.findAll(inner).map { cm ->
                Chapter(
                    cid = cm.groupValues[1].trim(),
                    name = unescape(stripTags(cm.groupValues[2])).trim(),
                )
            }.filter { it.cid.isNotBlank() }.toList()
            if (chapters.isEmpty()) return@mapIndexedNotNull null
            val volName = unescape(stripTags(inner.substringBefore("<chapter"))).trim()
            Volume(name = volName.ifEmpty { "第${index + 1}卷" }, chapters = chapters)
        }.toList()
        return volumes.takeIf { it.isNotEmpty() }
    }

    /**
     * App API `action=book&do=text` 响应 → 章节正文。
     * 格式：第一非空行 = 标题；正文按行组织，`<!--image-->` 分隔插图，
     * 以 http 开头的段落为图片 URL。
     */
    fun parseAppChapter(html: String): ChapterContent? {
        val body = html.substringAfter("<body>", html).substringBefore("</body>")
        val lines = body.split("\n").map { stripTags(it).trim() }
        var title = ""
        val contentLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty()) continue
            if (title.isEmpty()) {
                title = unescape(line)
                continue
            }
            contentLines.add(line)
        }
        if (title.isEmpty()) return null
        val content = contentLines.joinToString("\n")
        val images = mutableListOf<String>()
        val textParts = mutableListOf<String>()
        content.split("<!--image-->").forEach { seg ->
            val s = seg.trim()
            if (s.startsWith("http")) {
                images.add(s)
            } else if (s.isNotBlank()) {
                textParts.add(unescape(s))
            }
        }
        return ChapterContent(
            title = title,
            text = textParts.joinToString("\n"),
            images = images,
        )
    }
}
