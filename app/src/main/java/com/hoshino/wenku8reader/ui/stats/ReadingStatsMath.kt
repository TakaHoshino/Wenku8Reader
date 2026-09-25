package com.hoshino.wenku8reader.ui.stats

import java.time.LocalDate
import kotlin.math.ceil

/**
 * 阅读统计里的纯计算：秒→分钟取整、连续阅读天数、日均分钟。
 *
 * 从 `ReadingStatsViewModel` 提为顶层函数以便单测。这三处此前完全无测试，而它们错了
 * 都不会报错、只会"数字看着不对"：
 * - 向上取整写错 → 每天少算/多算一分钟，热力图与汇总长期偏移；
 * - 连续天数从"最近一天"而不是"今天"起算 → 隔一天没读也显示连续（最常见的那种错）；
 * - 日均按日历天而不是活跃天算 → 用户一停更就被稀释成很小的数。
 */

/** 秒 → 分钟，向上取整（不足 1 分钟按 1 分钟计；非正数为 0）。 */
internal fun ceilMinutes(seconds: Long): Int =
    if (seconds <= 0) 0 else ceil(seconds / 60.0).toInt()

/**
 * 连续阅读天数：**从今天起**向前逐日回溯，直到某天没有记录为止。
 *
 * @param daysWithData 有阅读记录的日期（通常来自聚合 map 的 key 集合）
 * @param today 今天（测试注入固定日期，避免依赖系统时钟）
 */
internal fun streakDays(daysWithData: Set<Long>, today: LocalDate): Int {
    var streak = 0
    var cursor = today
    while (daysWithData.contains(cursor.toEpochDay())) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** 日均分钟：按"有记录的天数"平均（而非日历天数），无记录返回 0。 */
internal fun avgDailyMinutes(totalSeconds: Long, activeDays: Int): Int =
    if (activeDays > 0) ceilMinutes(totalSeconds / activeDays) else 0
