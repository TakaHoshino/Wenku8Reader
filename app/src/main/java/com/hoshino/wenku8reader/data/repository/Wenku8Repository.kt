package com.hoshino.wenku8reader.data.repository

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.Wenku8Client

/**
 * Single source of truth for wenku8 data. Exposes suspend functions that return
 * [Result] so the UI layer owns error handling without leaking network types.
 */
class Wenku8Repository(private val client: Wenku8Client) {

    suspend fun isLoggedIn(): Result<Boolean> =
        runCatching { client.isLoggedIn() }

    suspend fun login(username: String, password: String): Result<Boolean> =
        runCatching { client.login(username, password) }

    suspend fun logout(): Result<Unit> =
        runCatching { client.logout() }

    suspend fun search(keyword: String, byAuthor: Boolean): Result<List<SearchResult>> =
        runCatching { client.search(keyword, byAuthor) }

    suspend fun bookInfo(id: Int): Result<BookInfo> =
        runCatching { client.bookInfo(id) }

    suspend fun chapters(bookId: Int, groupId: Int): Result<List<Volume>> =
        runCatching { client.chapters(bookId, groupId) }

    suspend fun chapterContent(gid: Int, bookId: Int, cid: String): Result<ChapterContent> =
        runCatching { client.chapterContent(gid, bookId, cid) }

    suspend fun bookcase(): Result<List<BookcaseItem>> =
        runCatching { client.bookcase() }

    suspend fun homepage(): Result<List<HomeSection>> =
        runCatching { client.homepage() }

    suspend fun tags(): Result<List<String>> =
        runCatching { client.tags() }

    suspend fun tagBooks(tag: String): Result<List<HomeBook>> =
        runCatching { client.tagBooks(tag) }

    suspend fun downloadFullTxt(id: Int, type: String): Result<ByteArray> =
        runCatching { client.downloadFullTxt(id, type) }

    /** The `gid` required by /novel/ URLs. Falls back to id/1000 when the detail page omits it. */
    fun groupIdOf(info: BookInfo): Int =
        info.groupId ?: (info.id / 1000).coerceAtLeast(1)
}
