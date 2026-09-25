package com.hoshino.wenku8reader.ui.common

import androidx.compose.ui.graphics.Color

/**
 * 阅读热力图的色阶（工作日绿 / 周末蓝）。
 *
 * Material 版（`ui/stats/ReadingStatsScreen.kt`）与 MIUIX 版（`ui/miuix/MiuixStatsPage.kt`）
 * 此前各写了一份同样的色值（6 个 `Color(0x…)` 常量 ×2），已经出现命名漂移
 *（`WeekdayColor` / `weekdayColor`）。色阶是"数据可视化口径"，与 UI 风格无关，
 * 因此收敛到这里一份：改配色只改一个地方，两套界面自动一致。
 *
 * 分级依据（参考 LNR Levels）：0 分钟 → 主题的中性区块色（由调用方传入）；
 * 1~10 分钟浅色；11~30 分钟中色；>30 分钟实色。
 */
internal fun weekdayColor(minutes: Int): Color = when {
    minutes <= 10 -> Color(0x44329c32)
    minutes <= 30 -> Color(0x8C329c32)
    else -> Color(0xFF329c32)
}

internal fun weekendColor(minutes: Int): Color = when {
    minutes <= 10 -> Color(0x4429538f)
    minutes <= 30 -> Color(0x8C29538f)
    else -> Color(0xFF29538f)
}

/**
 * 单个格子的颜色。
 *
 * @param minutes 当天阅读分钟数
 * @param hasData 当天是否有记录（无记录一律用 [emptyColor]，不参与色阶）
 * @param weekend 是否周末（决定用蓝色系还是绿色系）
 * @param emptyColor 空数据格的颜色，取自当前主题（`surfaceContainerHighest` 等）
 */
internal fun heatmapCellColor(
    minutes: Int,
    hasData: Boolean,
    weekend: Boolean,
    emptyColor: Color,
): Color = when {
    !hasData || minutes <= 0 -> emptyColor
    minutes <= 10 -> if (weekend) weekendColor(10) else weekdayColor(10)
    minutes <= 30 -> if (weekend) weekendColor(30) else weekdayColor(30)
    else -> if (weekend) weekendColor(Int.MAX_VALUE) else weekdayColor(Int.MAX_VALUE)
}
