package com.hoshino.wenku8reader.data.remote

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.Wenku8Client

/**
 * 将当前成熟的 Cronet / WebView / HTML 解析实现适配成标准数据源。
 *
 * 这一步刻意不复制旧客户端逻辑：传输和解析先保持单点，仓库不再直接依赖它；
 * 后续 API Core 或备用站点数据源可以并列注册，由 [Wenku8SourceDispatcher] 轮换。
 */
class Wenku8ClientDataSource(
    private val client: Wenku8Client,
    override val id: Int = "wenku8-page".hashCode(),
) : Wenku8BookDataSource {
    override suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> =
        client.search(keyword, byAuthor)

    override suspend fun bookInfo(id: Int): BookInfo = client.bookInfo(id)

    override suspend fun chapters(bookId: Int, groupId: Int): List<Volume> =
        client.chapters(bookId, groupId)

    override suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent =
        client.chapterContent(gid, bookId, cid)

    override suspend fun bookcase(): List<BookcaseItem> = client.bookcase()

    override suspend fun addToSiteBookcase(bookId: Int) =
        client.addToSiteBookcase(bookId)

    override suspend fun removeFromSiteBookcase(bookcaseId: Int) =
        client.removeFromSiteBookcase(bookcaseId)

    override suspend fun homepage(): List<HomeSection> = client.homepage()

    override suspend fun tags(): List<String> = client.tags()

    override suspend fun tagBooks(tag: String, page: Int): List<HomeBook> =
        client.tagBooks(tag, page)

    override suspend fun downloadFullTxt(id: Int, type: String): ByteArray =
        client.downloadFullTxt(id, type)

    override suspend fun imageBytes(url: String): ByteArray = client.imageBytes(url)
}
