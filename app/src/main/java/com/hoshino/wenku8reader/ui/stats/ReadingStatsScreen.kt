package com.hoshino.wenku8reader.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import java.time.DayOfWeek

private val CellSize = 14.dp
private val CellGap = 3.dp

/**
 * 阅读热力图：GitHub 贡献图风格的每日阅读时长矩阵 + 书籍累计时长列表。
 * 时间尺度（本周/本月/本年/全部）切换时，热力图与列表联动刷新（见 ReadingStatsViewModel）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    vm: ReadingStatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // 时间尺度切换（默认：本月）
            val scales = ReadingScale.entries
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                scales.forEachIndexed { index, scale ->
                    SegmentedButton(
                        selected = ui.scale == scale,
                        onClick = { vm.setScale(scale) },
                        shape = SegmentedButtonDefaults.itemShape(index, scales.size),
                    ) {
                        Text(stringResource(scale.labelRes))
                    }
                }
            }

            if (!ui.hasAnyData) {
                // 空状态：尚无阅读记录
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.stats_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(4.dp))

                    // 汇总卡（LNR StatsCards 风格）：累计 / 本周 / 连续 / 日均
                    SummaryRow(ui)
                    Spacer(Modifier.height(8.dp))

                    HeatmapGrid(ui, onSelect = vm::selectDay)
                    Spacer(Modifier.height(6.dp))

                    // 图例（少 → 多）：工作日绿 / 周末蓝
                    HeatmapLegend()
                    Spacer(Modifier.height(12.dp))

                    // 选中日期 → 当日详情卡（LNR DailyStatsBlock 风格）
                    ui.selectedDay?.let { day ->
                        DailyDetailCard(day, ui)
                        Spacer(Modifier.height(12.dp))
                    }

                    // 书籍累计时长列表（降序）
                    SegmentedColumn(
                        title = stringResource(R.string.stats_books_title),
                        items = ui.bookList.map { book ->
                            {
                                SegmentedListItem(
                                    headlineContent = {
                                        Text(book.bookName, maxLines = 1)
                                    },
                                    trailingContent = {
                                        Text(
                                            stringResource(R.string.stats_minutes, book.minutes),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = { onOpenBook(book.bookId) },
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 汇总卡行：累计分钟 / 本周分钟 / 连续天数 / 日均分钟。 */
@Composable
private fun SummaryRow(ui: ReadingStatsUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(stringResource(R.string.stats_summary_total), ui.totalMinutes)
        SummaryCard(stringResource(R.string.stats_summary_week), ui.weekMinutes)
        SummaryCard(stringResource(R.string.stats_summary_streak), ui.streakDays)
        SummaryCard(stringResource(R.string.stats_summary_avg), ui.avgDailyMinutes)
    }
}

@Composable
private fun RowScope.SummaryCard(label: String, value: Int) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 图例：少 → 多，工作日（绿）/ 周末（蓝）两行。 */
@Composable
private fun HeatmapLegend() {
    Column {
        LegendRow(colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            WeekdayColor(10), WeekdayColor(30), WeekdayColor(Int.MAX_VALUE),
        ))
        LegendRow(colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            WeekendColor(10), WeekendColor(30), WeekendColor(Int.MAX_VALUE),
        ), labels = true)
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
                stringResource(R.string.stats_legend_less),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Text(
                stringResource(R.string.stats_legend_weekday),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                stringResource(R.string.stats_legend_more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 当日详情卡（LNR DailyStatsBlock）：日期 + 当日总分钟 + 当日每本书明细。 */
@Composable
private fun DailyDetailCard(day: HeatmapDay, ui: ReadingStatsUiState) {
    val total = ui.dayTotalMinutes[day.epochDay] ?: 0
    val books = ui.dailyBookMinutes[day.epochDay].orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDate(day.date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W600,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.stats_day_total, total),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (books.isEmpty()) {
            Text(
                stringResource(R.string.stats_no_records),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            books.forEach { book ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        book.bookName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.stats_minutes, book.minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/** 热力图矩阵：顶部为每列（周）的月份标签，下方为 7 行（周一..周日）× N 列的方块。 */
@Composable
private fun HeatmapGrid(
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
                // 列标签（"1月"），与方块同宽
                Box(Modifier.size(CellSize), contentAlignment = Alignment.Center) {
                    Text(
                        ui.weekLabels.getOrNull(col) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                } else {
                                    Modifier
                                }
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
 * 色阶（参考 LNR Levels）：0 分钟 → 中性灰；1~10 → 浅绿/蓝；11~30 → 中；>30 → 深。
 * 工作日绿色系 #329c32、周末蓝色系 #29538f，按 alpha 递增区分强度（深浅主题均清晰）。
 */
@Composable
private fun cellColor(day: HeatmapDay?): Color {
    val scheme = MaterialTheme.colorScheme
    if (day == null || !day.hasData || day.minutes <= 0) return scheme.surfaceContainerHighest
    val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
    return when {
        day.minutes <= 10 -> if (weekend) WeekendColor(10) else WeekdayColor(10)
        day.minutes <= 30 -> if (weekend) WeekendColor(30) else WeekdayColor(30)
        else -> if (weekend) WeekendColor(Int.MAX_VALUE) else WeekdayColor(Int.MAX_VALUE)
    }
}

/** 工作日（绿）分级色：alpha 随分钟递增（参考 LNR #329c32 系）。 */
private fun WeekdayColor(minutes: Int): Color = when {
    minutes <= 10 -> Color(0x44329c32)
    minutes <= 30 -> Color(0x8C329c32)
    else -> Color(0xFF329c32)
}

/** 周末（蓝）分级色（参考 LNR #29538f 系）。 */
private fun WeekendColor(minutes: Int): Color = when {
    minutes <= 10 -> Color(0x4429538f)
    minutes <= 30 -> Color(0x8C29538f)
    else -> Color(0xFF29538f)
}

private fun formatDate(date: java.time.LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
