package com.hoshino.wenku8reader.data.remote

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * 高/默认/低优先级数据源入口，对应参考实现的 `WebBookDataSourceProvider`。
 *
 * 阅读器与详情页使用 high，普通浏览使用 default，批量下载与图片使用 low；
 * 三类入口共享一个按优先级唤醒的并发闸门，避免下载任务占满连接后饿死前台阅读。
 */
class Wenku8DataSourceProvider(
    source: Wenku8BookDataSource,
) {
    private val gate = Wenku8PrioritySemaphore(source.permits)

    val highPriority: Wenku8BookDataSource =
        PriorityDataSource(source, gate, HIGH_PRIORITY)
    val default: Wenku8BookDataSource =
        PriorityDataSource(source, gate, DEFAULT_PRIORITY)
    val lowPriority: Wenku8BookDataSource =
        PriorityDataSource(source, gate, LOW_PRIORITY)

    private class PriorityDataSource(
        private val delegate: Wenku8BookDataSource,
        private val gate: Wenku8PrioritySemaphore,
        private val priority: Int,
    ) : Wenku8BookDataSource by delegate {
        private suspend fun <T> guard(block: suspend () -> T): T =
            withContext(Dispatchers.IO) {
                gate.withPermit(priority, block)
            }

        override suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> =
            guard { delegate.search(keyword, byAuthor) }

        override suspend fun bookInfo(id: Int): BookInfo =
            guard { delegate.bookInfo(id) }

        override suspend fun chapters(bookId: Int, groupId: Int): List<Volume> =
            guard { delegate.chapters(bookId, groupId) }

        override suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent =
            guard { delegate.chapterContent(gid, bookId, cid) }

        override suspend fun bookcase(): List<BookcaseItem> =
            guard { delegate.bookcase() }

        override suspend fun addToSiteBookcase(bookId: Int) =
            guard { delegate.addToSiteBookcase(bookId) }

        override suspend fun removeFromSiteBookcase(bookcaseId: Int) =
            guard { delegate.removeFromSiteBookcase(bookcaseId) }

        override suspend fun homepage(): List<HomeSection> =
            guard { delegate.homepage() }

        override suspend fun tags(): List<String> =
            guard { delegate.tags() }

        override suspend fun tagBooks(tag: String, page: Int): List<HomeBook> =
            guard { delegate.tagBooks(tag, page) }

        override suspend fun downloadFullTxt(id: Int, type: String): ByteArray =
            guard { delegate.downloadFullTxt(id, type) }

        override suspend fun imageBytes(url: String): ByteArray =
            guard { delegate.imageBytes(url) }
    }

    private companion object {
        const val HIGH_PRIORITY = 3
        const val DEFAULT_PRIORITY = 2
        const val LOW_PRIORITY = 1
    }
}

/**
 * 小型的公平优先级信号量。阻塞发生在数据源的 IO 调度器内，调用方仍使用 suspend API。
 */
private class Wenku8PrioritySemaphore(permits: Int) {
    private val lock = ReentrantLock(true)
    private val condition = lock.newCondition()
    private val sequence = AtomicLong(0)
    private val waiting = PriorityBlockingQueue<WaitingRequest>()
    private var available = permits.coerceAtLeast(1)

    suspend fun <T> withPermit(priority: Int, block: suspend () -> T): T {
        runInterruptible { acquire(priority) }
        return try {
            block()
        } finally {
            release()
        }
    }

    private fun acquire(priority: Int) {
        lock.withLock {
            val request = WaitingRequest(priority, sequence.getAndIncrement())
            waiting.add(request)
            try {
                while (waiting.peek() != request || available <= 0) {
                    condition.await()
                }
                available--
                waiting.remove(request)
                signalNext()
            } catch (error: InterruptedException) {
                waiting.remove(request)
                signalNext()
                Thread.currentThread().interrupt()
                throw error
            }
        }
    }

    private fun release() {
        lock.withLock {
            available++
            signalNext()
        }
    }

    private fun signalNext() {
        if (waiting.isNotEmpty() && available > 0) condition.signalAll()
    }

    private data class WaitingRequest(
        val priority: Int,
        val sequence: Long,
    ) : Comparable<WaitingRequest> {
        override fun compareTo(other: WaitingRequest): Int {
            if (priority != other.priority) return other.priority - priority
            return sequence.compareTo(other.sequence)
        }
    }
}
