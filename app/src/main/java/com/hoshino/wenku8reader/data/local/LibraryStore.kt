package com.hoshino.wenku8reader.data.local

import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.local.db.BookEntity
import com.hoshino.wenku8reader.data.local.db.LibraryDao
import com.hoshino.wenku8reader.data.local.db.toBookInfo
import com.hoshino.wenku8reader.data.local.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 书架条目：书目快照 + 入架信息。
 *
 * [shelves] 是**归属集合**（一本书可以同时在多个书架）；它直接来自持久化解析，可能含
 * 已经不存在的书架名，展示/统计前请先用 `ShelfOps.normalizeMembership` 过滤。
 */
data class LibraryBook(
    val book: BookInfo,
    val shelves: Set<String> = setOf(DEFAULT_SHELF),
    val addedAt: Long = 0L,
)

/**
 * 本地书架的读写入口（Room 支撑）。
 *
 * 相比旧的 `LocalLibraryStore`：不再把整份 JSON 读出来全量解析——旧的 `contains()`
 * 在详情页与书架页高频调用，百本规模下每次都要重建整个 map；现在按主键查、由 SQLite 索引兜底。
 *
 * 读接口一律先过 [LocalDataMigration.ensure]，保证首帧就落在"已搬迁完成"的状态上。
 */
class LibraryStore internal constructor(
    private val dao: LibraryDao,
    private val migration: LocalDataMigration,
) {

    suspend fun all(): List<LibraryBook> {
        migration.ensure()
        return dao.books().map { it.toLibraryBook() }
    }

    /** 观察整份书架（按入架时间倒序，新入架在前）。 */
    fun observeAll(): Flow<List<LibraryBook>> = flow {
        migration.ensure()
        emitAll(dao.observeBooks().map { rows -> rows.map { it.toLibraryBook() } })
    }

    suspend fun contains(bookId: Int): Boolean {
        migration.ensure()
        return dao.containsBook(bookId)
    }

    /** 观察某本书是否在书架（详情页收藏星标的数据源）。 */
    fun observeContains(bookId: Int): Flow<Boolean> = flow {
        migration.ensure()
        emitAll(dao.observeBook(bookId).map { it != null })
    }

    /**
     * 观察某本书的整条书架条目。
     *
     * 详情页既要判断"在不在书架"，也要知道"在哪个书架"——取消收藏时的确认弹窗要用勾选框
     * 指出它当前的位置（见 `ShelfPickerDialog`）。一个 Flow 同时满足两件事，
     * 不必再单独订一次 [observeContains]。
     */
    fun observeBook(bookId: Int): Flow<LibraryBook?> = flow {
        migration.ensure()
        emitAll(dao.observeBook(bookId).map { it?.toLibraryBook() })
    }

    /**
     * 加入书架（可一次加入多个）。已存在时**保留原来的入架时间**（否则书架排序会把它当成
     * 新书跳到最前），书目快照与归属则用新数据覆盖——与旧 `LocalLibraryStore.add` 的语义一致。
     */
    suspend fun add(book: BookInfo, shelves: Collection<String> = listOf(DEFAULT_SHELF)) {
        migration.ensure()
        val previous = dao.book(book.id)
        dao.upsertBook(
            book.toEntity(
                shelfValue = encodeMembership(shelves),
                addedAt = previous?.addedAt ?: System.currentTimeMillis(),
            ),
        )
    }

    /** 移出书架。只动书架，不碰阅读进度（与旧实现一致）。 */
    suspend fun remove(bookId: Int) {
        migration.ensure()
        dao.deleteBook(bookId)
    }

    /**
     * 覆盖一本书的归属（多书架功能：勾选哪些书架就属于哪些）。
     *
     * 与 [add] 的区别：这里**只改归属**，既不动书目快照也不动入架时间。
     * 阅读进度不受影响——它按 bookId 存在 `reading_progress` 里，多书架共用同一份。
     */
    suspend fun setShelves(bookId: Int, shelves: Collection<String>) {
        migration.ensure()
        dao.updateShelves(bookId, encodeMembership(shelves))
    }

    /**
     * 删除书架后清理归属：把所有还挂着 [name] 的书摘掉它（摘空则回退默认书架）。
     *
     * 必须**先**调用它、再改书架清单：反过来的话中途失败会让那批书指向一个已不存在的书架
     * （展示层虽有兜底，但用户会看到书架凭空清空）。集合语义下这是一次"读回来逐条改写"，
     * 不再是单条 `UPDATE ... WHERE shelf = ?`。
     */
    suspend fun removeShelfFromAll(name: String) {
        migration.ensure()
        val changed = dao.books()
            .filter { name in parseMembership(it.shelf) }
            .map { it.copy(shelf = encodeMembership(withShelfRemoved(parseMembership(it.shelf), name))) }
        if (changed.isNotEmpty()) dao.upsertBooks(changed)
    }

    /** 书架改名后同步所有书的归属（同上：先改书，再改书架清单）。 */
    suspend fun renameShelfInAll(from: String, to: String) {
        migration.ensure()
        val changed = dao.books()
            .filter { from in parseMembership(it.shelf) }
            .map {
                it.copy(shelf = encodeMembership(withShelfRenamedIn(parseMembership(it.shelf), from, to)))
            }
        if (changed.isNotEmpty()) dao.upsertBooks(changed)
    }
}

private fun BookEntity.toLibraryBook(): LibraryBook =
    LibraryBook(book = toBookInfo(), shelves = parseMembership(shelf).toSet(), addedAt = addedAt)
