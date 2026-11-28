package com.hoshino.wenku8reader.data.repository

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.remote.Wenku8DataSourceProvider
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single source of truth for wenku8 data. Exposes suspend functions that return
 * [Result] so the UI layer owns error handling without leaking network types.
 *
 * 网络入口只依赖 [Wenku8DataSourceProvider]，不再知道 Cronet、网页解析或旧 App API。
 * 账号会话仍由独立的客户端管理；仓库层不暴露 login/logout API。
 */
class Wenku8Repository(private val network: Wenku8DataSourceProvider) {

    suspend fun search(keyword: String, byAuthor: Boolean): Result<List<SearchResult>> =
        runCatchingNotCancelling { network.highPriority.search(keyword, byAuthor) }

    suspend fun bookInfo(id: Int): Result<BookInfo> =
        runCatchingNotCancelling { network.highPriority.bookInfo(id) }

    suspend fun chapters(bookId: Int, groupId: Int): Result<List<Volume>> =
        runCatchingNotCancelling { network.default.chapters(bookId, groupId) }

    suspend fun chapterContent(gid: Int, bookId: Int, cid: String): Result<ChapterContent> =
        runCatchingNotCancelling { network.highPriority.chapterContent(gid, bookId, cid) }

    suspend fun bookcase(): Result<List<BookcaseItem>> =
        runCatchingNotCancelling { network.default.bookcase() }

    /** 加入站方书架（按**书 id**）。 */
    suspend fun addToSiteBookcase(bookId: Int): Result<Unit> =
        runCatchingNotCancelling { network.default.addToSiteBookcase(bookId) }

    /** 从站方书架移出（按**书架记录 id**，即 `BookcaseItem.bid`）。 */
    suspend fun removeFromSiteBookcase(bookcaseId: Int): Result<Unit> =
        runCatchingNotCancelling { network.default.removeFromSiteBookcase(bookcaseId) }

    suspend fun homepage(): Result<List<HomeSection>> =
        runCatchingNotCancelling { network.default.homepage() }

    suspend fun tags(): Result<List<String>> =
        runCatchingNotCancelling { network.default.tags() }

    suspend fun tagBooks(tag: String, page: Int = 1): Result<List<HomeBook>> =
        runCatchingNotCancelling { network.default.tagBooks(tag, page) }

    suspend fun downloadFullTxt(id: Int, type: String): Result<ByteArray> =
        runCatchingNotCancelling { network.lowPriority.downloadFullTxt(id, type) }

    suspend fun imageBytes(url: String): Result<ByteArray> =
        runCatchingNotCancelling { network.lowPriority.imageBytes(url) }

    /** The `gid` required by /novel/ URLs. Falls back to id/1000 when the detail page omits it. */
    fun groupIdOf(info: BookInfo): Int = bookGroupId(info)
}

/**
 * 网络层的 `runCatching`：**不吞协程取消**。
 *
 * 标准 `runCatching` 捕获 `Throwable`，会把 `CancellationException` 也包成
 * `Result.failure`；调用方（如下载任务、阅读器加载）随后会把"取消"误判成
 * "网络失败"并继续执行后续逻辑，取消信号就此丢失。
 */
private inline fun <T> runCatchingNotCancelling(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
