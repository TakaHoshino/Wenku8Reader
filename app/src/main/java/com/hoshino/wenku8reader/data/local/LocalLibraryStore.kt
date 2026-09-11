package com.hoshino.wenku8reader.data.local

import android.content.Context
import com.hoshino.wenku8reader.data.BookInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in the on-device bookshelf.
 *
 * 说明：这里**只保存书目快照与入架信息**。阅读进度（上次章节、已读章节、进度位置）
 * 的唯一事实来源是 [AppPreferences]（`reading` 偏好）。此前本类还保存
 * `lastReadCid/progressPos/progressTotal` 并在 `add()` 时"保留旧值"，
 * 但那套数据从未被读取、也从不与 `reading` 同步，属于必然长期不一致的第二套存储，已移除。
 */
data class LibraryBook(
    val book: BookInfo,
    val shelf: String = "默认",
    val addedAt: Long = 0L,
)

/**
 * On-device bookshelf backed by SharedPreferences. Stores a snapshot of each
 * book's details so the shelf renders offline without hitting the network.
 *
 * 读取走内存缓存：`contains`/`all` 在详情页与书架页高频调用，而 SharedPreferences
 * 里存的是整份 JSON，每次调用都重新全量解析（百本规模下开销明显）。
 * 写操作在落盘的同时同步更新缓存，因此不会出现「写完再读要重新解析」的浪费。
 */
class LocalLibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    /** 解析结果缓存；null 表示尚未加载。 */
    @Volatile
    private var cache: Map<Int, LibraryBook>? = null

    fun all(): List<LibraryBook> = snapshot().values.toList()

    fun contains(bookId: Int): Boolean = bookId in snapshot()

    /** Adds the book, preserving an existing entry's shelf and added-time. */
    @Synchronized
    fun add(book: BookInfo, shelf: String = "默认") {
        val map = snapshot().toMutableMap()
        val prev = map[book.id]
        map[book.id] = LibraryBook(
            book = book,
            shelf = shelf,
            addedAt = prev?.addedAt ?: System.currentTimeMillis(),
        )
        save(map)
    }

    @Synchronized
    fun remove(bookId: Int) {
        val map = snapshot().toMutableMap()
        if (map.remove(bookId) == null) return
        save(map)
    }

    // ------------------------------------------------------------------ //

    private fun snapshot(): Map<Int, LibraryBook> =
        cache ?: synchronized(this) {
            cache ?: parse().also { cache = it }
        }

    private fun parse(): Map<Int, LibraryBook> {
        val raw = prefs.getString("data", null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val out = LinkedHashMap<Int, LibraryBook>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val entry = fromJson(o)
                out[entry.book.id] = entry
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** 落盘并同步刷新内存缓存（两者始终一致）。 */
    private fun save(map: Map<Int, LibraryBook>) {
        val arr = JSONArray()
        map.values.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("data", arr.toString()).apply()
        cache = map
    }

    private fun toJson(b: LibraryBook): JSONObject = JSONObject()
        .put("id", b.book.id)
        .put("title", b.book.title)
        .put("author", b.book.author)
        .put("cover", b.book.coverUrl)
        .put("status", b.book.status)
        .put("lastUpdate", b.book.lastUpdate)
        .put("wordCount", b.book.wordCount)
        .put("desc", b.book.description)
        .put("gid", b.book.groupId ?: -1)
        .put("tags", JSONArray(b.book.tags))
        .put("shelf", b.shelf)
        .put("time", b.addedAt)

    private fun fromJson(o: JSONObject): LibraryBook {
        val tags = mutableListOf<String>()
        val ta = o.optJSONArray("tags")
        if (ta != null) {
            for (i in 0 until ta.length()) tags.add(ta.optString(i))
        }
        val gid = o.optInt("gid", -1)
        return LibraryBook(
            book = BookInfo(
                id = o.optInt("id"),
                title = o.optString("title"),
                author = o.optString("author"),
                status = o.optString("status"),
                lastUpdate = o.optString("lastUpdate"),
                wordCount = o.optString("wordCount"),
                description = o.optString("desc"),
                coverUrl = o.optString("cover").ifEmpty { null },
                groupId = if (gid >= 0) gid else null,
                tags = tags,
            ),
            shelf = o.optString("shelf", "默认"),
            addedAt = o.optLong("time"),
        )
    }
}
