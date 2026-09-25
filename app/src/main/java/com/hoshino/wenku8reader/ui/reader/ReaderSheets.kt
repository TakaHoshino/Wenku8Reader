package com.hoshino.wenku8reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.ui.components.ExpressiveSlider
import com.hoshino.wenku8reader.ui.components.ExpressiveSwitch
import kotlin.math.roundToInt

/**
 * 阅读器的两个 Material 面板：章节目录与阅读设置。
 *
 * 从 `ReaderScreen.kt` 拆出。MIUIX 风格下的对应实现是 `ui/miuix/MiuixReaderSheets.kt`，
 * 页面按 `isMiuixStyle()` 二选一；这里保留 Material 版（与阅读器其余部分一致）。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterSelectionSheet(
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.reader_toc),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            volume.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (expanded) {
                    items(volume.chapters, key = { it.cid }) { ch ->
                        val current = ch.cid == ui.currentCid
                        ListItem(
                            headlineContent = {
                                Text(
                                    ch.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                    color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                )
                            },
                            supportingContent = { if (current) Text(stringResource(R.string.reader_current)) },
                            modifier = Modifier.clickable {
                                vm.loadChapter(ch.cid)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    rs: ReaderSettingsState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.reader_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SecondaryTabRow(selectedTabIndex = tab) {
                listOf(
                    R.string.reader_settings_appearance,
                    R.string.reader_settings_action,
                    R.string.reader_settings_margin,
                ).forEachIndexed { index, res ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(stringResource(res)) },
                    )
                }
            }
            when (tab) {
                0 -> AppearanceSettings(rs, vm)
                1 -> ActionSettings(rs, vm)
                else -> MarginSettings(rs, vm)
            }
        }
    }
}

@Composable
private fun AppearanceSettings(rs: ReaderSettingsState, vm: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            SettingSliderRow(
                stringResource(R.string.reader_font_size),
                rs.fontSize.toFloat(),
                14f..28f,
            ) { vm.setFontSize(it.roundToInt()) }
        }
        item {
            SettingSliderRow(
                stringResource(R.string.settings_font_weight),
                rs.fontWeight.toFloat(),
                300f..700f,
            ) { vm.setFontWeight(it.roundToInt()) }
        }
        item {
            SettingSliderRow(
                stringResource(R.string.settings_line_spacing),
                rs.lineSpacing,
                1.2f..2.5f,
            ) { vm.setLineSpacing((it * 10f).roundToInt() / 10f) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "default" to R.string.settings_font_default,
                    "sans" to R.string.settings_font_sans,
                    "serif" to R.string.settings_font_serif,
                    "mono" to R.string.settings_font_mono,
                ).forEach { (key, res) ->
                    FilterChip(
                        selected = rs.fontFamily == key,
                        onClick = { vm.setFontFamily(key) },
                        label = { Text(stringResource(res)) },
                    )
                }
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_turn_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !rs.scrollMode,
                        onClick = { vm.setScrollMode(false) },
                        label = { Text(stringResource(R.string.reader_mode_page)) },
                    )
                    FilterChip(
                        selected = rs.scrollMode,
                        onClick = { vm.setScrollMode(true) },
                        label = { Text(stringResource(R.string.reader_mode_scroll)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSettings(rs: ReaderSettingsState, vm: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            SettingRow(stringResource(R.string.reader_turn_direction)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rs.pageTurnDirection,
                        onClick = { vm.setPageTurnDirection(true) },
                        label = { Text(stringResource(R.string.reader_turn_left)) },
                    )
                    FilterChip(
                        selected = !rs.pageTurnDirection,
                        onClick = { vm.setPageTurnDirection(false) },
                        label = { Text(stringResource(R.string.reader_turn_right)) },
                    )
                }
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_click_turn)) {
                ExpressiveSwitch(checked = rs.clickTurnPage, onCheckedChange = { vm.setClickTurnPage(it) })
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_volume_turn)) {
                ExpressiveSwitch(checked = rs.volumeKeyTurnPage, onCheckedChange = { vm.setVolumeKeyTurnPage(it) })
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_auto_next)) {
                ExpressiveSwitch(checked = rs.autoNextChapter, onCheckedChange = { vm.setAutoNextChapter(it) })
            }
        }
        item {
            var intervalText by remember { mutableStateOf(rs.autoTurnInterval.toString()) }
            LaunchedEffect(rs.autoTurnInterval) { intervalText = rs.autoTurnInterval.toString() }
            SettingRow(stringResource(R.string.reader_auto_interval)) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        intervalText = digits
                        digits.toIntOrNull()?.takeIf { it in 1..999 }
                            ?.let { vm.setAutoTurnInterval(it) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

@Composable
private fun MarginSettings(rs: ReaderSettingsState, vm: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            SettingRow(stringResource(R.string.reader_auto_margin)) {
                ExpressiveSwitch(checked = rs.autoPadding, onCheckedChange = { vm.setAutoPadding(it) })
            }
        }
        if (!rs.autoPadding) {
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_top), rs.topPadding.toFloat(), 0f..128f) { vm.setTopPadding(it.roundToInt()) }
            }
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_bottom), rs.bottomPadding.toFloat(), 0f..128f) { vm.setBottomPadding(it.roundToInt()) }
            }
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_left), rs.leftPadding.toFloat(), 0f..128f) { vm.setLeftPadding(it.roundToInt()) }
            }
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_right), rs.rightPadding.toFloat(), 0f..128f) { vm.setRightPadding(it.roundToInt()) }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        ExpressiveSlider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}
