package com.hoshino.wenku8reader.data.local

import android.content.Context

/**
 * Persists per-book reading progress and lightweight UI state in SharedPreferences.
 * Replaces ad-hoc SharedPreferences access scattered across the UI layer.
 *
 * 安全说明：此处**不再持久化任何账号密码**。
 * 旧版本曾提供 `saveCredentials/username/password`（明文写入未加密的 `account` 偏好），
 * 经全仓检索确认从未被调用，属于纯粹的安全暴露面，故整体移除——登录态由
 * [com.hoshino.wenku8reader.data.CookieStore] 持久化的会话 Cookie 承担，
 * 无需保存密码即可免登录。
 */
class AppPreferences(context: Context) {

    private val reading = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val ui = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun resumeCid(bookId: Int): String? =
        reading.getString("progress_$bookId", null)

    fun hasProgress(bookId: Int): Boolean =
        reading.contains("progress_$bookId")

    fun saveProgress(bookId: Int, cid: String) {
        reading.edit().putString("progress_$bookId", cid).apply()
    }

    /**
     * 该书的总章节数（用于书架进度：已读章节数 / 总章节数）。
     *
     * 原先这里还有个 `progressPosition` 返回 `(pos, total)`，但 `pos`（章节序号）
     * 全仓没有任何读取点——书架的进度条用的是「已读数 / 总数」，阅读位置另有
     * `resumeCid` 承担。留着两套语义相近的位置数据只会互相漂移，故只保留 total。
     */
    fun progressTotal(bookId: Int): Int =
        reading.getInt("progress_total_$bookId", 0)

    fun saveProgressTotal(bookId: Int, total: Int) {
        reading.edit().putInt("progress_total_$bookId", total).apply()
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
