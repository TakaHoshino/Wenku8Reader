package com.hoshino.wenku8reader.data

/**
 * 官方 App API 的返回解析（Base64 + 自定义分隔符的 metadata/index/chapter）。
 *
 * 从 `Parsers.kt` 拆出：这段与 HTML 解析无关（输入不是网页，而是 App 协议的文本），
 * 失败一律返回 null 让调用方回退到网页路径，因此放在独立文件里更利于对照阅读。
 * 依赖 `Parsers` 的两个文本清洗 helper（unescape / stripTags），已放宽为 internal。
 */
object AppParsers {
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
            ?.groupValues?.get(2)?.let { Parsers.unescape(Parsers.stripTags(it)).trim() }

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
                    name = Parsers.unescape(Parsers.stripTags(cm.groupValues[2])).trim(),
                )
            }.filter { it.cid.isNotBlank() }.toList()
            if (chapters.isEmpty()) return@mapIndexedNotNull null
            val volName = Parsers.unescape(Parsers.stripTags(inner.substringBefore("<chapter"))).trim()
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
        val lines = body.split("\n").map { Parsers.stripTags(it).trim() }
        var title = ""
        val contentLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty()) continue
            if (title.isEmpty()) {
                title = Parsers.unescape(line)
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
                textParts.add(Parsers.unescape(s))
            }
        }
        return ChapterContent(
            title = title,
            text = textParts.joinToString("\n"),
            images = images,
        )
    }
}
