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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem

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
                    HeatmapGrid(ui, onSelect = vm::selectDay)
                    Spacer(Modifier.height(8.dp))

                    // 累计时长 + 选中日期详情
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.stats_total, ui.totalMinutes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ui.selectedDay?.let { day ->
                            Text(
                                stringResource(R.string.stats_day_detail, formatDate(day.date), day.minutes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

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

/** 热力图矩阵：顶部为每列（周）的月份标签，下方为 7 行（周一..周日）× N 列的方块。 */
@Composable
private fun HeatmapGrid(
    ui: ReadingStatsUiState,
    onSelect: (HeatmapDay?) -> Unit,
) {
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
                            .clickable(enabled = day != null) { onSelect(day) },
                    )
                }
            }
        }
    }
}

/**
 * 颜色分级（参考 GitHub 贡献图绿色系，浅/深色主题均清晰）：
 * 0 分钟 → 中性灰；1~10 分钟 → 浅绿；11~30 分钟 → 中绿；>30 分钟 → 深绿。
 */
@Composable
private fun cellColor(day: HeatmapDay?): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        day == null || !day.hasData || day.minutes <= 0 -> scheme.surfaceContainerHighest
        day.minutes <= 10 -> Color(0xFF9BE9A8)
        day.minutes <= 30 -> Color(0xFF30A14E)
        else -> Color(0xFF216E39)
    }
}

private fun formatDate(date: java.time.LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
