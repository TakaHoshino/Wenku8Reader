package com.hoshino.wenku8reader.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.data.Wenku8Hosts
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.di.AppContainer
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import com.hoshino.wenku8reader.ui.theme.UiStyle
import kotlin.math.roundToInt

/**
 * 设置分类二级页（PiliPlus 模式：设置主页只放分类入口，具体设置项都在二级页里）。
 *
 * 每页只用 miuix 组件（`MiuixSubPage` + `MiuixSection` + 各类 miuix 行），
 * 与 Material 版设置页互不共用 UI 代码；逻辑仍复用 [SettingsViewModel]。
 */
@Composable
fun MiuixAppearancePage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MiuixCategoryPage(title = stringResource(R.string.settings_section_appearance), onBack = onBack) {
        val languageOptions = listOf(
            "system" to R.string.settings_language_system,
            "zh-CN" to R.string.settings_language_simplified,
            "zh-TW" to R.string.settings_language_traditional,
        )
        val darkModeOptions = listOf(
            "system" to R.string.settings_dark_system,
            "light" to R.string.settings_dark_light,
            "dark" to R.string.settings_dark_dark,
        )
        MiuixSection(title = stringResource(R.string.settings_section_appearance)) {
            MiuixDropdownRow(
                title = stringResource(R.string.settings_language),
                summary = stringResource(R.string.settings_language_summary),
                icon = Icons.Filled.Language,
                items = languageOptions.map { stringResource(it.second) },
                selectedIndex = languageOptions.indexOfFirst { it.first == rs.appLanguage }
                    .coerceAtLeast(0),
                onSelected = { index ->
                    val lang = languageOptions[index].first
                    if (lang != rs.appLanguage) {
                        vm.setAppLanguage(lang)
                        context.findActivity()?.recreate()
                    }
                },
            )
            MiuixRowDivider()
            MiuixDropdownRow(
                title = stringResource(R.string.settings_dark_mode),
                summary = stringResource(R.string.settings_dark_mode_summary),
                icon = Icons.Filled.DarkMode,
                items = darkModeOptions.map { stringResource(it.second) },
                selectedIndex = darkModeOptions.indexOfFirst { it.first == rs.darkMode }
                    .coerceAtLeast(0),
                onSelected = { index -> vm.setDarkMode(darkModeOptions[index].first) },
            )
            // MIUIX 不使用动态取色：配色由 miuix 色板决定，故此处不出现
            // 「动态取色 / 手动主题色 / 纯黑模式」这些 MD3 专属设置项。
        }
        Spacer(Modifier.height(13.dp))
        // 通用：触觉反馈（与 Material 版同在一处分组的做法一致）。
        // 这是应用行为设置、不是 MD3 专属项，所以 MIUIX 下同样要能调。
        MiuixSection(title = stringResource(R.string.settings_section_general)) {
            MiuixSwitchRow(
                title = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_summary),
                icon = Icons.Filled.Vibration,
                checked = rs.hapticsEnabled,
                onCheckedChange = vm::setHapticsEnabled,
            )
            if (rs.hapticsEnabled) {
                MiuixRowDivider()
                MiuixSliderRow(
                    title = stringResource(R.string.settings_haptics_strength),
                    valueText = "${rs.hapticsStrength}%",
                    value = rs.hapticsStrength.toFloat(),
                    onValueChange = { vm.setHapticsStrength(it.roundToInt()) },
                    valueRange = 0f..100f,
                )
            }
        }
    }
}

@Composable
fun MiuixNetworkPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    MiuixCategoryPage(title = stringResource(R.string.settings_section_network), onBack = onBack) {
        MiuixSection(title = stringResource(R.string.settings_section_network)) {
            MiuixDropdownRow(
                title = stringResource(R.string.settings_primary_mirror),
                summary = stringResource(R.string.settings_primary_mirror_summary),
                icon = Icons.Filled.Public,
                items = Wenku8Hosts.MIRRORS,
                selectedIndex = Wenku8Hosts.MIRRORS.indexOf(rs.primaryMirror).coerceAtLeast(0),
                onSelected = { index -> vm.setPrimaryMirror(Wenku8Hosts.MIRRORS[index]) },
            )
        }
    }
}

