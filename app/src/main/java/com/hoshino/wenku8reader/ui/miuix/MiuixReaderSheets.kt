package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.ui.reader.ReaderUiState
import com.hoshino.wenku8reader.ui.reader.ReaderViewModel
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 阅读器里的两个底部面板（目录 / 阅读设置）的 **MIUIX 版**。
 *
 * 与 Material 版（`ui/reader/ReaderScreen.kt` 里的 `ChapterSelectionSheet` / `SettingsSheet`）
 * 完全独立：面板用 miuix `WindowBottomSheet`，内部一律 miuix 组件
 * （`TabRow` 分段控件 / `MiuixSliderRow` / `MiuixSwitchRow` / `TextField`），
 * 字号与配色取自 `MiuixTheme`。逻辑仍由同一个 [ReaderViewModel] 驱动。
 */

/** 目录面板：按分卷折叠，当前章高亮（HyperOS 列表形态）。 */
@Composable
fun MiuixChapterSheet(
    ui: ReaderUiState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    var expandedVolumes by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(ui.currentCid, ui.volumes) {
        ui.volumes.firstOrNull { v -> v.chapters.any { it.cid == ui.currentCid } }?.name?.let {
            if (it !in expandedVolumes) expandedVolumes = expandedVolumes + it
        }
    }
    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.reader_toc),
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            ui.volumes.forEach { volume ->
                val expanded = volume.name in expandedVolumes
                item(key = "vol_${volume.name}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedVolumes = if (expanded) expandedVolumes - volume.name
                                else expandedVolumes + volume.name
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = volume.name,
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
                if (expanded) {
                    items(volume.chapters, key = { it.cid }) { chapter ->
                        val current = chapter.cid == ui.currentCid
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.loadChapter(chapter.cid)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = chapter.name,
                                style = MiuixTheme.textStyles.body1,
                                color = if (current) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (current) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.reader_current),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 阅读设置面板：外观 / 操作 / 边距 三个分组（miuix 分段控件切换）。 */
@Composable
fun MiuixReaderSettingsSheet(
    rs: ReaderSettingsState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.reader_settings),
        onDismissRequest = onDismiss,
    ) {
        Column {
            TabRow(
                tabs = listOf(
                    stringResource(R.string.reader_settings_appearance),
                    stringResource(R.string.reader_settings_action),
                    stringResource(R.string.reader_settings_margin),
                ),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (tab) {
                    0 -> ReaderAppearanceTab(rs, vm)
                    1 -> ReaderActionTab(rs, vm)
                    else -> ReaderMarginTab(rs, vm)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReaderAppearanceTab(rs: ReaderSettingsState, vm: ReaderViewModel) {
    MiuixSliderRow(
        title = stringResource(R.string.reader_font_size),
        valueText = stringResource(R.string.settings_font_size_value, rs.fontSize),
        value = rs.fontSize.toFloat(),
        onValueChange = { vm.setFontSize(it.roundToInt()) },
        valueRange = 14f..28f,
        steps = 6,
    )
    MiuixSliderRow(
        title = stringResource(R.string.settings_font_weight),
        valueText = stringResource(R.string.settings_font_weight_value, rs.fontWeight),
        value = rs.fontWeight.toFloat(),
        onValueChange = { vm.setFontWeight(it.roundToInt()) },
        valueRange = 300f..700f,
        steps = 3,
    )
    MiuixSliderRow(
        title = stringResource(R.string.settings_line_spacing),
        valueText = stringResource(R.string.settings_line_spacing_value, rs.lineSpacing),
        value = rs.lineSpacing,
        onValueChange = { vm.setLineSpacing((it * 10f).roundToInt() / 10f) },
        valueRange = 1.2f..2.5f,
    )
    MiuixSegmentedSetting(
        label = stringResource(R.string.settings_font),
        options = listOf(
            "default" to R.string.settings_font_default,
            "sans" to R.string.settings_font_sans,
            "serif" to R.string.settings_font_serif,
            "mono" to R.string.settings_font_mono,
        ),
        selected = rs.fontFamily,
        onSelect = vm::setFontFamily,
    )
    MiuixSegmentedSetting(
        label = stringResource(R.string.reader_turn_mode),
        options = listOf(
            "page" to R.string.reader_mode_page,
            "scroll" to R.string.reader_mode_scroll,
        ),
        selected = if (rs.scrollMode) "scroll" else "page",
        onSelect = { vm.setScrollMode(it == "scroll") },
    )
}

@Composable
private fun ReaderActionTab(rs: ReaderSettingsState, vm: ReaderViewModel) {
    MiuixSegmentedSetting(
        label = stringResource(R.string.reader_turn_direction),
        options = listOf(
            "left" to R.string.reader_turn_left,
            "right" to R.string.reader_turn_right,
        ),
        selected = if (rs.pageTurnDirection) "left" else "right",
        onSelect = { vm.setPageTurnDirection(it == "left") },
    )
    MiuixSwitchRow(
        title = stringResource(R.string.reader_click_turn),
        checked = rs.clickTurnPage,
        onCheckedChange = { vm.setClickTurnPage(it) },
    )
    MiuixSwitchRow(
        title = stringResource(R.string.reader_volume_turn),
        checked = rs.volumeKeyTurnPage,
        onCheckedChange = { vm.setVolumeKeyTurnPage(it) },
    )
    MiuixSwitchRow(
        title = stringResource(R.string.reader_auto_next),
        checked = rs.autoNextChapter,
        onCheckedChange = { vm.setAutoNextChapter(it) },
    )
    // 自动翻页间隔：原本是 M3 的 OutlinedTextField，这里换成 miuix TextField
    var intervalText by remember { mutableStateOf(rs.autoTurnInterval.toString()) }
    LaunchedEffect(rs.autoTurnInterval) { intervalText = rs.autoTurnInterval.toString() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reader_auto_interval),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextField(
            value = intervalText,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(3)
                intervalText = digits
                digits.toIntOrNull()?.takeIf { it in 1..999 }?.let { vm.setAutoTurnInterval(it) }
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun ReaderMarginTab(rs: ReaderSettingsState, vm: ReaderViewModel) {
    MiuixSwitchRow(
        title = stringResource(R.string.reader_auto_margin),
        checked = rs.autoPadding,
        onCheckedChange = { vm.setAutoPadding(it) },
    )
    if (!rs.autoPadding) {
        MiuixSliderRow(
            title = stringResource(R.string.reader_margin_top),
            valueText = "${rs.topPadding}dp",
            value = rs.topPadding.toFloat(),
            onValueChange = { vm.setTopPadding(it.roundToInt()) },
            valueRange = 0f..128f,
        )
        MiuixSliderRow(
            title = stringResource(R.string.reader_margin_bottom),
            valueText = "${rs.bottomPadding}dp",
            value = rs.bottomPadding.toFloat(),
            onValueChange = { vm.setBottomPadding(it.roundToInt()) },
            valueRange = 0f..128f,
        )
        MiuixSliderRow(
            title = stringResource(R.string.reader_margin_left),
            valueText = "${rs.leftPadding}dp",
            value = rs.leftPadding.toFloat(),
            onValueChange = { vm.setLeftPadding(it.roundToInt()) },
            valueRange = 0f..128f,
        )
        MiuixSliderRow(
            title = stringResource(R.string.reader_margin_right),
            valueText = "${rs.rightPadding}dp",
            value = rs.rightPadding.toFloat(),
            onValueChange = { vm.setRightPadding(it.roundToInt()) },
            valueRange = 0f..128f,
        )
    }
}

/** 带标题的分段控件（miuix `TabRow`），用于字体、翻页模式、翻页方向这类互斥选项。 */
@Composable
private fun MiuixSegmentedSetting(
    label: String,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        TabRow(
            tabs = options.map { stringResource(it.second) },
            selectedTabIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
            onTabSelected = { index -> onSelect(options[index].first) },
        )
    }
}
