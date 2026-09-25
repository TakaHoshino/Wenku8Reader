package com.hoshino.wenku8reader.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.data.Wenku8Hosts
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.ui.components.ExpressiveSlider
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedDropdownItem
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.SegmentedSwitchItem
import com.hoshino.wenku8reader.ui.theme.seedColorOptions
import com.hoshino.wenku8reader.ui.theme.UiStyle

// ------------------------------------------------------------------ //
// 分组
// ------------------------------------------------------------------ //

/**
 * 账号分组：**说明性**展示（无交互，符合产品设计）。
 *
 * 本应用全程使用内置共享账号取数，不提供登录、退出或切换账号的入口，
 * 因此这里不需要（也不应该）展示"未登录 / 某个用户名"这类用户可切换的状态——
 * 那会暗示存在用户可操作但实际不存在的登录流程。
 * 此前的实现只写死一句提示、与运行状态无关，容易被误读为"能操作却没做"，
 * 现改为明确说明"无需登录/切换"，语义与真实行为一致。
 */
@Composable
internal fun AccountSection() {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_section_account),
        items = listOf {
            SegmentedListItem(
                leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_builtin_account)) },
                supportingContent = { Text(stringResource(R.string.settings_account_builtin_desc)) },
            )
        },
    )
}

/** 外观分组：界面语言、深色模式、纯黑模式、动态取色。 */
@Composable
internal fun AppearanceSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
    context: Context,
) {
    val darkModeOptions = listOf(
        "system" to R.string.settings_dark_system,
        "light" to R.string.settings_dark_light,
        "dark" to R.string.settings_dark_dark,
    )
    val darkModeLabels = darkModeOptions.map { (_, res) -> stringResource(res) }
    // 界面语言（切换后重建 Activity 使 attachBaseContext 生效）
    val languageOptions = listOf(
        "system" to R.string.settings_language_system,
        "zh-CN" to R.string.settings_language_simplified,
        "zh-TW" to R.string.settings_language_traditional,
    )
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_section_appearance),
        items = listOf(
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(R.string.settings_language_summary),
                    items = languageOptions.map { (_, res) -> stringResource(res) },
                    selectedIndex = languageOptions.indexOfKey(rs.appLanguage),
                    onItemSelected = { index ->
                        val lang = languageOptions[index].first
                        if (lang != rs.appLanguage) {
                            vm.setAppLanguage(lang)
                            // LocalContext 在预览/测试或被包装时不是 Activity，直接强转会静默失败
                            // （语言不生效也没有提示），故递归解包 ContextWrapper 找宿主 Activity。
                            context.findActivity()?.recreate()
                        }
                    },
                )
            },
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_dark_mode),
                    summary = stringResource(R.string.settings_dark_mode_summary),
                    items = darkModeLabels,
                    selectedIndex = darkModeOptions.indexOfKey(rs.darkMode),
                    onItemSelected = { index ->
                        vm.setDarkMode(darkModeOptions[index].first)
                    },
                )
            },
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_amoled),
                    summary = stringResource(R.string.settings_amoled_summary),
                    checked = rs.amoled,
                    onCheckedChange = vm::setAmoled,
                )
            },
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = rs.dynamicColor,
                    onCheckedChange = vm::setDynamicColor,
                )
            },
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.Animation,
                    title = stringResource(R.string.settings_expressive_motion),
                    summary = stringResource(R.string.settings_expressive_motion_summary),
                    checked = rs.expressiveMotion,
                    onCheckedChange = vm::setExpressiveMotion,
                )
            },
        ),
    )
}

/** 手动取色分组（仅在关闭动态取色时展示；色值取自 theme 层的单一来源 [seedColorOptions]）。 */
@Composable
internal fun ManualColorSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_manual_color),
        items = listOf {
            SegmentedListItem(
                headlineContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        seedColorOptions.forEach { color ->
                            ColorDot(
                                color = Color(color),
                                selected = rs.seedColor == color,
                                onClick = { vm.setSeedColor(color) },
                            )
                        }
                    }
                },
            )
        },
    )
}

/**
 * 实验性分组：UI 风格切换（Material 3 Expressive ↔ MIUIX）。
 *
 * 参考 SukiSU-Ultra：整套界面代码只有一份，由 [LocalUiStyle] 决定公共组件走哪套实现，
 * 根主题相应切换 `MaterialExpressiveTheme` / `MiuixTheme`，因此切换无需重启。
 */
