package com.hoshino.wenku8reader.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 书架与阅读进度的唯一数据访问入口。
 *
 * 读接口同时提供 suspend（一次性）与 [Flow]（观察）两种形态：
 * 页面首帧用一次性读取即可，而「加入/移出书架后详情页按钮要立刻变」「阅读器记录进度后
 * 书架进度条要跟着变」这类跨页面联动只有靠 Flow 才能自动刷新——旧实现靠
 * `refreshLocalState()` 这种手动回调，漏调用就会显示过期状态。
 */
@Dao
interface LibraryDao {

    // ---------------------------------------------------------------- //
    // 书架
    // ---------------------------------------------------------------- //

    /** 按入架时间倒序（新入架在前），与旧书架页的 `sortedByDescending { addedAt }` 一致。 */
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun book(bookId: Int): BookEntity?

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeBook(bookId: Int): Flow<BookEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE id = :bookId)")
    suspend fun containsBook(bookId: Int): Boolean

    @Upsert
    suspend fun upsertBook(book: BookEntity)

    /** 批量写入（迁移导入用）；空列表直接返回，避免无意义的空事务。 */
    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: Int)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun bookCount(): Int

    // ---------------------------------------------------------------- //
    // 阅读进度
    // ---------------------------------------------------------------- //

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun progress(bookId: Int): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observeProgress(bookId: Int): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress")
    suspend fun allProgress(): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress")
    fun observeAllProgress(): Flow<List<ReadingProgressEntity>>

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    /** 批量写入（迁移导入用）；空列表直接返回。 */
    @Upsert
    suspend fun upsertProgressAll(items: List<ReadingProgressEntity>)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: Int)

    /**
     * 按主键批量删除（清理过期阅读数据用）。
     *
     * 哪些算"过期"由 `staleProgressBookIds` 这个**有单测的纯函数**决定，而不是写成 SQL 条件：
     * 这段逻辑会删用户数据，规则必须能在 JVM 单测里把边界钉死。空列表直接返回，
     * 避免生成 `IN ()` 这种 SQLite 不接受的语法。
     */
    @Query("DELETE FROM reading_progress WHERE bookId IN (:bookIds)")
    suspend fun deleteProgressByIds(bookIds: List<Int>)
}
