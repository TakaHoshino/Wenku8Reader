package com.hoshino.wenku8reader.data.local

import com.hoshino.wenku8reader.data.BookInfo
import org.json.JSONArray
import org.json.JSONObject

internal const val SHELF_CONFIG_FORMAT = "wenku8reader.shelf"
internal const val SHELF_CONFIG_VERSION = 1

internal data class ShelfConfig(
    val shelves: List<String>,
    val books: List<LibraryBook>,
)

internal fun encodeShelfConfig(
    shelves: List<String>,
    books: List<LibraryBook>,
    exportedAt: Long = System.currentTimeMillis(),
): String {
    val normalizedShelves = shelves
        .map(::normalizeShelfName)
        .filter { it.isNotEmpty() && it != DEFAULT_SHELF && it.length <= SHELF_NAME_MAX_LENGTH }
        .distinct()
    val root = JSONObject()
        .put("format", SHELF_CONFIG_FORMAT)
        .put("version", SHELF_CONFIG_VERSION)
        .put("exportedAt", exportedAt)
        .put("shelves", JSONArray(normalizedShelves))
    val booksJson = JSONArray()
    books.forEach { entry ->
        val book = entry.book
        booksJson.put(
            JSONObject()
                .put("id", book.id)
                .put("title", book.title)
                .put("author", book.author)
                .put("status", book.status)
                .put("lastUpdate", book.lastUpdate)
                .put("wordCount", book.wordCount)
                .put("description", book.description)
                .put("coverUrl", book.coverUrl ?: JSONObject.NULL)
                .put("groupId", book.groupId ?: JSONObject.NULL)
                .put("tags", JSONArray(book.tags))
                .put("shelves", JSONArray(entry.shelves.toList()))
                .put("addedAt", entry.addedAt),
        )
    }
    return root.put("books", booksJson).toString(2)
}

internal fun decodeShelfConfig(raw: String): ShelfConfig? = runCatching {
    val root = JSONObject(raw)
    if (root.optString("format") != SHELF_CONFIG_FORMAT) return null
    if (root.optInt("version", -1) !in 1..SHELF_CONFIG_VERSION) return null
    val shelves = root.optJSONArray("shelves")
        ?.let(::stringList)
        .orEmpty()
        .map(::normalizeShelfName)
        .filter { it.isNotEmpty() && it != DEFAULT_SHELF && it.length <= SHELF_NAME_MAX_LENGTH }
        .distinct()
    val allowedShelves = (shelves + DEFAULT_SHELF).toSet()
    val books = root.optJSONArray("books")
        ?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    val title = item.optString("title").trim()
                    if (id <= 0 || title.isEmpty()) continue
                    val membership = stringList(item.optJSONArray("shelves"))
                        .map(::normalizeShelfName)
                        .filter { it in allowedShelves }
                        .toSet()
                        .ifEmpty { setOf(DEFAULT_SHELF) }
                    add(
                        LibraryBook(
                            book = BookInfo(
                                id = id,
                                title = title,
                                author = item.optString("author"),
                                status = item.optString("status"),
                                lastUpdate = item.optString("lastUpdate"),
                                wordCount = item.optString("wordCount"),
                                description = item.optString("description"),
                                coverUrl = item.nullableString("coverUrl"),
                                groupId = item.takeUnless { it.isNull("groupId") }?.optInt("groupId"),
                                tags = stringList(item.optJSONArray("tags")),
                            ),
                            shelves = membership,
                            addedAt = item.optLong("addedAt", 0L),
                        ),
                    )
                }
            }
        }
        .orEmpty()
    ShelfConfig(shelves = shelves, books = books)
}.getOrNull()

internal fun mergeShelfNames(current: List<String>, imported: List<String>): List<String> =
    (current + imported)
        .map(::normalizeShelfName)
        .filter { it.isNotEmpty() && it != DEFAULT_SHELF && it.length <= SHELF_NAME_MAX_LENGTH }
        .distinct()

private fun stringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
