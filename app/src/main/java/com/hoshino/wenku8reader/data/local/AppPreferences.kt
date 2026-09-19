package com.hoshino.wenku8reader.data.local

import android.content.Context

/** 最后阅读时间戳的 key 前缀（`progress_at_{bookId}`）。 */
internal const val KEY_PROGRESS_AT = "progress_at_"

/**
 * 从 SharedPreferences 的全量快照中挑出「最后阅读时间早于 [cutoff]」的书 id。
 *
 * 抽成纯函数的原因：这段逻辑**会删用户数据**，必须能脱离 Android 环境单测
 * （见 `AppPreferencesCleanupTest`）。两条保守规则：
 * 1. 没有时间戳的记录不返回——那是本功能上线前写入的进度，无法判断新旧；
 * 2. 时间戳必须**严格早于** cutoff（`<`）才算过期。
 */
internal fun staleReadingBookIds(all: Map<String, Any?>, cutoff: Long): List<Int> =
    all.entries
        .filter { (key, _) -> key.startsWith(KEY_PROGRESS_AT) }
        .mapNotNull { (key, value) ->
            val at = value as? Long ?: return@mapNotNull null
            if (at >= cutoff) return@mapNotNull null
            key.removePrefix(KEY_PROGRESS_AT).toIntOrNull()
        }

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

    /**
     * 保存阅读进度，并记录**最后阅读时间**（`progress_at_$bookId`）。
     *
     * 时间戳是「清理过期阅读数据」的唯一依据：没有它就只能靠猜，
     * 而删掉用户真正在看的书是不可接受的。与进度写在同一次 edit 里，不额外落盘。
     */
    fun saveProgress(bookId: Int, cid: String, at: Long = System.currentTimeMillis()) {
        reading.edit()
            .putString("progress_$bookId", cid)
            .putLong("${KEY_PROGRESS_AT}$bookId", at)
            .apply()
    }

    /** 某书最后阅读时间（毫秒）；从未记录（旧版本写入的进度）返回 null。 */
    fun lastReadAt(bookId: Int): Long? =
        if (reading.contains("${KEY_PROGRESS_AT}$bookId")) {
            reading.getLong("${KEY_PROGRESS_AT}$bookId", 0L)
        } else {
            null
        }

    /**
     * 清理「过期阅读数据」：删除最后阅读时间早于 [keepDays] 天的书的
     * 进度、总章节数、已读标记与时间戳。
     *
     * 两条保守规则：
     * 1. **没有时间戳的记录不删**——那些是本功能上线前写入的进度，无法判断新旧，
     *    删掉等于凭猜测销毁用户数据；
     * 2. 只有时间戳**确实早于**截止时间才删（`<` 而非 `<=`）。
     *
     * @return 被清理的书本数量。
     */
    fun cleanupStaleReadingData(keepDays: Int = DEFAULT_KEEP_DAYS): Int {
        if (keepDays <= 0) return 0
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60 * 60 * 1000
        val staleIds = staleReadingBookIds(reading.all, cutoff)
        if (staleIds.isEmpty()) return 0

        reading.edit().apply {
            staleIds.forEach { id ->
                remove("progress_$id")
                remove("progress_total_$id")
                remove("finished_$id")
                remove("${KEY_PROGRESS_AT}$id")
            }
        }.apply()
        return staleIds.size
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

    companion object {
        /** 「清理过期阅读数据」默认保留天数。 */
        const val DEFAULT_KEEP_DAYS = 30
    }
}
