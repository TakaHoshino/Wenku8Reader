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
}
