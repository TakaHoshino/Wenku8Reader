package com.hoshino.wenku8reader.data.local

import android.content.Context

/**
 * Persists account credentials and per-book reading progress in SharedPreferences.
 * Replaces ad-hoc SharedPreferences access scattered across the UI layer.
 */
class AppPreferences(context: Context) {

    private val account = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val reading = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val ui = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    val username: String?
        get() = account.getString("username", null)

    val password: String?
        get() = account.getString("password", null)

    fun saveCredentials(username: String, password: String) {
        account.edit()
            .putString("username", username)
            .putString("password", password)
            .apply()
    }

    fun clearAccount() {
        account.edit().clear().apply()
    }

    fun resumeCid(bookId: Int): String? =
        reading.getString("progress_$bookId", null)

    fun hasProgress(bookId: Int): Boolean =
        reading.contains("progress_$bookId")

    fun saveProgress(bookId: Int, cid: String) {
        reading.edit().putString("progress_$bookId", cid).apply()
    }

    fun progressPosition(bookId: Int): Pair<Int, Int> =
        reading.getInt("progress_pos_$bookId", 0) to reading.getInt("progress_total_$bookId", 0)

    fun saveProgressPosition(bookId: Int, pos: Int, total: Int) {
        reading.edit()
            .putInt("progress_pos_$bookId", pos)
            .putInt("progress_total_$bookId", total)
            .apply()
    }

    // ------------------------------------------------------------------ //
    // 章节完成状态（目录页"已读"标记 / 重读重置）
    // 存储：reading 中 "finished_$bookId" = JSONArray(cid, ...)
    // ------------------------------------------------------------------ //

    /** 某书所有已完成章节的 cid 集合。 */
    fun finishedChapters(bookId: Int): Set<String> {
        val raw = reading.getString("finished_$bookId", null) ?: return emptySet()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        }.getOrDefault(emptySet())
    }

    fun isChapterFinished(bookId: Int, cid: String): Boolean =
        cid in finishedChapters(bookId)

    /** 标记章节完成（幂等）。 */
    fun markChapterFinished(bookId: Int, cid: String) {
        val set = finishedChapters(bookId).toMutableSet()
        if (!set.add(cid)) return
        saveFinished(bookId, set)
    }

    /** 重读重置：从完成集合移除该章节（进度回到未完成）。 */
    fun resetChapterFinished(bookId: Int, cid: String) {
        val set = finishedChapters(bookId).toMutableSet()
        if (set.remove(cid)) saveFinished(bookId, set)
    }

    private fun saveFinished(bookId: Int, set: Set<String>) {
        val arr = org.json.JSONArray()
        set.forEach { arr.put(it) }
        reading.edit().putString("finished_$bookId", arr.toString()).apply()
    }

    var bookcaseSortType: String
        get() = ui.getString("bookcase_sort", "default") ?: "default"
        set(value) {
            ui.edit().putString("bookcase_sort", value).apply()
        }

    var bookcaseSortReversed: Boolean
        get() = ui.getBoolean("bookcase_sort_reversed", false)
        set(value) {
            ui.edit().putBoolean("bookcase_sort_reversed", value).apply()
        }

    // ---- 更新检查 ----
    /** 用户选择「跳过该版本」的 release tag（如 v0.2.0）；启动/手动检查时不再提示。 */
    var skippedUpdateVersion: String?
        get() = ui.getString("skipped_update_version", null)
        set(value) {
            ui.edit().putString("skipped_update_version", value).apply()
        }

    /** 上次启动自动检查更新的时间戳（毫秒）；用于节流，降低网络无线电功耗。 */
    var lastUpdateCheckAt: Long
        get() = ui.getLong("last_update_check_at", 0L)
        set(value) {
            ui.edit().putLong("last_update_check_at", value).apply()
        }
}
