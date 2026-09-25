package com.hoshino.wenku8reader.data.local

import android.content.Context

/**
 * 轻量界面偏好（`ui` 偏好文件）：书架排序、更新检查节流等。
 *
 * 这里**只剩不构成用户数据的东西**：
 * - 书架与阅读进度已迁到 Room（见 [LibraryStore] / [ReadingProgressStore]）；
 * - 阅读外观与主题设置由 [ReaderSettings] 承担。
 *
 * 安全说明：此处**不再持久化任何账号密码**。
 * 旧版本曾提供 `saveCredentials/username/password`（明文写入未加密的 `account` 偏好），
 * 经全仓检索确认从未被调用，属于纯粹的安全暴露面，故整体移除——登录态由
 * [com.hoshino.wenku8reader.data.CookieStore] 持久化的会话 Cookie 承担，
 * 无需保存密码即可免登录。
 * 实验性「账户登录」同样只保存**用户名**（见 [AccountStore]），密码仅在登录请求期间
 * 存在于内存中。
 */
class AppPreferences(context: Context) {

    private val ui = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

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
