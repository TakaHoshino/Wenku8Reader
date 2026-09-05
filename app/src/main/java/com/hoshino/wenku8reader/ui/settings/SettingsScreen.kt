package com.hoshino.wenku8reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedDropdownItem
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.SegmentedSwitchItem
import com.hoshino.wenku8reader.ui.theme.seedColorOptions
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost

/**
 * 设置页（主 Tab 之一）。参考 SukiSU-Ultra 的 SettingsMaterial：
 * surfaceBright 分组卡片（SegmentedColumn）+ 折叠大顶栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onOpenCustom: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val cacheSizes by vm.cacheSizes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    // 更新检查（应用级单例）
    val updateCenter = remember(context) {
        (context.applicationContext as com.hoshino.wenku8reader.Wenku8Application)
            .container.updateCenter
    }
    val updateState by updateCenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updateCenter.notices.collect { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() }
    }
    // 进入设置页时刷新缓存大小统计
    LaunchedEffect(Unit) { vm.refreshCacheSizes() }

    // 静态顶栏（64dp）：去掉折叠顶栏的逐帧布局级联，滚动更顺滑
    ExpressiveScaffold(
        topBar = {
            TopBar(onOpenDownloads = onOpenDownloads)
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            // 账号
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.settings_section_account),
                items = listOf {
                    SegmentedListItem(
                        leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_builtin_account)) },
                        supportingContent = { Text(stringResource(R.string.settings_builtin_account_hint)) },
                    )
                },
            )

            // 外观（应用主题）
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
                            selectedIndex = languageOptions.indexOfFirst { it.first == rs.appLanguage }
                                .coerceAtLeast(0),
                            onItemSelected = { index ->
                                val lang = languageOptions[index].first
                                if (lang != rs.appLanguage) {
                                    vm.setAppLanguage(lang)
                                    (context as? android.app.Activity)?.recreate()
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
                            selectedIndex = darkModeOptions.indexOfFirst { it.first == rs.darkMode }
                                .coerceAtLeast(0),
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
                ),
            )
            if (!rs.dynamicColor) {
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

            // 通用（触感反馈 + 缓存管理）
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
            if (rs.hapticsEnabled) {
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
                                    Slider(
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
            CacheManagementSection(vm = vm, rs = rs)

            // 网络（主站镜像切换）
            val mirrorOptions = listOf(
                "https://www.wenku8.cc",
                "https://www.wenku8.net",
                "https://www.wenku8.com",
            )
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.settings_section_network),
                items = listOf {
                    SegmentedDropdownItem(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.settings_primary_mirror),
                        summary = stringResource(R.string.settings_primary_mirror_summary),
                        items = mirrorOptions,
                        selectedIndex = mirrorOptions.indexOf(rs.primaryMirror).coerceAtLeast(0),
                        onItemSelected = { index -> vm.setPrimaryMirror(mirrorOptions[index]) },
                    )
                },
            )

            // 更新
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
                            selectedIndex = updateChannelOptions.indexOfFirst { it.first == rs.updateChannel }
                                .coerceAtLeast(0),
                            onItemSelected = { index -> vm.setUpdateChannel(updateChannelOptions[index].first) },
                        )
                    },
                    {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.CloudDownload,
                            title = stringResource(R.string.settings_update_source),
                            summary = stringResource(R.string.settings_update_source_summary),
                            items = updateSourceOptions.map { (_, res) -> stringResource(res) },
                            selectedIndex = updateSourceOptions.indexOfFirst { it.first == rs.updateSource }
                                .coerceAtLeast(0),
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

            // 阅读器自定义
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.settings_section_reading),
                items = listOf {
                    SegmentedListItem(
                        onClick = onOpenCustom,
                        leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_custom)) },
                        supportingContent = { Text(stringResource(R.string.settings_custom_desc)) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                    )
                },
            )

            // 关于
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.settings_section_about),
                items = listOf {
                    SegmentedListItem(
                        onClick = onOpenAbout,
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = { Text(stringResource(R.string.settings_version, version ?: "-")) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                    )
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // 更新对话框（立即更新 / 稍后提醒 / 跳过该版本）
    UpdateDialogHost(
        state = updateState,
        currentVersionName = updateCenter.currentVersionName,
        onUpdate = updateCenter::download,
        onLater = updateCenter::later,
        onSkip = updateCenter::skip,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onOpenDownloads: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.tab_settings)) },
        actions = {
            IconButton(onClick = onOpenDownloads) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.action_downloads),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
}

/** 缓存管理分组：总大小 + 分类明细（可单项清理）+ 清理全部 + 上限设置。 */
@Composable
private fun CacheManagementSection(
    vm: SettingsViewModel,
    rs: ReaderSettingsState,
) {
    val cacheSizes by vm.cacheSizes.collectAsStateWithLifecycle()
    val total = cacheSizes.values.sum()
    // 分类顺序固定展示；缺失/为 0 的类别显示 0B
    val categories = listOf(
        "home" to R.string.settings_cache_home,
        "book" to R.string.settings_cache_book,
        "chapter" to R.string.settings_cache_chapter,
        "tag" to R.string.settings_cache_tag,
        "other" to R.string.settings_cache_other,
        "legacy" to R.string.settings_cache_legacy,
    )
    // 上限选项：默认 30 含在其中，其余按常用档位
    val maxOptions = listOf(30, 50, 100, 200, 500)
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_cache),
        items = buildList {
            add {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_size)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_cache_size_summary, formatSize(total), rs.cacheMaxMb))
                    },
                )
            }
            categories.forEach { (cat, labelRes) ->
                add {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(labelRes)) },
                        supportingContent = { Text(formatSize(cacheSizes[cat] ?: 0L)) },
                        trailingContent = {
                            TextButton(onClick = { vm.clearCache(cat) }) {
                                Text(stringResource(R.string.settings_cache_clear))
                            }
                        },
                    )
                }
            }
            add {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.settings_cache_clear_all)) },
                    supportingContent = { Text(stringResource(R.string.settings_cache_clear_all_desc)) },
                    onClick = { vm.clearCache(null) },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.settings_cache_max),
                    summary = stringResource(R.string.settings_cache_max_summary),
                    items = maxOptions.map { "$it MB" },
                    selectedIndex = maxOptions.indexOf(rs.cacheMaxMb).coerceAtLeast(0),
                    onItemSelected = { index -> vm.setCacheMaxMb(maxOptions[index]) },
                )
            }
        },
    )
}

/** 人类可读的文件大小：B / KB / MB / GB。 */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024f * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024f * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

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
