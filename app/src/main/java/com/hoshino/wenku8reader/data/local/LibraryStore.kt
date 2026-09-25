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
 * 与旧 `LocalLibraryStore.LibraryBook` 同形，调用方（书架页 / 详情页）无需改动。
 */
data class LibraryBook(
    val book: BookInfo,
    val shelf: String = "默认",
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
     * 加入书架。已存在时**保留原来的入架时间**（否则书架排序会把它当成新书跳到最前），
     * 书目快照则用新数据覆盖——与旧 `LocalLibraryStore.add` 的语义一致。
     */
    suspend fun add(book: BookInfo, shelf: String = "默认") {
        migration.ensure()
        val previous = dao.book(book.id)
        dao.upsertBook(
            book.toEntity(shelf = shelf, addedAt = previous?.addedAt ?: System.currentTimeMillis()),
        )
    }

    /** 移出书架。只动书架，不碰阅读进度（与旧实现一致）。 */
    suspend fun remove(bookId: Int) {
        migration.ensure()
        dao.deleteBook(bookId)
    }

    /**
     * 把一本书移动到另一个书架（多书架功能）。
     *
     * 与 [add] 的区别：这里**只改归属**，既不动书目快照也不动入架时间。
     */
    suspend fun moveToShelf(bookId: Int, shelf: String) {
        migration.ensure()
        dao.updateShelf(bookId, shelf)
    }

    /**
     * 把整个书架的书一次性移回 [to]。
     *
     * 删除书架时必须**先**调用它、再改书架清单：反过来的话，中途失败会让那批书
     * 指向一个已不存在的书架（虽然展示层有兜底，但用户会看到一个空书架）。
     */
    suspend fun moveShelf(from: String, to: String) {
        migration.ensure()
        dao.moveShelf(from, to)
    }

    /**
     * 书架重命名。
     *
     * 与 [moveShelf] 是同一条 SQL（批量改 `shelf` 列），单独留个名字只是为了让调用点
     * 读起来是它本来的意思：**重命名必须同时改书的归属**，否则那批书会指向一个
     * 已不存在的书架（展示层虽有兜底，但用户会看到书架凭空清空）。
     */
    suspend fun renameShelf(from: String, to: String) {
        migration.ensure()
        dao.moveShelf(from, to)
    }
}

private fun BookEntity.toLibraryBook(): LibraryBook =
    LibraryBook(book = toBookInfo(), shelf = shelf, addedAt = addedAt)
