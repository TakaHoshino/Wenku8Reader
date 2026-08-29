package com.hoshino.wenku8reader.data.local

import android.content.Context
import com.hoshino.wenku8reader.data.BookInfo
import org.json.JSONArray
import org.json.JSONObject

/** One entry in the on-device bookshelf. */
data class LibraryBook(
    val book: BookInfo,
    val shelf: String = "默认",
    val lastReadCid: String? = null,
    val progressPos: Int = 0,
    val progressTotal: Int = 0,
    val addedAt: Long = 0L,
)

/**
 * On-device bookshelf backed by SharedPreferences. Stores a snapshot of each
 * book's details plus the local reading progress.
 */
class LocalLibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun all(): List<LibraryBook> = load()

    fun contains(bookId: Int): Boolean = load().any { it.book.id == bookId }

    /** Adds the book, preserving an existing entry's shelf and progress. */
    fun add(book: BookInfo, shelf: String = "默认") {
        val map = loadMap()
        val prev = map[book.id]
        map[book.id] = LibraryBook(
            book = book,
            shelf = shelf,
            lastReadCid = prev?.lastReadCid,
            progressPos = prev?.progressPos ?: 0,
            progressTotal = prev?.progressTotal ?: 0,
            addedAt = prev?.addedAt ?: System.currentTimeMillis(),
        )
        save(map)
    }

    /** Adds the book only if it is not already present. */
    fun ensure(book: BookInfo) {
        val map = loadMap()
        if (book.id in map) return
        map[book.id] = LibraryBook(book = book, addedAt = System.currentTimeMillis())
        save(map)
    }

    fun updateProgress(bookId: Int, cid: String, pos: Int, total: Int) {
        val map = loadMap()
        val cur = map[bookId] ?: return
        map[bookId] = cur.copy(lastReadCid = cid, progressPos = pos, progressTotal = total)
        save(map)
    }

    fun remove(bookId: Int) {
        val map = loadMap()
        map.remove(bookId)
        save(map)
    }

    // ------------------------------------------------------------------ //
    private fun loadMap(): MutableMap<Int, LibraryBook> =
        load().associateBy { it.book.id }.toMutableMap()

    private fun load(): List<LibraryBook> {
        val raw = prefs.getString("data", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(map: Map<Int, LibraryBook>) {
        val arr = JSONArray()
        map.values.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("data", arr.toString()).apply()
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
        .put("cid", b.lastReadCid)
        .put("pos", b.progressPos)
        .put("total", b.progressTotal)
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
            lastReadCid = o.optString("cid").ifEmpty { null },
            progressPos = o.optInt("pos"),
            progressTotal = o.optInt("total"),
            addedAt = o.optLong("time"),
        )
    }
}
