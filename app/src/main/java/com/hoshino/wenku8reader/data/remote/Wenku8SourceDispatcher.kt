package com.hoshino.wenku8reader.data.remote

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.log.AppLogger
import kotlin.coroutines.cancellation.CancellationException

/**
 * 多数据源顺序调度，对应 LightNovelReader 的 `BookRequestDispatcher.rotation`。
 *
 * 单个源返回空结果时继续尝试下一个；抛错时也继续尝试，但取消信号必须原样向上抛。
 * 若所有源都抛错，则保留最后一个异常，避免把真实网络故障静默吞成“空数据”。
 */
class Wenku8SourceDispatcher(
    private val sources: List<Wenku8BookDataSource>,
    private val logger: AppLogger? = null,
    override val id: Int = "wenku8-dispatcher".hashCode(),
) : Wenku8BookDataSource {
    init {
        require(sources.isNotEmpty()) { "at least one Wenku8 data source is required" }
    }

    override val permits: Int = sources.maxOf(Wenku8BookDataSource::permits)
    override val sourceName: String = "wenku8-dispatcher"

    override suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> =
        rotate(emptyList()) { this.search(keyword, byAuthor) }

    override suspend fun bookInfo(id: Int): BookInfo =
        rotate(BookInfo(id)) { this.bookInfo(id) }

    override suspend fun chapters(bookId: Int, groupId: Int): List<Volume> =
        rotate(emptyList()) { this.chapters(bookId, groupId) }

    override suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent =
        rotate(ChapterContent("", "")) { this.chapterContent(gid, bookId, cid) }

    override suspend fun bookcase(): List<BookcaseItem> =
        rotate(emptyList()) { this.bookcase() }

    override suspend fun addToSiteBookcase(bookId: Int) {
        rotateUnit { this.addToSiteBookcase(bookId) }
    }

    override suspend fun removeFromSiteBookcase(bookcaseId: Int) {
        rotateUnit { this.removeFromSiteBookcase(bookcaseId) }
    }

    override suspend fun homepage(): List<HomeSection> =
        rotate(emptyList()) { this.homepage() }

    override suspend fun tags(): List<String> =
        rotate(emptyList()) { this.tags() }

    override suspend fun tagBooks(tag: String, page: Int): List<HomeBook> =
        rotate(emptyList()) { this.tagBooks(tag, page) }

    override suspend fun downloadFullTxt(id: Int, type: String): ByteArray =
        rotate(ByteArray(0)) { this.downloadFullTxt(id, type) }

    override suspend fun imageBytes(url: String): ByteArray =
        rotate(ByteArray(0)) { this.imageBytes(url) }

    private suspend fun <T> rotate(
        fallback: T,
        isEmpty: (T) -> Boolean,
        block: suspend Wenku8BookDataSource.() -> T,
    ): T {
        var hadResponse = false
        var lastFailure: Throwable? = null
        var failedSources = 0
        for (source in sources) {
            try {
                val result = source.block()
                hadResponse = true
                if (!isEmpty(result)) {
                    if (failedSources > 0) {
                        logger?.info(
                            "NET",
                            "数据源回退命中 ${source.sourceName}（前序失败 $failedSources 个）",
                        )
                    }
                    return result
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastFailure = error
                failedSources++
                logger?.warn("NET", "数据源 ${source.sourceName} 失败", error)
            }
        }
        if (lastFailure != null && !hadResponse) throw lastFailure
        return fallback
    }

    private suspend fun <T> rotate(
        fallback: T,
        block: suspend Wenku8BookDataSource.() -> T,
    ): T = rotate(
        fallback = fallback,
        isEmpty = { isEmptyValue(it) },
        block = block,
    )

    private suspend fun rotateUnit(block: suspend Wenku8BookDataSource.() -> Unit) {
        rotate(Unit, { false }, block)
    }

    private fun isEmptyValue(value: Any?): Boolean = when (value) {
        null -> true
        is Collection<*> -> value.isEmpty()
        is ByteArray -> value.isEmpty()
        is String -> value.isBlank()
        is BookInfo -> value.title.isBlank()
        is ChapterContent -> value.title.isBlank() && value.text.isBlank() && value.images.isEmpty()
        else -> false
    }
}
