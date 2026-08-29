package com.hoshino.wenku8reader.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 某书在某自然日的累计阅读秒数（按「书 + 日期」聚合后的最小存储单元）。 */
data class ReadingDayStat(
    val bookId: Int,
    val bookName: String,
    val epochDay: Long,
    val seconds: Long,
)

/**
 * 阅读时长存储：按「书 + 日期」聚合秒数（一书一天一条），
 * 内存态 + SharedPreferences JSON 持久化（阅读器埋点周期性落盘）。
 *
 * [version] 在每次 [persist] 后 +1，UI（热力图/书籍列表）据此重新聚合刷新。
 * 聚合算法：每日/每书分钟数 = ceil(秒数 / 60)，不足 1 分钟按 1 分钟计。
 */
class ReadingStatsStore(context: Context) {

    private val prefs = context.getSharedPreferences("reading_stats", Context.MODE_PRIVATE)

    /** epochDay -> bookId -> 当日累计 */
    private val map = HashMap<Long, HashMap<Int, ReadingDayStat>>()

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    init {
        load()
    }

    /** 给某书累加阅读秒数（记入今天）。 */
    @Synchronized
    fun addSeconds(bookId: Int, bookName: String, seconds: Long) {
        if (bookId <= 0 || seconds <= 0) return
        val day = LocalDate.now().toEpochDay()
        val byBook = map.getOrPut(day) { HashMap() }
        val cur = byBook[bookId]
        byBook[bookId] = if (cur == null) {
            ReadingDayStat(bookId, bookName, day, seconds)
        } else {
            cur.copy(
                seconds = cur.seconds + seconds,
                bookName = bookName.takeIf { it.isNotBlank() } ?: cur.bookName,
            )
        }
    }

    /** 持久化全部数据并递增版本号（通知 UI 重新聚合）。 */
    @Synchronized
    fun persist() {
        val arr = JSONArray()
        map.values.forEach { byBook ->
            byBook.values.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("bookId", s.bookId)
                        .put("bookName", s.bookName)
                        .put("day", s.epochDay)
                        .put("seconds", s.seconds)
                )
            }
        }
        prefs.edit().putString("data", arr.toString()).apply()
        _version.value = _version.value + 1
    }

    /** 当前全部聚合记录快照（供 UI 聚合计算）。 */
    @Synchronized
    fun snapshot(): List<ReadingDayStat> = map.values.flatMap { it.values }

    private fun load() {
        val raw = prefs.getString("data", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val day = o.getLong("day")
                val bookId = o.getInt("bookId")
                map.getOrPut(day) { HashMap() }[bookId] = ReadingDayStat(
                    bookId = bookId,
                    bookName = o.optString("bookName", ""),
                    epochDay = day,
                    seconds = o.getLong("seconds"),
                )
            }
        }
    }
}
