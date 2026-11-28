package com.hoshino.wenku8reader.data.remote

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.Volume
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Wenku8SourceDispatcherTest {
    @Test
    fun emptySourceFallsThroughToNextSource() = runBlocking {
        val dispatcher = Wenku8SourceDispatcher(
            listOf(
                FakeSource("page", book = BookInfo(1)),
                FakeSource("app", book = BookInfo(1, title = "备用源结果")),
            )
        )

        assertEquals("备用源结果", dispatcher.bookInfo(1).title)
    }

    @Test
    fun failedSourceFallsThroughToNextSource() = runBlocking {
        val dispatcher = Wenku8SourceDispatcher(
            listOf(
                FakeSource("broken", bookFailure = IOException("boom")),
                FakeSource("backup", book = BookInfo(7, title = "可用")),
            )
        )

        assertEquals("可用", dispatcher.bookInfo(7).title)
    }

    @Test
    fun allFailedSourcesSurfaceLastFailure() {
        val dispatcher = Wenku8SourceDispatcher(
            listOf(
                FakeSource("one", bookFailure = IOException("one")),
                FakeSource("two", bookFailure = IOException("two")),
            )
        )

        val error = assertThrows(IOException::class.java) {
            runBlocking { dispatcher.bookInfo(1) }
        }
        assertEquals("two", error.message)
    }

    private class FakeSource(
        name: String,
        private val book: BookInfo = BookInfo(0),
        private val bookFailure: Throwable? = null,
    ) : Wenku8BookDataSource {
        override val id: Int = name.hashCode()

        override suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> =
            emptyList()

        override suspend fun bookInfo(id: Int): BookInfo {
            bookFailure?.let { throw it }
            return book
        }

        override suspend fun chapters(bookId: Int, groupId: Int): List<Volume> = emptyList()

        override suspend fun chapterContent(
            gid: Int,
            bookId: Int,
            cid: String,
        ): ChapterContent = ChapterContent("", "")

        override suspend fun bookcase(): List<BookcaseItem> = emptyList()

        override suspend fun addToSiteBookcase(bookId: Int) = Unit

        override suspend fun removeFromSiteBookcase(bookcaseId: Int) = Unit

        override suspend fun homepage(): List<HomeSection> = emptyList()

        override suspend fun tags(): List<String> = emptyList()

        override suspend fun tagBooks(tag: String, page: Int): List<HomeBook> = emptyList()

        override suspend fun downloadFullTxt(id: Int, type: String): ByteArray = ByteArray(0)

        override suspend fun imageBytes(url: String): ByteArray = ByteArray(0)
    }
}
