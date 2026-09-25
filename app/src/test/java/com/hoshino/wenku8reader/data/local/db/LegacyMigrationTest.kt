package com.hoshino.wenku8reader.data.local.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧 SharedPreferences → Room 的一次性迁移单测。
 *
 * 这是全项目**回归代价最高**的一段代码：它搬的是用户唯一的书架与阅读记录，一旦
 * "导入不完整却清理了旧数据"，用户的书就永久没了。因此这里把三件事钉死：
 * 1. 逐字段搬迁正确（含旧键名 `desc`/`gid=-1`/`time`、http 封面升级）；
 * 2. 幂等——重复执行不产生重复行；
 * 3. 任何失败或解析不完整，都**不清理旧数据**。
 */
class LegacyMigrationTest {

    // ------------------------------------------------------------------ //
    // 书架解析
    // ------------------------------------------------------------------ //

    @Test
    fun `书架条目逐字段搬迁`() {
        val parse = parseLegacyLibrary(libraryJson(bookJson()))

        assertTrue("完整解析的旧数据允许清理", parse.canClear)
        assertEquals(1, parse.books.size)
        val book = parse.books.single()
        assertEquals(101, book.id)
        assertEquals("剑来", book.title)
        assertEquals("烽火戏诸侯", book.author)
        assertEquals("连载中", book.status)
        assertEquals("2026-09-01", book.lastUpdate)
        assertEquals("123万", book.wordCount)
        // 旧 JSON 的键名是 desc，不是 description——写错只会静默丢描述
        assertEquals("简介文本", book.description)
        assertEquals(listOf("仙侠", "热血"), book.tags)
        assertEquals("默认", book.shelf)
        assertEquals(1_700_000_000_000L, book.addedAt)
        assertEquals(20, book.groupId)
        // 与旧 fromJson 一致：读出来就把明文封面升级为 https
        assertEquals("https://img.wenku8.com/p/101.jpg", book.coverUrl)
    }

    @Test
    fun `gid 缺省时分组为空且不臆造 0`() {
        val parse = parseLegacyLibrary(libraryJson(bookJson(gid = -1)))
        assertNull(parse.books.single().groupId)
    }

    @Test
    fun `同 id 重复条目以后者为准`() {
        val parse = parseLegacyLibrary(
            libraryJson(
                bookJson(id = 7, title = "旧标题"),
                bookJson(id = 7, title = "新标题"),
            )
        )
        assertEquals(1, parse.books.size)
        assertEquals("新标题", parse.books.single().title)
    }

    @Test
    fun `书架 JSON 损坏时标记不可解析`() {
        val parse = parseLegacyLibrary("{不是数组")
        assertFalse(parse.readable)
        assertTrue(parse.books.isEmpty())
        assertFalse("读不出来的旧数据绝不能清", parse.canClear)
    }

    @Test
    fun `数组里混入非对象条目时标记丢条`() {
        val raw = "[${bookJson(id = 1)},42]"
        val parse = parseLegacyLibrary(raw)
        assertEquals(1, parse.books.size)
        assertEquals(1, parse.droppedEntries)
        assertFalse("有丢条就不能清旧数据", parse.canClear)
    }

    @Test
    fun `没有旧书架数据时视为缺省`() {
        val parse = parseLegacyLibrary(null)
        assertTrue(parse.readable)
        assertEquals(0, parse.droppedEntries)
        assertTrue(parse.books.isEmpty())
        assertFalse("没有数据就没有可清理的对象", parse.canClear)
    }

    // ------------------------------------------------------------------ //
    // 阅读进度解析
    // ------------------------------------------------------------------ //

    @Test
    fun `阅读进度四类键合并成一行`() {
        val at = 1_700_000_000_000L
        val parse = parseLegacyReading(
            mapOf(
                "progress_101" to "cid-a",
                "progress_at_101" to at,
                "progress_total_101" to 88,
                "finished_101" to JSONArray(listOf("c1", "c2")).toString(),
            )
        )
        assertTrue(parse.canClear)
        val row = parse.rows.single()
        assertEquals(101, row.bookId)
        assertEquals("cid-a", row.resumeCid)
        assertEquals(at, row.lastReadAt)
        assertEquals(88, row.totalChapters)
        assertEquals(listOf("c1", "c2"), row.finishedCids)
    }

