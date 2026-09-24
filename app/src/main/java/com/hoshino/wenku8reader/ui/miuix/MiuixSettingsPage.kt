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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * MIUIX 设置页（主 Tab 之一）——**与 Material 版完全独立的实现**。
 *
 * 结构照 HyperOS 设置页来：大标题（"设置"）→ 分组卡片，每组一张卡、卡内缩进分隔线；
 * 全部组件取自 miuix（Card/BasicComponent/ArrowPreference/SwitchPreference/
 * WindowDropdownPreference/Slider/Text），不复用 `ui/settings/SettingsScreen.kt` 的
 * Material 实现，也不经过 `ui/components/Expressive.kt` 的分派门面。
 * 逻辑（ReaderSettings 读写、更新检查、镜像切换）与 Material 版共用同一个
 * [SettingsViewModel]，两套界面只是"渲染层"不同。
 */
@Composable
fun MiuixSettingsPage(
    onOpenCustom: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStorageSettings: () -> Unit,
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
    val updateState by updateCenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updateCenter.notices.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    MiuixPage(
        title = stringResource(R.string.tab_settings),
        actions = {
            MiuixIconButton(
                icon = Icons.Filled.Download,
                contentDescription = stringResource(R.string.action_downloads),
                onClick = onOpenDownloads,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            // 账号（说明性，无交互）
            MiuixSection(
                title = stringResource(R.string.settings_section_account),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                MiuixRow(
                    title = stringResource(R.string.settings_builtin_account),
                    summary = stringResource(R.string.settings_account_builtin_desc),
                    icon = Icons.Filled.AccountCircle,
                )
            }
            MiuixAppearanceSection(rs = rs, vm = vm, context = context)
            MiuixGeneralSection(rs = rs, vm = vm)
            MiuixStorageEntrySection(
                onOpenStorageSettings = onOpenStorageSettings,
                onOpenDownloads = onOpenDownloads,
            )
            MiuixExperimentalSection(rs = rs, vm = vm)
            MiuixNetworkSection(rs = rs, vm = vm)
            MiuixUpdateSection(rs = rs, vm = vm, version = version, updateCenter = updateCenter)
            MiuixReadingSection(onOpenCustom = onOpenCustom)
            MiuixAboutSection(onOpenAbout = onOpenAbout, version = version)
            Spacer(Modifier.height(24.dp))
        }
    }

    UpdateDialogHost(
        state = updateState,
        currentVersionName = updateCenter.currentVersionName,
        onUpdate = updateCenter::download,
        onLater = updateCenter::later,
        onSkip = updateCenter::skip,
    )
}

@Composable
private fun MiuixAppearanceSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
    context: Context,
) {
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
    MiuixSection(
        title = stringResource(R.string.settings_section_appearance),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
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
        // 说明：MIUIX 不使用动态取色，配色由 miuix 自己的色板决定（HyperOS 默认蓝），
        // 因此这里**不展示**「动态取色 / 手动主题色 / 纯黑模式」这些 MD3 专属设置项；
        // 它们只在 Material 3 风格的设置页出现，两套设置互不干扰。
    }
}

@Composable
private fun MiuixGeneralSection(rs: ReaderSettingsState, vm: SettingsViewModel) {
    MiuixSection(
        title = stringResource(R.string.settings_section_general),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
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
                value = rs.hapticsStrength.toFloat(),
                valueRange = 0f..100f,
                valueText = "${rs.hapticsStrength}%",
                onValueChange = { vm.setHapticsStrength(it.toInt()) },
            )
        }
    }
}

@Composable
private fun MiuixStorageEntrySection(
    onOpenStorageSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    MiuixSection(
        title = stringResource(R.string.settings_section_storage),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
        MiuixArrowRow(
            title = stringResource(R.string.settings_storage),
            summary = stringResource(R.string.settings_storage_desc),
            icon = Icons.Filled.Storage,
            onClick = onOpenStorageSettings,
        )
        MiuixRowDivider()
        MiuixArrowRow(
            title = stringResource(R.string.action_downloads),
            icon = Icons.Filled.Download,
            onClick = onOpenDownloads,
        )
    }
}

@Composable
private fun MiuixExperimentalSection(rs: ReaderSettingsState, vm: SettingsViewModel) {
    val styles = UiStyle.entries
    // UI 风格的切换会替换整棵界面（含 miuix 根宿主），必须等下拉弹层收起后再应用，
    // 否则弹层宿主会在显示过程中被销毁（表现为闪退）。
    var pendingStyleIndex by remember { mutableStateOf<Int?>(null) }
    MiuixSection(
        title = stringResource(R.string.settings_section_experimental),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
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
    }
}

@Composable
private fun MiuixNetworkSection(rs: ReaderSettingsState, vm: SettingsViewModel) {
    MiuixSection(
        title = stringResource(R.string.settings_section_network),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
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

@Composable
private fun MiuixUpdateSection(
    rs: ReaderSettingsState,
    vm: SettingsViewModel,
    version: String?,
    updateCenter: UpdateCenter,
) {
    val channelOptions = listOf(
        "stable" to R.string.settings_update_channel_stable,
        "beta" to R.string.settings_update_channel_beta,
    )
    val sourceOptions = listOf(
        "github" to R.string.settings_update_source_github,
        "gh_proxy" to R.string.settings_update_source_ghproxy,
    )
    MiuixSection(
        title = stringResource(R.string.settings_section_update),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
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

@Composable
private fun MiuixReadingSection(onOpenCustom: () -> Unit) {
    MiuixSection(
        title = stringResource(R.string.settings_section_reading),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
        MiuixArrowRow(
            title = stringResource(R.string.settings_custom),
            summary = stringResource(R.string.settings_custom_desc),
            icon = Icons.Filled.Tune,
            onClick = onOpenCustom,
        )
    }
}

@Composable
private fun MiuixAboutSection(onOpenAbout: () -> Unit, version: String?) {
    MiuixSection(
        title = stringResource(R.string.settings_section_about),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
    ) {
        MiuixArrowRow(
            title = stringResource(R.string.app_name),
            summary = stringResource(R.string.settings_version, version ?: "-"),
            icon = Icons.Filled.Info,
            onClick = onOpenAbout,
        )
    }
}

private val Context.appContainer: AppContainer
    get() = (applicationContext as Wenku8Application).container

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 版本号格式化所需的显式 Locale（与 Material 版一致的约定）。 */
@Suppress("unused")
private val locale = Locale.US
