package com.hoshino.wenku8reader.data.local

import com.hoshino.wenku8reader.data.BookInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfConfigTest {

    @Test
    fun exportAndImport_roundTripsShelvesAndBookMembership() {
        val books = listOf(
            LibraryBook(
                book = BookInfo(
                    id = 42,
                    title = "测试书籍",
                    author = "作者",
                    coverUrl = "https://example.com/cover.jpg",
                    tags = listOf("奇幻"),
                ),
                shelves = setOf(DEFAULT_SHELF, "待读"),
                addedAt = 123456L,
            ),
        )
        val restored = decodeShelfConfig(
            encodeShelfConfig(
                shelves = listOf("待读", "已读"),
                books = books,
                exportedAt = 999L,
            ),
        )

        assertEquals(listOf("待读", "已读"), restored?.shelves)
        assertEquals(42, restored?.books?.single()?.book?.id)
        assertEquals(setOf(DEFAULT_SHELF, "待读"), restored?.books?.single()?.shelves)
        assertEquals(123456L, restored?.books?.single()?.addedAt)
    }

    @Test
    fun mergeShelfNames_preservesExistingOrderAndDeduplicates() {
        assertEquals(
            listOf("本地", "已读", "导入"),
            mergeShelfNames(
                current = listOf("本地", "已读"),
                imported = listOf("导入", "已读", "本地"),
            ),
        )
    }

    @Test
    fun rejectsUnknownOrFutureFormats() {
        assertNull(decodeShelfConfig("{}"))
        assertNull(
            decodeShelfConfig(
                """{"format":"wenku8reader.shelf","version":99,"shelves":[],"books":[]}""",
            ),
        )
        val imported = decodeShelfConfig(
            """{"format":"wenku8reader.shelf","version":1,"shelves":[],"books":[]}""",
        )
        assertTrue(imported != null)
    }
}
