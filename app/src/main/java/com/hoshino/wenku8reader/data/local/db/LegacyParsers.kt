package com.hoshino.wenku8reader.data.local.db

import com.hoshino.wenku8reader.data.Wenku8Hosts
import org.json.JSONArray
import org.json.JSONObject

/**
 * 旧存储 → Room 实体的纯解析函数。
 *
 * 抽成纯函数（只吃 `String` / `Map`，不碰 `Context`）有两个理由：
 * 1. 这段代码决定用户书架与阅读进度能否完整搬到新库，必须在 JVM 单测里逐字段钉死；
 * 2. 迁移逻辑本身要能脱离 Android 环境跑（见 `LegacyMigration`）。
 *
 * 这里刻意**逐字段对应**旧的 `LocalLibraryStore.fromJson` / `AppPreferences` 的读法，
 * 包括它们的容错行为（脏数据返回空、同 id 后者覆盖前者），迁移不是"顺便改进格式"的时机。
 *
 * 返回值都带「解析是否完整」的信息：迁移靠它决定能不能清理旧数据。
 * 只丢掉无法解析的条目而不作声，等于静默销毁用户数据。
 */

/**
 * 书架 JSON 的解析结果。
 *
 * @param books 成功解析出的书目（同 id 后者覆盖前者，与旧实现一致）。
 * @param readable 整份 JSON 是否可解析；false 表示内容已损坏、一个条目都读不出来。
 * @param droppedEntries 可解析但被跳过的条目数（例如数组里混进了非对象元素）。
 */
internal data class LegacyLibraryParse(
    val books: List<BookEntity>,
    val readable: Boolean,
    val droppedEntries: Int,
) {
    /** 只有"完整解析且非空"才允许清理旧数据。 */
    val canClear: Boolean get() = readable && droppedEntries == 0 && books.isNotEmpty()

    companion object {
        /** 没有旧数据。 */
        val ABSENT = LegacyLibraryParse(emptyList(), readable = true, droppedEntries = 0)
    }
}

/** 把 `library` 偏好里的 JSON 数组解析成书目快照。 */
internal fun parseLegacyLibrary(raw: String?): LegacyLibraryParse {
    if (raw.isNullOrEmpty()) return LegacyLibraryParse.ABSENT
    val arr = runCatching { JSONArray(raw) }.getOrElse {
        return LegacyLibraryParse(emptyList(), readable = false, droppedEntries = 0)
    }
    // 与旧实现一致：同一 bookId 在数组里出现多次时，后者覆盖前者
    val out = LinkedHashMap<Int, BookEntity>(arr.length())
    var dropped = 0
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i)
        if (o == null) {
            dropped++
            continue
        }
        val entry = legacyBook(o)
        out[entry.id] = entry
    }
    return LegacyLibraryParse(out.values.toList(), readable = true, droppedEntries = dropped)
}

private fun legacyBook(o: JSONObject): BookEntity {
    val tags = mutableListOf<String>()
    o.optJSONArray("tags")?.let { ta ->
        for (i in 0 until ta.length()) tags.add(ta.optString(i))
    }
    val gid = o.optInt("gid", -1)
    return BookEntity(
        id = o.optInt("id"),
        title = o.optString("title"),
        author = o.optString("author"),
        status = o.optString("status"),
        lastUpdate = o.optString("lastUpdate"),
        wordCount = o.optString("wordCount"),
        // 旧键名是 desc，不是 description：写错不会报错，只会让详情页描述变空
        description = o.optString("desc"),
        // 与旧 fromJson 一致，读出来就把 http:// 就地升级为 https://
        coverUrl = Wenku8Hosts.normalizeImageUrl(o.optString("cover")).ifEmpty { null },
        groupId = if (gid >= 0) gid else null,
        tags = tags,
        shelf = o.optString("shelf", "默认"),
        addedAt = o.optLong("time"),
    )
}

/**
 * 阅读进度的解析结果。
 *
 * @param rows 解析出的进度行。
 * @param droppedEntries `finished_<id>` 内容损坏而无法解析的条目数（那些 cid 读不回来）。
 */
internal data class LegacyReadingParse(
    val rows: List<ReadingProgressEntity>,
    val droppedEntries: Int,
) {
    /** 只有一条都没丢才允许清理旧键。 */
    val canClear: Boolean get() = droppedEntries == 0
}

/**
 * 把 `reading` 偏好的全量快照解析成进度行。
 *
 * 四类键在旧存储里彼此独立：可能有 `finished_` 而没有 `progress_`，也可能有 `progress_`
 * 而没有时间戳（本功能上线前写入）。因此先按 key 收集涉及的 bookId，再逐 id 取值，
 * 缺哪个就是哪个的默认值，绝不臆造。
 *
 * 只由 `progress_total_<id> = 0` 这类空记录构成的行也会被读出来（值与"没有这行"等价），
 * 这样清理旧键时不会漏掉尾巴、下次启动也就不必再反复迁移。
 */
internal fun parseLegacyReading(all: Map<String, Any?>): LegacyReadingParse {
    var dropped = 0
    val rows = all.keys
        .mapNotNull(::legacyBookIdOf)
        .toSortedSet()
        .map { id -> legacyProgress(id, all) { dropped++ } }
    return LegacyReadingParse(rows, droppedEntries = dropped)
}

private fun legacyProgress(
    bookId: Int,
    all: Map<String, Any?>,
    onDroppedFinished: () -> Unit,
): ReadingProgressEntity {
    val finished = parseLegacyCidArray(
        raw = all["$LEGACY_KEY_FINISHED$bookId"] as? String,
        onFailure = onDroppedFinished,
    )
    return ReadingProgressEntity(
        bookId = bookId,
        // 与旧的 `contains("progress_$id")` 判定一致：空字符串也算"有进度"
        resumeCid = all["$LEGACY_KEY_RESUME_CID$bookId"] as? String,
        lastReadAt = all["$LEGACY_KEY_PROGRESS_AT$bookId"] as? Long,
        totalChapters = (all["$LEGACY_KEY_PROGRESS_TOTAL$bookId"] as? Int) ?: 0,
        finishedCids = finished,
    )
}

/**
 * `finished_<id>` 存的是 JSON 数组。解析失败仍退化为空集合（与旧实现一致，否则用户连
 * 书架都打不开），但通过 [onFailure] 上报，让迁移**保留**旧键而不是把读不懂的内容抹掉。
 */
private fun parseLegacyCidArray(raw: String?, onFailure: () -> Unit): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it) }
    }.getOrElse {
        onFailure()
        emptyList()
    }
}
