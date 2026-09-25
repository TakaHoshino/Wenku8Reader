package com.hoshino.wenku8reader.data.local

import com.hoshino.wenku8reader.data.local.db.LibraryDao
import com.hoshino.wenku8reader.data.local.db.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 单本书的阅读进度快照。
 *
 * [lastReadAt] 为 null 表示该记录写于「最后阅读时间」功能上线之前，无法判断新旧，
 * 清理过期数据时必须保留。
 */
data class ReadingProgress(
    val bookId: Int = 0,
    val resumeCid: String? = null,
    val lastReadAt: Long? = null,
    val totalChapters: Int = 0,
    val finishedCids: Set<String> = emptySet(),
) {
    /** 是否读过（详情页「继续阅读 / 开始阅读」文案依据）。 */
    val isStarted: Boolean get() = resumeCid != null

    companion object {
        val EMPTY = ReadingProgress()
    }
}

/**
 * 从进度快照里挑出「最后阅读时间早于 [cutoff]」的书 id。
 *
 * 抽成纯函数的原因：这段逻辑**会删用户数据**，必须能脱离 Android 环境单测
 * （见 `ReadingProgressCleanupTest`）。两条保守规则：
 * 1. 没有时间戳的记录不返回——那是本功能上线前写入的进度，无法判断新旧；
 * 2. 时间戳必须**严格早于** cutoff（`<`）才算过期。
 */
internal fun staleProgressBookIds(rows: Collection<ReadingProgress>, cutoff: Long): List<Int> =
    rows.filter { row ->
        val at = row.lastReadAt ?: return@filter false
        at < cutoff
    }.map { it.bookId }

/**
 * 阅读进度与「已读」标记的读写入口（Room 支撑），取代旧 `AppPreferences` 的 `reading` 偏好。
 *
 * 与旧实现的行为差异只有一处、且是修复：进度不再和「章节已读」标记挤在同一份偏好里，
 * 四个字段合并成一行写入，读者切换章节时不再产生 4 次独立的 SharedPreferences 落盘。
 */
class ReadingProgressStore internal constructor(
    private val dao: LibraryDao,
    private val migration: LocalDataMigration,
) {

    suspend fun read(bookId: Int): ReadingProgress {
        migration.ensure()
        return dao.progress(bookId)?.toProgress() ?: ReadingProgress(bookId = bookId)
    }

    /** 观察某本书的进度（阅读器 / 目录页 / 详情页按钮文案的数据源）。 */
    fun observe(bookId: Int): Flow<ReadingProgress> = flow {
        migration.ensure()
        emitAll(dao.observeProgress(bookId).map { it?.toProgress() ?: ReadingProgress(bookId = bookId) })
    }

    /** 观察全部进度（书架进度条 = 已读章节数 / 总章节数）。 */
    fun observeAll(): Flow<Map<Int, ReadingProgress>> = flow {
        migration.ensure()
        emitAll(
            dao.observeAllProgress().map { rows ->
                rows.associate { row -> row.bookId to row.toProgress() }
            }
        )
    }

    /** 一次性读取全部进度（书架页手动刷新用）。 */
    suspend fun readAll(): Map<Int, ReadingProgress> {
        migration.ensure()
        return dao.allProgress().associate { it.bookId to it.toProgress() }
    }

    /**
     * 打开章节：一次写入「继续阅读位置 + 总章节数 + 最后阅读时间」。
     *
     * 三者在旧实现里是三次独立落盘（`saveProgress` 与 `saveProgressTotal`），
     * 现在合并成一行 upsert。
     */
    suspend fun savePosition(
        bookId: Int,
        cid: String,
        totalChapters: Int,
        at: Long = System.currentTimeMillis(),
    ) {
        edit(bookId) { it.copy(resumeCid = cid, lastReadAt = at, totalChapters = totalChapters) }
    }

    suspend fun updateTotalChapters(bookId: Int, total: Int) {
        edit(bookId) { it.copy(totalChapters = total) }
    }

    /** 某书所有已完成章节的 cid（目录页"已读"标记）。 */
    suspend fun finishedChapters(bookId: Int): Set<String> = read(bookId).finishedCids

    /** 标记章节完成（幂等）。 */
    suspend fun markFinished(bookId: Int, cid: String) {
        edit(bookId) { row ->
            if (cid in row.finishedCids) row else row.copy(finishedCids = row.finishedCids + cid)
        }
    }

    /** 重读重置：从完成集合移除该章节（进度回到未完成）。 */
    suspend fun resetFinished(bookId: Int, cid: String) {
        edit(bookId) { row ->
            if (cid !in row.finishedCids) row else row.copy(finishedCids = row.finishedCids - cid)
        }
    }

    /**
     * 清理「过期阅读数据」：删除最后阅读时间早于 [keepDays] 天的记录。
     *
     * 判定走纯函数 [staleProgressBookIds]（有单测钉边界），删除按主键进行。
     *
     * @return 被清理的书本数量。
     */
    suspend fun cleanupStale(keepDays: Int = DEFAULT_KEEP_DAYS): Int {
        if (keepDays <= 0) return 0
        val cutoff = System.currentTimeMillis() - keepDays * DAY_MS
        val stale = staleProgressBookIds(dao.allProgress().map { it.toProgress() }, cutoff)
        if (stale.isEmpty()) return 0
        dao.deleteProgressByIds(stale)
        return stale.size
    }

    /**
     * 读改写辅助：行不存在时按"全空"起步，与旧偏好里"键不存在"的语义一致。
     *
     * 注意这是非原子的读改写（与旧 SharedPreferences 实现同性质）。实际调用点都在同一个
     * ViewModel 的 viewModelScope 里顺序发生，不会真的并发；若将来出现并发写同一本书，
     * 需要改成 DAO 上的 `@Transaction` 方法。
     */
    private suspend fun edit(
        bookId: Int,
        transform: (ReadingProgressEntity) -> ReadingProgressEntity,
    ) {
        migration.ensure()
        val current = dao.progress(bookId) ?: ReadingProgressEntity(
            bookId = bookId,
            resumeCid = null,
            lastReadAt = null,
            totalChapters = 0,
            finishedCids = emptyList(),
        )
        dao.upsertProgress(transform(current))
    }

    companion object {
        /** 「清理过期阅读数据」默认保留天数。 */
        const val DEFAULT_KEEP_DAYS = 30

        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

private fun ReadingProgressEntity.toProgress(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    resumeCid = resumeCid,
    lastReadAt = lastReadAt,
    totalChapters = totalChapters,
    finishedCids = finishedCids.toSet(),
)
