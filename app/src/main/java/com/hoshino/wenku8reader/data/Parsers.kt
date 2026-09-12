package com.hoshino.wenku8reader.data

import java.util.regex.Matcher
import java.util.regex.Pattern

/** HTML parsing helpers ported from the Python tool (wenku8/jieqi CMS). */
object Parsers {

    private val WHITESPACE = Regex("\\s+")
    private val ANY_TAG = Regex("<[^>]+>")

    /**
     * Java `Matcher.group(n)` 是平台类型，Kotlin 推断为 `String?`；
     * 统一非空提取（null → ""），消除 `matcher(CharSequence)` / `clean(String)` 处的空值告警。
     */
    private fun Matcher.groupOrEmpty(n: Int): String = group(n) ?: ""

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
    /**
     * 搜索结果封面 URL：`src="…/image/{gid}/{bookId}/{bookId}s.jpg"`。
     * 组 1 = 封面地址，组 2 = bookId——一次匹配同时拿到两者，
     * 不再对同一个 URL 二次跑正则（原 `COVER_URL_IN_RESULT` + `IMAGE_BOOKID_PATTERN`）。
     * `/image/` 前用 `*` 而非 `+`：站点也会给纯相对路径（`/image/1/2/2s.jpg`），
     * 这种写法才能匹配到，再由 [absolutizeCover] 补全域名。
     */
    private val COVER_URL_IN_RESULT = Pattern.compile(
        "src=\"([^\"]*/image/\\d+/(\\d+)/\\d+s\\.jpg)\""
    )
    private const val IMG_DOMAIN = "https://img.wenku8.com"

    /**
     * 把封面地址补全为绝对 URL。站点在详情页给绝对地址，在标签/书单页给相对路径
     * （`/image/…`），协议相对地址 `//img.…` 也可能出现；不补全的话图片按页面地址解析会 404。
     * `parseSearchResults` 与 `parseBookList` 共用同一实现，避免两处行为漂移。
     */
    private fun absolutizeCover(url: String): String {
        val u = url.trim()
        val absolute = when {
            u.isEmpty() -> u
            // 协议相对地址：补上 https:，不能直接拼域名（否则会变成 https://img…//img…）
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> IMG_DOMAIN + u
            // 已带 scheme（http(s)、data: 等）的地址原样保留，不擅自改域名
            u.contains(":") -> u
            else -> "$IMG_DOMAIN/$u"
        }
        // 站点常给 http:// 图片地址，而应用默认禁止明文流量，必须升级为 https，
        // 否则 Android 会拦截请求、封面全部加载失败。
        return Wenku8Hosts.normalizeImageUrl(absolute)
    }

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
    /** 折叠正文里的连续空行。提到文件级常量，避免 parseChapter 每章都新建一次正则。 */
    private val MULTI_NEWLINE = Regex("\n{3,}")

    private val BOOKCASE_LINK = Pattern.compile(
        "<a[^>]+href=\"([^\"]*readbookcase\\.php[^\"]*)\"[^>]*>([^<]+?)</a>", Pattern.DOTALL
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

    /**
     * 从已命中的"书籍链接" matcher 中提取一条 [HomeBook]：
     * 书名优先取 `<a title="…">`（站点该属性是完整书名，链接文本可能被截断），
     * 缺失时回退到链接标签文本；封面取链接内第一个 `<img src>` 并补全域名。
     * `parseSearchResults` 与 `parseBookList` 共用，避免两套逐行重复的逻辑各自漂移。
     */
    private fun homeBookAt(matcher: Matcher): HomeBook? {
        val whole = matcher.groupOrEmpty(0)
        val id = matcher.groupOrEmpty(1).toIntOrNull() ?: return null
        val titleAttr = LINK_TITLE.matcher(whole)
            .let { if (it.find()) it.groupOrEmpty(1) else null }
        val name = if (titleAttr != null) clean(titleAttr)
        else clean(stripTags(matcher.groupOrEmpty(2)))
        if (name.isEmpty()) return null
        val cover = IMG_SRC.matcher(whole)
            .let { if (it.find()) absolutizeCover(it.groupOrEmpty(1)) else null }
        return HomeBook(id, name, cover)
    }

    // ------------------------------------------------------------------ //
    fun parseSearchResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<Int>()
        val m = SEARCH_CAPTION.matcher(html)
        if (!m.find()) return results
        val scope = m.groupOrEmpty(1)
        // 提取封面：一次正则同时得到封面地址与 bookId（URL 路径第二段），
        // 以 bookId 为 key 建立映射——与下方 SEARCH_BOOK_LINK 的 bookId 对齐。
        val covers = mutableMapOf<Int, String>()
        val im = COVER_URL_IN_RESULT.matcher(scope)
        while (im.find()) {
            val bookId = im.groupOrEmpty(2).toIntOrNull() ?: continue
            covers[bookId] = absolutizeCover(im.groupOrEmpty(1))
        }
        val bm = SEARCH_BOOK_LINK.matcher(scope)
        while (bm.find()) {
            val book = homeBookAt(bm) ?: continue
            if (book.name == "我要阅读") continue
            if (!seen.add(book.id)) continue
            results.add(SearchResult(book.id, book.name, covers[book.id]))
        }
        return results
    }

