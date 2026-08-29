package com.hoshino.wenku8reader.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.ReadingDayStat
import com.hoshino.wenku8reader.data.local.ReadingStatsStore
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 热力图时间尺度。 */
enum class ReadingScale(val labelRes: Int) {
    WEEK(R.string.stats_week),
    MONTH(R.string.stats_month),
    YEAR(R.string.stats_year),
    ALL(R.string.stats_all),
}

/** 热力图单个方块：某天（null 表示不在范围内/未来）。 */
@Immutable
data class HeatmapDay(
    val epochDay: Long,
    val date: LocalDate,
    val minutes: Int,
    val hasData: Boolean,
)

/** 书籍在所选范围内的累计阅读分钟数。 */
@Immutable
data class BookMinutes(val bookId: Int, val bookName: String, val minutes: Int)

@Immutable
data class ReadingStatsUiState(
    val scale: ReadingScale = ReadingScale.MONTH,
    /** GitHub 风格：列 = 周（周一到周日），行 = 星期。 */
    val weeks: List<List<HeatmapDay?>> = emptyList(),
    /** 每列顶部标签（如 "1月"），无标签为 null。 */
    val weekLabels: List<String?> = emptyList(),
    val selectedDay: HeatmapDay? = null,
    /** 所选范围内的总分钟数。 */
    val totalMinutes: Int = 0,
    /** 本周（周一到今天）总分钟数。 */
    val weekMinutes: Int = 0,
    /** 连续阅读天数（从今天起向前连续有记录的自然日数）。 */
    val streakDays: Int = 0,
    /** 范围内有记录的天数（用于日均计算）。 */
    val activeDays: Int = 0,
    /** 每日分钟数 = 总分钟 / 活跃天数（无记录为 0）。 */
    val avgDailyMinutes: Int = 0,
    /** 书籍在所选范围内的累计分钟数（降序）。 */
    val bookList: List<BookMinutes> = emptyList(),
    /** 每个日期的当日书籍分钟明细（LNR 风格：点日期看当日详情）。 */
    val dailyBookMinutes: Map<Long, List<BookMinutes>> = emptyMap(),
    /** 每个日期的当日总分钟（先累计秒再 ceil 一次，精确）。 */
    val dayTotalMinutes: Map<Long, Int> = emptyMap(),
    val hasAnyData: Boolean = false,
)

class ReadingStatsViewModel(private val store: ReadingStatsStore) : ViewModel() {

    private val _ui = MutableStateFlow(ReadingStatsUiState())
    val ui: StateFlow<ReadingStatsUiState> = _ui.asStateFlow()

    private val scale = MutableStateFlow(ReadingScale.MONTH)

    init {
        viewModelScope.launch {
            combine(scale, store.version) { s, _ -> s }
                .collect { s -> refresh(s) }
        }
    }

    fun setScale(s: ReadingScale) {
        scale.value = s
    }

    /** 手动刷新（顶栏刷新按钮）。 */
    fun refresh() {
        viewModelScope.launch { refresh(scale.value) }
    }

    fun selectDay(day: HeatmapDay?) {
        _ui.value = _ui.value.copy(selectedDay = day)
    }

    private suspend fun refresh(s: ReadingScale) {
        val state = withContext(Dispatchers.Default) { compute(s) }
        _ui.value = state
    }

    // ------------------------------------------------------------------ //
    // 聚合核心算法
    // ------------------------------------------------------------------ //