    @Test
    fun `没有时间戳的旧进度保留为空时间戳`() {
        // 「最后阅读时间」上线前写入的进度：只有 cid，没有 progress_at_
        val parse = parseLegacyReading(mapOf("progress_202" to "cid-b"))
        val row = parse.rows.single()
        assertEquals("cid-b", row.resumeCid)
        // 不能补一个「现在」的时间戳——那会让清理逻辑误删/误留
        assertNull(row.lastReadAt)
        assertEquals(0, row.totalChapters)
        assertTrue(row.finishedCids.isEmpty())
    }

    @Test
    fun `只有已读标记的书也会搬迁`() {
        val parse = parseLegacyReading(
            mapOf("finished_303" to JSONArray(listOf("c9")).toString())
        )
        val row = parse.rows.single()
        assertEquals(303, row.bookId)
        assertNull(row.resumeCid)
        assertEquals(listOf("c9"), row.finishedCids)
    }

    @Test
    fun `非进度键被忽略`() {
        val parse = parseLegacyReading(
            mapOf("bookcase_sort" to "latest", "skipped_update_version" to "v0.1.0")
        )
        assertTrue(parse.rows.isEmpty())
        assertEquals(0, parse.droppedEntries)
    }

    @Test
    fun `时间戳类型不是 Long 时视为缺失`() {
        // 旧偏好里若混进字符串/空值，读取必须退化为"没有时间戳"（→ 清理时保留），
        // 而不是抛异常或猜一个数字。
        val parse = parseLegacyReading(
            mapOf(
                "progress_401" to "cid-401",
                "progress_at_401" to "not-a-long",
                "progress_402" to "cid-402",
                "progress_at_402" to null,
            )
        )
        assertEquals(2, parse.rows.size)
        assertTrue(parse.rows.all { it.lastReadAt == null })
        assertEquals(listOf("cid-401", "cid-402"), parse.rows.map { it.resumeCid }.sortedBy { it })
    }

    @Test
    fun `已读标记损坏时标记丢条并保留旧键`() {
        val parse = parseLegacyReading(mapOf("finished_404" to "{不是数组"))
        assertEquals(1, parse.rows.size)
        assertEquals(1, parse.droppedEntries)
        assertFalse("读不出来的已读标记必须先留着", parse.canClear)
    }

    @Test
    fun `进度键前缀解析不会互相串味`() {
        // progress_at_ 的前缀里含 progress_：匹配顺序错了就会把 at_101 当成 bookId
        assertEquals(101, legacyBookIdOf("progress_at_101"))
        assertEquals(101, legacyBookIdOf("progress_total_101"))
        assertEquals(101, legacyBookIdOf("finished_101"))
        assertEquals(101, legacyBookIdOf("progress_101"))
        assertNull(legacyBookIdOf("progress_"))
        assertNull(legacyBookIdOf("bookcase_sort"))
    }

    // ------------------------------------------------------------------ //
    // 迁移编排：导入 → 校验 → 才算清理
    // ------------------------------------------------------------------ //

    @Test
    fun `没有旧数据时什么都不做`() = runBlocking {
        val dao = FakeLibraryDao()
        val legacy = FakeLegacySource()

        assertEquals(MigrationResult.NothingToDo, migrateLegacyStores(dao, legacy))
        assertTrue(dao.bookRows.isEmpty())
        assertTrue(dao.progressRows.isEmpty())
        assertFalse(legacy.clearedLibrary)
    }

    @Test
    fun `迁移成功后写入 Room 并清理旧数据`() = runBlocking {
        val dao = FakeLibraryDao()
        val legacy = FakeLegacySource(
            libraryRaw = libraryJson(bookJson(id = 1), bookJson(id = 2, title = "第二本")),
            readingRaw = mapOf(
                "progress_1" to "cid-1",
                "progress_at_1" to 1_700_000_000_000L,
                "progress_total_1" to 10,
                "finished_1" to JSONArray(listOf("c1")).toString(),
                "progress_2" to "cid-2",
            ),
        )

        val result = migrateLegacyStores(dao, legacy)

        assertEquals(MigrationResult.Imported(books = 2, progressRows = 2), result)
        assertEquals(setOf(1, 2), dao.bookRows.keys)
        assertEquals(2, dao.progressRows.size)
        assertEquals("cid-2", dao.progressRows[2]?.resumeCid)
        assertEquals(listOf("c1"), dao.progressRows[1]?.finishedCids)
        assertTrue("校验通过才允许清理", legacy.clearedLibrary)
        assertEquals(setOf(1, 2), legacy.clearedProgressIds)
        assertNull("旧键应被清空，避免下次重复迁移", legacy.libraryRaw)
        assertTrue(legacy.readingRaw.isEmpty())
    }

