package com.hoshino.wenku8reader.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.data.Wenku8Hosts
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.di.AppContainer
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.ExpressiveTopAppBar
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedDropdownItem
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.SegmentedSwitchItem
import com.hoshino.wenku8reader.ui.theme.seedColorOptions
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost
import java.util.Locale

/**
 * 设置页（主 Tab 之一）。参考 SukiSU-Ultra 的 SettingsMaterial：
 * surfaceBright 分组卡片（SegmentedColumn）+ 折叠大顶栏。
 *
 * 本函数只做「取状态 + 按固定顺序组合各分组」，每个分组各自是独立的 `XxxSection`，
 * 避免单个函数 300+ 行、内联 8 个分组字面量导致的阅读与改动成本。
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
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    // 更新检查（应用级单例）
    val updateCenter = remember(context) { context.appContainer.updateCenter }
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
            // 分组顺序与原实现完全一致：
            // 账号 → 外观 →（手动取色）→ 通用 →（振动强度）→ 缓存 → 网络 → 更新 → 阅读 → 关于
            AccountSection()
            AppearanceSection(rs = rs, vm = vm, context = context)
            if (!rs.dynamicColor) {
                ManualColorSection(rs = rs, vm = vm)
            }
            GeneralSection(rs = rs, vm = vm)
            if (rs.hapticsEnabled) {
                HapticsStrengthSection(rs = rs, vm = vm)
            }
            CacheManagementSection(vm = vm, rs = rs)
            NetworkSection(rs = rs, vm = vm)
            UpdateSection(rs = rs, vm = vm, version = version, updateCenter = updateCenter)
            ReadingSection(onOpenCustom = onOpenCustom)
            AboutSection(onOpenAbout = onOpenAbout, version = version)

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
private fun AccountSection() {
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
private fun AppearanceSection(
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
private fun ManualColorSection(
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

/** 通用分组：触觉反馈总开关。 */
@Composable
private fun GeneralSection(
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
private fun HapticsStrengthSection(
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

/** 网络分组：主站镜像切换（清单来自单一来源 [Wenku8Hosts.MIRRORS]）。 */
@Composable
private fun NetworkSection(
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
private fun UpdateSection(
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

/** 阅读设置分组：进入阅读器自定义页。 */
@Composable
private fun ReadingSection(onOpenCustom: () -> Unit) {
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
}

/** 关于分组：应用信息与版本。 */
@Composable
private fun AboutSection(
    onOpenAbout: () -> Unit,
    version: String?,
) {
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
}

// ------------------------------------------------------------------ //
// 顶栏 / 缓存管理
// ------------------------------------------------------------------ //

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onOpenDownloads: () -> Unit,
) {
    ExpressiveTopAppBar(
        title = { Text(stringResource(R.string.tab_settings)) },
        actions = {
            IconButton(onClick = onOpenDownloads) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.action_downloads),
                )
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
}

/**
 * 磁盘缓存类别：`key` 必须与 `HtmlDiskCache` 的文件名前缀一致（`{category}_{md5}.html`，
 * 见 `HtmlDiskCache.put`），类别名集中在此定义，避免魔法字符串散落在统计展示与清理两处。
 */
private enum class CacheCategory(val key: String, @StringRes val labelRes: Int) {
    HOME("home", R.string.settings_cache_home),
    BOOK("book", R.string.settings_cache_book),
    CHAPTER("chapter", R.string.settings_cache_chapter),
    TAG("tag", R.string.settings_cache_tag),
    OTHER("other", R.string.settings_cache_other),
    LEGACY("legacy", R.string.settings_cache_legacy),
}

/** 缓存管理分组：总大小 + 分类明细（可单项清理）+ 清理全部 + 上限设置。 */
@Composable
private fun CacheManagementSection(
    vm: SettingsViewModel,
    rs: ReaderSettingsState,
) {
    val cacheSizes by vm.cacheSizes.collectAsStateWithLifecycle()
    val total = cacheSizes.values.sum()
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
            // 分类顺序固定展示；缺失/为 0 的类别显示 0B
            CacheCategory.entries.forEach { category ->
                add {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(category.labelRes)) },
                        supportingContent = { Text(formatSize(cacheSizes[category.key] ?: 0L)) },
                        trailingContent = {
                            TextButton(onClick = { vm.clearCache(category.key) }) {
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
                    selectedIndex = maxOptions.indexOfKey(rs.cacheMaxMb),
                    onItemSelected = { index -> vm.setCacheMaxMb(maxOptions[index]) },
                )
            }
        },
    )
}

// ------------------------------------------------------------------ //
// helpers
// ------------------------------------------------------------------ //

/**
 * 人类可读的文件大小：B / KB / MB / GB。
 *
 * 数字格式显式固定 [Locale.US]：`ar`/`fa` 等区域的默认 Locale 会输出本地化数字字符，
 * 既不符合这里的展示语义（与 MB/GB 单位混排），也让同一份缓存大小在不同语言下形态不一致。
 */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        "%.1f GB".format(Locale.US, bytes / (1024f * 1024 * 1024))
    bytes >= 1024L * 1024 ->
        "%.1f MB".format(Locale.US, bytes / (1024f * 1024))
    bytes >= 1024L ->
        "%.0f KB".format(Locale.US, bytes / 1024f)
    else -> "$bytes B"
}

/**
 * 下拉选项的选中下标：找不到时回退 0。
 *
 * `SegmentedDropdownItem.selectedIndex` 不接受负值，而设置值可能来自旧版本或被移除的选项，
 * 统一在此收敛为 -1 → 0，避免每个调用点都重复写一遍 `coerceAtLeast(0)`。
 */
private fun <T> List<T>.indexOfKey(key: T): Int = indexOf(key).coerceAtLeast(0)

/** 同上，但选项以 `(key, value)` 对存放：按 key 匹配而不是按整个 Pair 匹配。 */
private fun <T> List<Pair<String, T>>.indexOfKey(key: String): Int =
    indexOfFirst { it.first == key }.coerceAtLeast(0)

/**
 * 递归解包 ContextWrapper 找宿主 Activity。
 *
 * `LocalContext` 通常是 Activity，但在预览/测试或被包装的 Context 场景下不是，
 * 直接 `as? Activity` 会静默失败（语言切换后既不重建也无提示）。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 应用级单例容器：设置页需要直接读更新中心与网络客户端（二者由 Application 持有）。 */
private val Context.appContainer: AppContainer
    get() = (applicationContext as Wenku8Application).container

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