    private fun compute(s: ReadingScale): ReadingStatsUiState {
        val records = store.snapshot()
        val today = LocalDate.now()
        val (start, end) = rangeOf(s, records, today)

        // 1) 先按「天」与「书」分别累计秒数，最后统一向上取整成分钟
        //    （逐条 ceil 再求和会产生累加误差，如两条 30s 同日应计 1 分钟而非 2 分钟）
        val daySeconds = HashMap<Long, Long>()
        val bookSeconds = HashMap<Int, Long>()
        val bookName = HashMap<Int, String>()
        // 当日每书明细：(epochDay, bookId) -> (书名, 秒数)
        val dayBookSeconds = HashMap<Long, HashMap<Int, Pair<String, Long>>>()
        var totalSeconds = 0L
        for (r in records) {
            val d = LocalDate.ofEpochDay(r.epochDay)
            if (d < start || d > end) continue
            totalSeconds += r.seconds
            daySeconds.merge(r.epochDay, r.seconds, Long::plus)
            bookSeconds.merge(r.bookId, r.seconds, Long::plus)
            if (r.bookName.isNotBlank()) bookName[r.bookId] = r.bookName
            val byBook = dayBookSeconds.getOrPut(r.epochDay) { HashMap() }
            val prev = byBook[r.bookId]
            byBook[r.bookId] = (r.bookName to (prev?.second ?: 0L) + r.seconds)
        }

        // 2) 分钟化（向上取整，不足 1 分钟按 1 分钟）
        val dailyMinutes = HashMap<Long, Int>(daySeconds.size)
        daySeconds.forEach { (day, sec) -> dailyMinutes[day] = ceilMinutes(sec) }

        // 3) 构建周列网格（GitHub 贡献图：列 = 周，行 = 周一..周日）
        val weeks = buildWeeks(dailyMinutes, start, end, today)
        val labels = buildLabels(weeks)

        // 4) 书籍时长列表（降序）
        val books = bookSeconds.map { (id, sec) ->
            BookMinutes(id, bookName[id] ?: "书 $id", ceilMinutes(sec))
        }.sortedByDescending { it.minutes }

        // 5) 当日每书明细（LNR 风格：点日期看当日详情）
        val dailyBookMinutes = dayBookSeconds.mapValues { (_, byBook) ->
            byBook.map { (id, pair) ->
                BookMinutes(id, pair.first.ifBlank { bookName[id] ?: "书 $id" }, ceilMinutes(pair.second))
            }.sortedByDescending { it.minutes }
        }

        // 6) 汇总：本周时长 / 连续阅读天数 / 活跃天数 / 日均
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekSeconds = records
            .filter { LocalDate.ofEpochDay(it.epochDay) in weekStart..today }
            .sumOf { it.seconds }
        var streak = 0
        var cursor = today
        while (dailyMinutes.containsKey(cursor.toEpochDay())) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val activeDays = daySeconds.size

        return ReadingStatsUiState(
            scale = s,
            weeks = weeks,
            weekLabels = labels,
            totalMinutes = ceilMinutes(totalSeconds),
            weekMinutes = ceilMinutes(weekSeconds),
            streakDays = streak,
            activeDays = activeDays,
            avgDailyMinutes = if (activeDays > 0) ceilMinutes(totalSeconds / activeDays) else 0,
            bookList = books,
            dailyBookMinutes = dailyBookMinutes,
            dayTotalMinutes = dailyMinutes,
            hasAnyData = records.isNotEmpty(),
        )
    }

    /** 所选尺度的时间范围：[start, end]（end 不超过今天）。 */
    private fun rangeOf(
        s: ReadingScale,
        records: List<ReadingDayStat>,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate> = when (s) {
        ReadingScale.WEEK -> today.with(DayOfWeek.MONDAY) to today
        ReadingScale.MONTH -> today.withDayOfMonth(1) to today
        ReadingScale.YEAR -> today.withDayOfYear(1) to today
        ReadingScale.ALL -> {
            val min = records.minOfOrNull { LocalDate.ofEpochDay(it.epochDay) }
            (min ?: today.minusDays(364)) to today
        }
    }

    /**
     * 构建周列网格：从覆盖 [start] 的周一开始，到覆盖 [end] 的周日为止。
     * 范围外 / 未来（>今天）的格子为 null。
     */
    private fun buildWeeks(
        dailyMinutes: Map<Long, Int>,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
    ): List<List<HeatmapDay?>> {
        val weeks = mutableListOf<List<HeatmapDay?>>()
        var cursor = start.with(DayOfWeek.MONDAY)
        val gridEnd = end.with(DayOfWeek.SUNDAY)
        while (!cursor.isAfter(gridEnd)) {
            weeks.add(
                (0..6).map { dow ->
                    val d = cursor.plusDays(dow.toLong())
                    if (d < start || d > end || d > today) {
                        null
                    } else {
                        val has = dailyMinutes.containsKey(d.toEpochDay())
                        HeatmapDay(d.toEpochDay(), d, dailyMinutes[d.toEpochDay()] ?: 0, has)
                    }
                }
            )
            cursor = cursor.plusWeeks(1)
        }
        return weeks
    }

    /** 每月首个周列处标注 "M月"。 */
    private fun buildLabels(weeks: List<List<HeatmapDay?>>): List<String?> {
        val labels = mutableListOf<String?>()
        var lastMonth = -1
        for (column in weeks) {
            val month = column.filterNotNull().firstOrNull()?.date?.monthValue
            if (month != null && month != lastMonth) {
                labels.add("${month}月")
                lastMonth = month
            } else {
                labels.add(null)
            }
        }
        return labels
    }

    /** 秒 → 分钟，向上取整（不足 1 分钟按 1 分钟）。 */
    private fun ceilMinutes(seconds: Long): Int =
        if (seconds <= 0) 0 else ceil(seconds / 60.0).toInt()
}
