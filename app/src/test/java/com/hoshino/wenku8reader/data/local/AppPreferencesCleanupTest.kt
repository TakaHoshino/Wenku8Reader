package com.hoshino.wenku8reader.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「清理过期阅读数据」的判定逻辑单测。
 *
 * 这段逻辑会删除用户的阅读进度与已读标记，回归代价极高，因此把判定抽成纯函数
 * （[staleReadingBookIds]）并在这里把边界钉死：旧记录要删、新记录必须留、
 * 没有时间戳的（旧版本写入的）也必须留。
 */
class AppPreferencesCleanupTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000
    private val cutoff30d = now - 30 * dayMs

    private fun prefs(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.toMap()

    @Test
    fun `早于截止时间的书被判为过期`() {
        val all = prefs(
            "progress_at_101" to cutoff30d - 1,
        )
        assertEquals(listOf(101), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `恰好等于截止时间的书仍保留`() {
        // 边界：判定用严格小于，30 天整不清理
        val all = prefs("progress_at_102" to cutoff30d)
        assertEquals(emptyList<Int>(), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `最近读过的书保留`() {
        val all = prefs(
            "progress_at_201" to now,
            "progress_at_202" to now - dayMs,
        )
        assertEquals(emptyList<Int>(), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `没有时间戳的旧记录不清理`() {
        // 本功能上线前写入的进度只有 progress_*/finished_*，没有 progress_at_*，
        // 无法判断新旧 —— 宁可不清理，也不能凭猜测删掉用户正在看的书。
        val all = prefs(
            "progress_301" to "cid-1",
            "finished_301" to "[\"cid-1\"]",
            "progress_total_301" to 120,
        )
        assertEquals(emptyList<Int>(), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `时间戳类型异常时跳过`() {
        val all = prefs(
            "progress_at_401" to "not-a-long",
            "progress_at_402" to null,
        )
        assertEquals(emptyList<Int>(), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `书本 id 非数字时跳过`() {
        val all = prefs(
            "progress_at_abc" to cutoff30d - 1,
            "progress_at_" to cutoff30d - 1,
        )
        assertEquals(emptyList<Int>(), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `与阅读无关的 key 不受影响`() {
        val all = prefs(
            "bookcase_sort" to "recent",
            "progress_501" to "cid-2",
            "some_other_key" to 123L,
        )
        assertEquals(emptyList<Int>(), staleReadingBookIds(all, cutoff30d))
    }

    @Test
    fun `多个书混合时只返回过期的那些`() {
        val all = prefs(
            "progress_at_601" to cutoff30d - dayMs,   // 过期
            "progress_at_602" to now,                 // 新
            "progress_at_603" to cutoff30d - 1,       // 过期
            "progress_at_604" to cutoff30d,           // 边界，保留
            "progress_605" to "cid-3",                // 无时间戳，保留
        )
        assertEquals(listOf(601, 603), staleReadingBookIds(all, cutoff30d).sorted())
    }

    @Test
    fun `空快照返回空`() {
        assertEquals(emptyList<Int>(), staleReadingBookIds(emptyMap(), cutoff30d))
    }
}
