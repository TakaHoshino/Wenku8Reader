package com.hoshino.wenku8reader.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「清理过期阅读数据」的判定逻辑单测。
 *
 * 这段逻辑会删除用户的阅读进度与已读标记，回归代价极高，因此把判定抽成纯函数
 * （[staleProgressBookIds]）并在这里把边界钉死：旧记录要删、新记录必须留、
 * 没有时间戳的（旧版本写入的）也必须留。
 */
class ReadingProgressCleanupTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000
    private val cutoff30d = now - 30 * dayMs

    private fun progress(bookId: Int, lastReadAt: Long?) =
        ReadingProgress(bookId = bookId, lastReadAt = lastReadAt)

    @Test
    fun `早于截止时间的书被判为过期`() {
        val rows = listOf(progress(101, cutoff30d - 1))
        assertEquals(listOf(101), staleProgressBookIds(rows, cutoff30d))
    }

    @Test
    fun `恰好等于截止时间的书仍保留`() {
        // 边界：判定用严格小于，30 天整不清理
        val rows = listOf(progress(102, cutoff30d))
        assertEquals(emptyList<Int>(), staleProgressBookIds(rows, cutoff30d))
    }

    @Test
    fun `最近读过的书保留`() {
        val rows = listOf(progress(201, now), progress(202, now - dayMs))
        assertEquals(emptyList<Int>(), staleProgressBookIds(rows, cutoff30d))
    }

    @Test
    fun `没有时间戳的旧记录不清理`() {
        // 本功能上线前写入的进度只有 progress_*/finished_*，没有 progress_at_*，
        // 无法判断新旧 —— 宁可不清理，也不能凭猜测删掉用户正在看的书。
        val rows = listOf(
            ReadingProgress(bookId = 301, resumeCid = "cid-1", totalChapters = 120),
        )
        assertEquals(emptyList<Int>(), staleProgressBookIds(rows, cutoff30d))
    }

    @Test
    fun `多个书混合时只返回过期的那些`() {
        val rows = listOf(
            progress(601, cutoff30d - dayMs),   // 过期
            progress(602, now),                 // 新
            progress(603, cutoff30d - 1),       // 过期
            progress(604, cutoff30d),           // 边界，保留
            ReadingProgress(bookId = 605, resumeCid = "cid-3"), // 无时间戳，保留
        )
        assertEquals(listOf(601, 603), staleProgressBookIds(rows, cutoff30d).sorted())
    }

    @Test
    fun `空快照返回空`() {
        assertEquals(emptyList<Int>(), staleProgressBookIds(emptyList(), cutoff30d))
    }
}