    @Test
    fun `清理成功后再跑一次是空操作`() = runBlocking {
        val dao = FakeLibraryDao()
        val legacy = FakeLegacySource(libraryRaw = libraryJson(bookJson(id = 1)))

        migrateLegacyStores(dao, legacy)
        val second = migrateLegacyStores(dao, legacy)

        assertEquals(MigrationResult.NothingToDo, second)
        assertEquals(1, dao.bookRows.size)
    }

    @Test
    fun `清理失败导致重跑时不产生重复行`() = runBlocking {
        val dao = FakeLibraryDao()
        val legacy = FakeLegacySource(
            libraryRaw = libraryJson(bookJson(id = 1)),
            readingRaw = mapOf("progress_1" to "cid-1"),
        ).apply { clearFails = true }

        val first = migrateLegacyStores(dao, legacy)
        // 第一次：数据已写进去，但旧数据没清掉 → 上报失败（不是"什么都没做"）
        assertTrue(first is MigrationResult.Failed)
        assertEquals(1, (first as MigrationResult.Failed).importedBooks)
        assertFalse(legacy.clearedLibrary)
        assertEquals(1, dao.bookRows.size)

        legacy.clearFails = false
        val second = migrateLegacyStores(dao, legacy)

        assertEquals(MigrationResult.Imported(books = 1, progressRows = 1), second)
        // 重跑必须走主键 upsert，不能多出重复行
        assertEquals(1, dao.bookRows.size)
        assertEquals(1, dao.progressRows.size)
    }

    @Test
    fun `写入抛异常时不清理旧数据`() = runBlocking {
        val dao = FakeLibraryDao().apply { failWrites = RuntimeException("模拟磁盘写失败") }
        val legacy = FakeLegacySource(
            libraryRaw = libraryJson(bookJson(id = 1)),
            readingRaw = mapOf("progress_1" to "cid-1"),
        )

        val result = migrateLegacyStores(dao, legacy)

        assertTrue(result is MigrationResult.Failed)
        assertFalse("导入没成功就绝不能清旧数据", legacy.clearedLibrary)
        assertNull(legacy.clearedProgressIds)
        assertTrue(dao.bookRows.isEmpty())
    }

    @Test
    fun `静默丢写导致校验不通过时不清理旧数据`() = runBlocking {
        val dao = FakeLibraryDao().apply { dropWrites = true }
        val legacy = FakeLegacySource(libraryRaw = libraryJson(bookJson(id = 1)))

        val result = migrateLegacyStores(dao, legacy)

        assertTrue("读回比对不上必须报失败", result is MigrationResult.Failed)
        assertFalse("校验没过就不能清旧数据", legacy.clearedLibrary)
        assertNotNull("旧数据必须原样留着，等下次启动重试", legacy.libraryRaw)
        // 校验没过时不应上报「已导入」
        assertEquals(0, (result as MigrationResult.Failed).importedBooks)
    }

    @Test
    fun `含丢条时导入能读的部分但保留旧数据`() = runBlocking {
        val dao = FakeLibraryDao()
        val legacy = FakeLegacySource(
            libraryRaw = "[${bookJson(id = 1)},42]",
        )

        val result = migrateLegacyStores(dao, legacy)

        assertTrue(result is MigrationResult.Failed)
        assertEquals(1, (result as MigrationResult.Failed).importedBooks)
        assertEquals("能读出来的书先搬过去，不能让用户觉得书丢了", setOf(1), dao.bookRows.keys)
        assertFalse("但旧数据要留着", legacy.clearedLibrary)
    }

    @Test
    fun `书架 JSON 损坏但仍有进度时只搬进度并保留旧书架`() = runBlocking {
        val dao = FakeLibraryDao()
        val legacy = FakeLegacySource(
            libraryRaw = "{不是数组",
            readingRaw = mapOf("progress_1" to "cid-1"),
        )

        val result = migrateLegacyStores(dao, legacy)

        assertTrue(result is MigrationResult.Failed)
        assertEquals(1, dao.progressRows.size)
        assertFalse(legacy.clearedLibrary)
        assertEquals(setOf(1), legacy.clearedProgressIds)
    }