@Composable
fun MiuixUpdatePage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    val updateCenter = remember(context) { context.appContainer.updateCenter }
    val channelOptions = listOf(
        "stable" to R.string.settings_update_channel_stable,
        "beta" to R.string.settings_update_channel_beta,
    )
    val sourceOptions = listOf(
        "github" to R.string.settings_update_source_github,
        "gh_proxy" to R.string.settings_update_source_ghproxy,
    )
    MiuixCategoryPage(title = stringResource(R.string.settings_section_update), onBack = onBack) {
        MiuixSection(title = stringResource(R.string.settings_section_update)) {
            MiuixSwitchRow(
                title = stringResource(R.string.settings_check_on_startup),
                summary = stringResource(R.string.settings_check_on_startup_summary),
                icon = Icons.Filled.SystemUpdate,
                checked = rs.checkUpdatesOnStartup,
                onCheckedChange = vm::setCheckUpdatesOnStartup,
            )
            MiuixRowDivider()
            MiuixDropdownRow(
                title = stringResource(R.string.settings_update_channel),
                summary = stringResource(R.string.settings_update_channel_summary),
                icon = Icons.Filled.Tune,
                items = channelOptions.map { stringResource(it.second) },
                selectedIndex = channelOptions.indexOfFirst { it.first == rs.updateChannel }
                    .coerceAtLeast(0),
                onSelected = { index -> vm.setUpdateChannel(channelOptions[index].first) },
            )
            MiuixRowDivider()
            MiuixDropdownRow(
                title = stringResource(R.string.settings_update_source),
                summary = stringResource(R.string.settings_update_source_summary),
                icon = Icons.Filled.CloudDownload,
                items = sourceOptions.map { stringResource(it.second) },
                selectedIndex = sourceOptions.indexOfFirst { it.first == rs.updateSource }
                    .coerceAtLeast(0),
                onSelected = { index -> vm.setUpdateSource(sourceOptions[index].first) },
            )
            MiuixRowDivider()
            MiuixRow(
                title = stringResource(R.string.settings_check_update),
                summary = stringResource(R.string.settings_version, version ?: "-"),
                icon = Icons.Filled.Refresh,
                onClick = { updateCenter.check(manual = true) },
            )
        }
    }
}

@Composable
fun MiuixExperimentalPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val styles = UiStyle.entries
    // UI 风格切换会替换整棵界面（含 miuix 根宿主），必须等下拉弹层收起后再应用
    var pendingStyleIndex by remember { mutableStateOf<Int?>(null) }
    MiuixCategoryPage(title = stringResource(R.string.settings_section_experimental), onBack = onBack) {
        MiuixSection(title = stringResource(R.string.settings_section_experimental)) {
            MiuixDropdownRow(
                title = stringResource(R.string.settings_ui_style),
                summary = stringResource(R.string.settings_ui_style_summary),
                icon = Icons.Filled.Animation,
                items = styles.map { style ->
                    stringResource(
                        when (style) {
                            UiStyle.MATERIAL3 -> R.string.settings_ui_style_material3
                            UiStyle.MIUIX -> R.string.settings_ui_style_miuix
                        },
                    )
                },
                selectedIndex = styles.indexOf(UiStyle.fromKey(rs.uiStyle)).coerceAtLeast(0),
                onSelected = { index -> pendingStyleIndex = index },
                onExpandedChange = { expanded ->
                    if (!expanded) {
                        pendingStyleIndex?.let { index ->
                            pendingStyleIndex = null
                            vm.setUiStyle(styles[index].key)
                        }
                    }
                },
            )
            MiuixRowDivider()
            MiuixSwitchRow(
                title = stringResource(R.string.settings_floating_bottom_bar),
                summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                icon = Icons.Filled.Tune,
                checked = rs.floatingBottomBar,
                onCheckedChange = vm::setFloatingBottomBar,
            )
            MiuixRowDivider()
            MiuixSwitchRow(
                title = stringResource(R.string.settings_bottom_bar_glass),
                summary = stringResource(R.string.settings_bottom_bar_glass_summary),
                icon = Icons.Filled.Palette,
                checked = rs.bottomBarGlass,
                enabled = rs.floatingBottomBar,
                onCheckedChange = vm::setBottomBarGlass,
            )
            MiuixRowDivider()
            // 多书架是通用实验功能（Material 与 MIUIX 都生效），不像上面两个只影响 MIUIX
            MiuixSwitchRow(
                title = stringResource(R.string.settings_multi_shelf),
                summary = stringResource(R.string.settings_multi_shelf_summary),
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                checked = rs.multiShelfEnabled,
                onCheckedChange = vm::setMultiShelfEnabled,
            )
        }
    }
}

/** 分类二级页的公共骨架：miuix 小标题顶栏 + 可滚动内容（含悬浮底栏余量）。 */
@Composable
private fun MiuixCategoryPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    MiuixSubPage(title = title, onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(horizontal = 16.dp)) { content() }
            Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
        }
    }
}

private val Context.appContainer: AppContainer
    get() = (applicationContext as Wenku8Application).container

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