    // ------------------------------------------------------------------ //
    fun parseBookInfo(html: String, id: Int): BookInfo {
        var title = ""
        val tm = BOOK_TITLE.matcher(html)
        if (tm.find()) {
            val parts = clean(tm.groupOrEmpty(1)).split(" - ")
            if (parts.isNotEmpty()) title = parts[0]
        }

        var author = ""
        var category = ""
        var status = ""
        var lastUpdate = ""
        var wordCount = ""
        val rm = TABLE_ROW.matcher(html)
        while (rm.find()) {
            val cm = TABLE_CELL.matcher(rm.groupOrEmpty(1))
            while (cm.find()) {
                val cell = clean(stripTags(cm.groupOrEmpty(1)))
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
            val parts = dm.groupOrEmpty(1).split(DESC_SPLIT)
            val paras = parts.mapNotNull { p ->
                val t = clean(stripTags(p))
                t.ifEmpty { null }
            }
            desc = paras.joinToString("\n")
        }

        var cover: String? = null
        val cm = BOOK_COVER.matcher(html)
        // 详情页封面是绝对地址，但站点给的是 http://；统一经 absolutizeCover 补全/升级协议
        if (cm.find()) cover = absolutizeCover(cm.groupOrEmpty(1))

        val tags = mutableListOf<String>()
        val tm2 = BOOK_TAGS.matcher(html)
        if (tm2.find()) {
            // 捕获组可能含 <a> 等标签（如 <a href="tags.php?t=穿越">穿越</a>），先剥离再按空白拆分
            ANY_TAG.replace(tm2.groupOrEmpty(1), " ").split(WHITESPACE)
                .map { it.trim() }
                .filter { it.isNotEmpty() }.forEach { tags.add(it) }
        }

        var gid: Int? = null
        val gm = GID_FROM_INDEX.matcher(html)
        if (gm.find()) gid = gm.groupOrEmpty(1).toIntOrNull()
        if (gid == null) {
            val g2 = GID_FROM_CHAPTER.matcher(html)
            if (g2.find()) gid = g2.groupOrEmpty(1).toIntOrNull()
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
            val title = clean(stripTags(tm.groupOrEmpty(1)))
                .substringBefore('(')
                .substringBefore('（')
                .trim()
            if (title.isEmpty()) continue

            val books = mutableListOf<HomeBook>()
            val seen = mutableSetOf<Int>()
            val bm = HOME_BOOK_LINK.matcher(part)
            while (bm.find()) {
                val whole = bm.groupOrEmpty(0)
                val id = bm.groupOrEmpty(1).toIntOrNull() ?: continue
                if (!seen.add(id)) continue
                val titleAttr = LINK_TITLE.matcher(whole).let { if (it.find()) it.groupOrEmpty(1) else null }
                val name = if (titleAttr != null) clean(titleAttr)
                else clean(stripTags(bm.groupOrEmpty(2)))
                val cover = IMG_SRC.matcher(whole).let { if (it.find()) it.groupOrEmpty(1) else null }
                if (name.isNotEmpty()) books.add(HomeBook(id, name, cover))
            }
            if (books.isEmpty()) continue
            sections.add(HomeSection(title, books))
        }
        return sections
    }

    // ------------------------------------------------------------------ //
    /** Parse a book result page (search / tag / list) into books with covers. */
    fun parseBookList(html: String): List<HomeBook> {
        val map = LinkedHashMap<Int, HomeBook>()
        val bm = HOME_BOOK_LINK.matcher(html)
        while (bm.find()) {
            // 书名/封面解析与 parseSearchResults 完全共用 homeBookAt，
            // 封面同样经 absolutizeCover 补全域名（原实现只在搜索结果里补全，
            // 标签书单的相对路径 /image/… 会直接交给 Coil 导致加载失败）。
            val book = homeBookAt(bm) ?: continue
            val existing = map[book.id]
            if (existing == null) {
                map[book.id] = book
            } else if (existing.coverUrl == null && book.coverUrl != null) {
                map[book.id] = existing.copy(coverUrl = book.coverUrl)
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
            val cls = m.groupOrEmpty(1)
            val content = m.groupOrEmpty(2)
            if (cls == "vcss") {
                flush()
                currentName = clean(stripTags(content))
                hasVolume = true
                continue
            }
            val am = TOC_CHAPTER_LINK.matcher(content)
            if (am.find()) {
                val cid = CHAPTER_ID.find(am.groupOrEmpty(1))?.groupValues?.get(1) ?: continue
                currentChapters.add(Chapter(cid, clean(am.groupOrEmpty(2))))
            }
        }
        flush()
        return volumes
    }

    // ------------------------------------------------------------------ //
    fun parseChapter(html: String): ChapterContent {
        var title = ""
        val tm = CHAPTER_TITLE.matcher(html)
        if (tm.find()) title = clean(tm.groupOrEmpty(1))

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

        // illustration image urls（同样可能是 http://，需升级协议，否则被明文流量策略拦截）
        val images = mutableListOf<String>()
        val im = CHAPTER_IMG.matcher(raw)
        while (im.find()) images.add(Wenku8Hosts.normalizeImageUrl(im.groupOrEmpty(1)))

        var body = raw.replace(CONTENT_WATERMARK, "")
        body = body.replace(LINE_BREAK, "\n")
        body = body.replace(PARAGRAPH_END, "\n")
        var text = body.replace(ANY_TAG, "")
        text = unescape(text).replace("\u3000", "  ")
        val lines = text.split("\n").map { it.trim() }
        text = lines.joinToString("\n").replace(MULTI_NEWLINE, "\n\n").trim()
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
            val href = m.groupOrEmpty(1)
            val text = clean(m.groupOrEmpty(2))
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

        val chapters = mutableListOf<Pair<String, String>>() // header -> name
        for (v in volumes) {
            for (ch in v.chapters) {
                chapters.add((v.name + " " + ch.name).trim() to ch.name)
            }
        }

        // 只有目录里真实出现过的章节头文本才需要记录行号。原实现给全文每一行都建
        // "文本 → 行号列表" 的全量 map（几十万条装箱 Integer），这是 10MB 级 TXT 的主要开销；
        // 按章节头过滤后条目数等于章节数，正文行的去重不再需要全量索引。
        val expectedHeaders = HashSet<String>(chapters.size * 2)
        chapters.forEach { expectedHeaders.add(it.first) }

        // 一次遍历同时完成两件事（原实现要对每行 trim 两次）：
        //   1) headerMap：行号 → 去空白后的行文本，正文切片阶段直接按行号取，无需二次 trim；
        //      空白行为 null，等价于原实现里"trim 后为空的行跳过"。
        //      用"按行号索引的 ArrayList"承载（而不是 HashMap<Int, String>）：语义同为
        //      O(1) 随机访问，但不为每个行号装箱 Integer、不建哈希节点——实测后者在 MB 级
        //      文本上反而比旧实现更慢、分配更多，与本次优化的目的相悖。
        //   2) headerLines：章节头文本 → 出现过的行号（升序），用于按游标顺序定位章节起点。
        val headerMap = ArrayList<String?>()
        val headerLines = HashMap<String, MutableList<Int>>()
        // 按 '\n' 手工扫描而不是 lineSequence()/split()：lineSequence 的分隔符序列要为每条
        // 行边界建对象，实测比旧实现还慢；手工扫描只做一次 substring + 一次 trim，
        // 且无需再保留整份"原始行数组"，峰值内存更低。
        var lineStart = 0
        while (true) {
            val nl = text.indexOf('\n', lineStart)
            val end = if (nl < 0) text.length else nl
            val line = text.substring(lineStart, end).trim()
            headerMap.add(line.ifEmpty { null })
            if (line.isNotEmpty() && line in expectedHeaders) {
                headerLines.getOrPut(line) { mutableListOf() }.add(headerMap.size - 1)
            }
            if (nl < 0) break
            lineStart = nl + 1
        }
        val totalLines = headerMap.size

        val positions = IntArray(chapters.size) { -1 }
        var cursor = -1
        for ((i, pair) in chapters.withIndex()) {
            val list = headerLines[pair.first] ?: emptyList()
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

        val result = mutableListOf<ChapterContent>()
        for ((i, pair) in chapters.withIndex()) {
            val start = positions[i]
            if (start < 0) {
                result.add(ChapterContent(pair.second, ""))
                continue
            }
            val end = if (next[i] >= 0) next[i] else totalLines
            val body = mutableListOf<String>()
            for (li in start + 1 until end) {
                // 缺失即为空白行，跳过；已有文本是建索引时 trim 过的，不再重复 trim
                val line = headerMap[li] ?: continue
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
                images.add(Wenku8Hosts.normalizeImageUrl(s))
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