    // ------------------------------------------------------------------ //
    // 测试替身
    // ------------------------------------------------------------------ //

    private fun libraryJson(vararg entries: JSONObject): String =
        JSONArray(entries.toList()).toString()

    private fun bookJson(
        id: Int = 101,
        title: String = "剑来",
        author: String = "烽火戏诸侯",
        cover: String = "http://img.wenku8.com/p/101.jpg",
        status: String = "连载中",
        lastUpdate: String = "2026-09-01",
        wordCount: String = "123万",
        desc: String = "简介文本",
        gid: Int = 20,
        tags: List<String> = listOf("仙侠", "热血"),
        shelf: String = "默认",
        time: Long = 1_700_000_000_000L,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("author", author)
        .put("cover", cover)
        .put("status", status)
        .put("lastUpdate", lastUpdate)
        .put("wordCount", wordCount)
        .put("desc", desc)
        .put("gid", gid)
        .put("tags", JSONArray(tags))
        .put("shelf", shelf)
        .put("time", time)

    /** 内存版 DAO；[dropWrites]/[failWrites] 用来模拟数据库侧的故障。 */
    private class FakeLibraryDao : LibraryDao {

        val bookRows = LinkedHashMap<Int, BookEntity>()
        val progressRows = LinkedHashMap<Int, ReadingProgressEntity>()
        var dropWrites = false
        var failWrites: Throwable? = null

        private fun guardWrites() {
            failWrites?.let { throw it }
        }

        override suspend fun books(): List<BookEntity> =
            bookRows.values.sortedByDescending { it.addedAt }

        override fun observeBooks(): Flow<List<BookEntity>> =
            flowOf(bookRows.values.sortedByDescending { it.addedAt })

        override suspend fun book(bookId: Int): BookEntity? = bookRows[bookId]

        override fun observeBook(bookId: Int): Flow<BookEntity?> = flowOf(bookRows[bookId])

        override suspend fun containsBook(bookId: Int): Boolean = bookId in bookRows

        override suspend fun upsertBook(book: BookEntity) {
            guardWrites()
            if (!dropWrites) bookRows[book.id] = book
        }

        override suspend fun upsertBooks(books: List<BookEntity>) {
            guardWrites()
            if (dropWrites) return
            books.forEach { bookRows[it.id] = it }
        }

        override suspend fun deleteBook(bookId: Int) {
            bookRows.remove(bookId)
        }

        override suspend fun bookCount(): Int = bookRows.size

        override suspend fun progress(bookId: Int): ReadingProgressEntity? = progressRows[bookId]

        override fun observeProgress(bookId: Int): Flow<ReadingProgressEntity?> =
            flowOf(progressRows[bookId])

        override suspend fun allProgress(): List<ReadingProgressEntity> = progressRows.values.toList()

        override fun observeAllProgress(): Flow<List<ReadingProgressEntity>> =
            flowOf(progressRows.values.toList())

        override suspend fun upsertProgress(progress: ReadingProgressEntity) {
            guardWrites()
            if (!dropWrites) progressRows[progress.bookId] = progress
        }

        override suspend fun upsertProgressAll(items: List<ReadingProgressEntity>) {
            guardWrites()
            if (dropWrites) return
            items.forEach { progressRows[it.bookId] = it }
        }

        override suspend fun deleteProgress(bookId: Int) {
            progressRows.remove(bookId)
        }

        override suspend fun deleteProgressByIds(bookIds: List<Int>) {
            bookIds.forEach { progressRows.remove(it) }
        }
    }

    /** 内存版旧存储；[clearFails] 用来模拟清理时的写入失败。 */
    private class FakeLegacySource(
        var libraryRaw: String? = null,
        var readingRaw: Map<String, Any?> = emptyMap(),
    ) : LegacySource {

        var clearedLibrary = false
        var clearedProgressIds: Set<Int>? = null
        var clearFails = false

        override fun libraryJson(): String? = libraryRaw

        override fun readingSnapshot(): Map<String, Any?> = readingRaw

        override fun clearLibrary() {
            if (clearFails) throw IllegalStateException("模拟清理失败")
            clearedLibrary = true
            libraryRaw = null
        }

        override fun clearReading(bookIds: Set<Int>) {
            if (clearFails) throw IllegalStateException("模拟清理失败")
            clearedProgressIds = bookIds
            readingRaw = readingRaw.filterKeys { legacyBookIdOf(it) !in bookIds }
        }
    }
}
