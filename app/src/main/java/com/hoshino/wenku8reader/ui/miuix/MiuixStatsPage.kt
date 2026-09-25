package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.stats.HeatmapDay
import com.hoshino.wenku8reader.ui.stats.ReadingScale
import com.hoshino.wenku8reader.ui.stats.ReadingStatsUiState
import com.hoshino.wenku8reader.ui.stats.ReadingStatsViewModel
import java.time.DayOfWeek
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CellSize = 14.dp
private val CellGap = 3.dp

/**
 * MIUIX 阅读统计页——与 Material 版（`ui/stats/ReadingStatsScreen.kt`）**完全独立**。
 *
 * 形态照 HyperOS：小标题顶栏（返回 + 刷新）+ 分段控件切换时间尺度 + 汇总卡 + 阅读热力图
 * + 当日明细 + 书籍累计时长列表。热力图的色阶沿用 GitHub 贡献图惯例（工作日绿 / 周末蓝），
 * 空态区块色取自 `MiuixTheme`，逻辑全部来自同一个 [ReadingStatsViewModel]。
 */
@Composable
fun MiuixStatsPage(
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    vm: ReadingStatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scales = ReadingScale.entries

    MiuixSubPage(
        title = stringResource(R.string.stats_title),
        onBack = onBack,
        actions = {
            MiuixIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.action_refresh),
                onClick = vm::refresh,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            TabRow(
                tabs = scales.map { stringResource(it.labelRes) },
                selectedTabIndex = scales.indexOf(ui.scale).coerceAtLeast(0),
                onTabSelected = { index -> vm.setScale(scales[index]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (!ui.hasAnyData) {
                MiuixEmptyState(
                    title = stringResource(R.string.stats_empty),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    MiuixSummaryRow(ui)
                    Spacer(Modifier.height(10.dp))
                    MiuixHeatmapGrid(ui, onSelect = vm::selectDay)
                    Spacer(Modifier.height(8.dp))
                    MiuixHeatmapLegend()
                    Spacer(Modifier.height(14.dp))

                    ui.selectedDay?.let { day ->
                        MiuixDailyDetailCard(day, ui)
                        Spacer(Modifier.height(14.dp))
                    }

                    MiuixSection(title = stringResource(R.string.stats_books_title)) {
                        ui.bookList.forEachIndexed { index, book ->
                            if (index > 0) MiuixRowDivider()
                            MiuixRow(
                                title = book.bookName,
                                onClick = { onOpenBook(book.bookId) },
                                trailing = {
                                    Text(
                                        text = stringResource(R.string.stats_minutes, book.minutes),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
                }
            }
        }
    }
}

/** 汇总卡行：累计 / 本周 / 连续 / 日均（miuix 卡片，一行四张）。 */
@Composable
private fun MiuixSummaryRow(ui: ReadingStatsUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiuixSummaryCard(stringResource(R.string.stats_summary_total), ui.totalMinutes)
        MiuixSummaryCard(stringResource(R.string.stats_summary_week), ui.weekMinutes)
        MiuixSummaryCard(stringResource(R.string.stats_summary_streak), ui.streakDays)
        MiuixSummaryCard(stringResource(R.string.stats_summary_avg), ui.avgDailyMinutes)
    }
}

@Composable
private fun RowScope.MiuixSummaryCard(label: String, value: Int) {
    Card(
        modifier = Modifier.weight(1f),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
    }
}

/** 图例：少 → 多，工作日（绿）/ 周末（蓝）两行。 */
@Composable
private fun MiuixHeatmapLegend() {
    Column {
        LegendRow(
            colors = listOf(
                MiuixTheme.colorScheme.surfaceContainerHighest,
                weekdayColor(10), weekdayColor(30), weekdayColor(Int.MAX_VALUE),
            ),
        )
        LegendRow(
            colors = listOf(
                MiuixTheme.colorScheme.surfaceContainerHighest,
                weekendColor(10), weekendColor(30), weekendColor(Int.MAX_VALUE),
            ),
            labels = true,
        )
    }
}

@Composable
private fun LegendRow(colors: List<Color>, labels: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (labels) {
            Text(
                text = stringResource(R.string.stats_legend_less),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Text(
                text = stringResource(R.string.stats_legend_weekday),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.weight(1f))
        }
        colors.forEach { color ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        if (labels) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.stats_legend_more),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

/** 当日详情卡：日期 + 当日总时长 + 每本书明细。 */
@Composable
private fun MiuixDailyDetailCard(day: HeatmapDay, ui: ReadingStatsUiState) {
    val total = ui.dayTotalMinutes[day.epochDay] ?: 0
    val books = ui.dailyBookMinutes[day.epochDay].orEmpty()
    MiuixSection(title = formatDate(day.date)) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_day_total, total),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            if (books.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_no_records),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                books.forEach { book ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = book.bookName,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.stats_minutes, book.minutes),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** 热力图矩阵：顶部每列（周）的月份标签，下方 7 行（周一..周日）× N 列方块。 */
@Composable
private fun MiuixHeatmapGrid(
    ui: ReadingStatsUiState,
    onSelect: (HeatmapDay?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        ui.weeks.forEachIndexed { col, weekDays ->
            Column(
                Modifier.padding(end = CellGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(CellSize), contentAlignment = Alignment.Center) {
                    Text(
                        text = ui.weekLabels.getOrNull(col) ?: "",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.height(CellGap))
                weekDays.forEach { day ->
                    Box(
                        Modifier
                            .padding(bottom = CellGap)
                            .size(CellSize)
                            .background(
                                color = cellColor(day),
                                shape = RoundedCornerShape(3.dp),
                            )
                            .then(
                                if (day != null && ui.selectedDay?.epochDay == day.epochDay) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = MiuixTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(3.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = day != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(day)
                            },
                    )
                }
            }
        }
    }
}

/**
 * 色阶：0 分钟 → 主题的中性区块色；1~10 浅、11~30 中、>30 深。
 * 工作日绿 #329c32、周末蓝 #29538f（与 Material 版同一套色值，深浅主题下都清晰）。
 */
@Composable
private fun cellColor(day: HeatmapDay?): Color {
    if (day == null || !day.hasData || day.minutes <= 0) {
        return MiuixTheme.colorScheme.surfaceContainerHighest
    }
    val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY ||
        day.date.dayOfWeek == DayOfWeek.SUNDAY
    return when {
        day.minutes <= 10 -> if (weekend) weekendColor(10) else weekdayColor(10)
        day.minutes <= 30 -> if (weekend) weekendColor(30) else weekdayColor(30)
        else -> if (weekend) weekendColor(Int.MAX_VALUE) else weekdayColor(Int.MAX_VALUE)
    }
}

private fun weekdayColor(minutes: Int): Color = when {
    minutes <= 10 -> Color(0x44329c32)
    minutes <= 30 -> Color(0x8C329c32)
    else -> Color(0xFF329c32)
}

private fun weekendColor(minutes: Int): Color = when {
    minutes <= 10 -> Color(0x4429538f)
    minutes <= 30 -> Color(0x8C29538f)
    else -> Color(0xFF29538f)
}

private fun formatDate(date: java.time.LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
