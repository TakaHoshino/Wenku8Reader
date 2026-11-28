package com.hoshino.wenku8reader.data.remote

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume

/**
 * Wenku8 网络数据源契约。
 *
 * 结构参考 LightNovelReader 的 `WebBookDataSource` / `BookRequestDispatcher`：
 * 仓库只依赖该接口，页面、旧 App API 或后续 API Core 都作为可替换数据源接入。
 * 数据源应自行处理传输重试；返回空集合表示“未命中，可尝试下一个源”。
 */
interface Wenku8BookDataSource {
    val id: Int

    /** 日志中可读的数据源名称。 */
    val sourceName: String get() = "source-${id}"

    /** 同一数据源允许的最大并发请求数。 */
    val permits: Int get() = 8

    suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult>
    suspend fun bookInfo(id: Int): BookInfo
    suspend fun chapters(bookId: Int, groupId: Int): List<Volume>
    suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent

    suspend fun bookcase(): List<BookcaseItem>
    suspend fun addToSiteBookcase(bookId: Int)
    suspend fun removeFromSiteBookcase(bookcaseId: Int)

    suspend fun homepage(): List<HomeSection>
    suspend fun tags(): List<String>
    suspend fun tagBooks(tag: String, page: Int): List<HomeBook>

    suspend fun downloadFullTxt(id: Int, type: String): ByteArray
    suspend fun imageBytes(url: String): ByteArray
}