@Composable
internal fun ExperimentalSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
) {
    val styles = UiStyle.entries
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_section_experimental),
        items = listOf(
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Animation,
                    title = stringResource(R.string.settings_ui_style),
                    summary = stringResource(R.string.settings_ui_style_summary),
                    items = styles.map { style ->
                        stringResource(
                            when (style) {
                                UiStyle.MATERIAL3 -> R.string.settings_ui_style_material3
                                UiStyle.MIUIX -> R.string.settings_ui_style_miuix
                            },
                        )
                    },
                    selectedIndex = styles.indexOf(UiStyle.fromKey(rs.uiStyle)).coerceAtLeast(0),
                    onItemSelected = { index -> vm.setUiStyle(styles[index].key) },
                )
            },
            {
                // 多书架是**通用**实验功能（Material 与 MIUIX 都生效），
                // 因此和"仅 MIUIX 生效"的悬浮底栏/液态玻璃不同，它两套设置页都放。
                SegmentedSwitchItem(
                    icon = Icons.Filled.Collections,
                    title = stringResource(R.string.settings_multi_shelf),
                    summary = stringResource(R.string.settings_multi_shelf_summary),
                    checked = rs.multiShelfEnabled,
                    onCheckedChange = vm::setMultiShelfEnabled,
                )
            },
            // 说明：这里**不再**放「悬浮底栏 / 液态玻璃」两个开关。
            // M3 底栏是固定样式的 NavigationBar，不接受这两个设置（见 MainScaffold 的
            // `floatingBar = isMiuixStyle() && …`），摆在 MD3 页里只会让人以为"开了却没效果"。
            // 它们只属于 MIUIX 的实验性页（MiuixExperimentalPage）。
        ),
    )
}

/** 通用分组：触觉反馈总开关。 */
@Composable
internal fun GeneralSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_section_general),
        items = listOf(
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.Vibration,
                    title = stringResource(R.string.settings_haptics),
                    summary = stringResource(R.string.settings_haptics_summary),
                    checked = rs.hapticsEnabled,
                    onCheckedChange = vm::setHapticsEnabled,
                )
            },
        ),
    )
}

/** 振动强度分组（仅在开启触觉反馈时展示）。 */
@Composable
internal fun HapticsStrengthSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_haptics_strength_title),
        items = listOf {
            SegmentedListItem(
                headlineContent = {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_haptics_strength),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${rs.hapticsStrength}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ExpressiveSlider(
                            value = rs.hapticsStrength.toFloat(),
                            onValueChange = { vm.setHapticsStrength(it.toInt()) },
                            valueRange = 0f..100f,
                        )
                    }
                },
            )
        },
    )
}

/** 网络分组：主站镜像切换（清单来自单一来源 [Wenku8Hosts.MIRRORS]）。 */
@Composable
internal fun NetworkSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_section_network),
        items = listOf {
            SegmentedDropdownItem(
                icon = Icons.Filled.Public,
                title = stringResource(R.string.settings_primary_mirror),
                summary = stringResource(R.string.settings_primary_mirror_summary),
                items = Wenku8Hosts.MIRRORS,
                selectedIndex = Wenku8Hosts.MIRRORS.indexOfKey(rs.primaryMirror),
                onItemSelected = { index -> vm.setPrimaryMirror(Wenku8Hosts.MIRRORS[index]) },
            )
        },
    )
}

/** 更新分组：启动检查、更新通道、更新源、手动检查。 */
@Composable
internal fun UpdateSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
    version: String?,
    updateCenter: UpdateCenter,
) {
    val updateChannelOptions = listOf(
        "stable" to R.string.settings_update_channel_stable,
        "beta" to R.string.settings_update_channel_beta,
    )
    val updateSourceOptions = listOf(
        "github" to R.string.settings_update_source_github,
        "gh_proxy" to R.string.settings_update_source_ghproxy,
    )
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_section_update),
        items = listOf(
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.SystemUpdate,
                    title = stringResource(R.string.settings_check_on_startup),
                    summary = stringResource(R.string.settings_check_on_startup_summary),
                    checked = rs.checkUpdatesOnStartup,
                    onCheckedChange = vm::setCheckUpdatesOnStartup,
                )
            },
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_update_channel),
                    summary = stringResource(R.string.settings_update_channel_summary),
                    items = updateChannelOptions.map { (_, res) -> stringResource(res) },
                    selectedIndex = updateChannelOptions.indexOfKey(rs.updateChannel),
                    onItemSelected = { index -> vm.setUpdateChannel(updateChannelOptions[index].first) },
                )
            },
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.settings_update_source),
                    summary = stringResource(R.string.settings_update_source_summary),
                    items = updateSourceOptions.map { (_, res) -> stringResource(res) },
                    selectedIndex = updateSourceOptions.indexOfKey(rs.updateSource),
                    onItemSelected = { index -> vm.setUpdateSource(updateSourceOptions[index].first) },
                )
            },
            {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_check_update)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_version, version ?: "-"))
                    },
                    onClick = { updateCenter.check(manual = true) },
                )
            },
        ),
    )
}

// 分类二级页（PiliPlus 模式）

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
    )
}
