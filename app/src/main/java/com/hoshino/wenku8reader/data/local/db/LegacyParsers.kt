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
 */

/** 把 `library` 偏好里的 JSON 数组解析成书目快照；任何异常退化为空列表。 */
internal fun parseLegacyLibrary(raw: String?): List<BookEntity> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        // 与旧实现一致：同一 bookId 在数组里出现多次时，后者覆盖前者
        val out = LinkedHashMap<Int, BookEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val entry = legacyBook(o)
            out[entry.id] = entry
        }
        out.values.toList()
    }.getOrDefault(emptyList())
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
 * 把 `reading` 偏好的全量快照解析成进度行。
 *
 * 四类键在旧存储里彼此独立：可能有 `finished_` 而没有 `progress_`，也可能有 `progress_`
 * 而没有时间戳（本功能上线前写入）。因此先按 key 收集涉及的 bookId，再逐 id 取值，
 * 缺哪个就是哪个的默认值，绝不臆造。
 */
internal fun parseLegacyReading(all: Map<String, Any?>): List<ReadingProgressEntity> =
    all.keys
        .mapNotNull(::legacyBookIdOf)
        .toSortedSet()
        .map { id -> legacyProgress(id, all) }
        .filterNot { it.isEmpty }

private fun legacyProgress(bookId: Int, all: Map<String, Any?>): ReadingProgressEntity =
    ReadingProgressEntity(
        bookId = bookId,
        // 与旧的 `contains("progress_$id")` 判定一致：空字符串也算"有进度"
        resumeCid = all["$LEGACY_KEY_RESUME_CID$bookId"] as? String,
        lastReadAt = all["$LEGACY_KEY_PROGRESS_AT$bookId"] as? Long,
        totalChapters = (all["$LEGACY_KEY_PROGRESS_TOTAL$bookId"] as? Int) ?: 0,
        finishedCids = parseLegacyCidArray(all["$LEGACY_KEY_FINISHED$bookId"] as? String),
    )

/** `finished_<id>` 存的是 JSON 数组；解析失败退化为空集合（与旧实现一致）。 */
private fun parseLegacyCidArray(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it) }
    }.getOrDefault(emptyList())
}
