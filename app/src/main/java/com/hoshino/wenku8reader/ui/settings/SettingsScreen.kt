package com.hoshino.wenku8reader.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.di.AppContainer
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior
import com.hoshino.wenku8reader.ui.components.ExpressiveTopAppBar
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost

/**
 * 设置页（主 Tab 之一）。参考 SukiSU-Ultra 的 SettingsMaterial：
 * surfaceBright 分组卡片（SegmentedColumn）+ 折叠大顶栏。
 *
 * **PiliPlus 模式（2026-09 起）**：本页只放「分类入口」，具体设置项在各分类的二级页
 * （`AppearanceSettingsPage` / `NetworkSettingsPage` / `UpdateSettingsPage` /
 * `ExperimentalSettingsPage` / `StorageSettingsPage`），参考 PiliPlus
 * `pages/setting/view.dart` 的「分类 + 副标题 + 箭头 → 二级页」结构。
 * MIUIX 风格有各自独立的分类页（`ui/miuix/`），两套 UI 不共用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onOpenAppearance: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenExperimental: () -> Unit,
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

    // 更新检查（应用级单例）
    val updateCenter = remember(context) { context.appContainer.updateCenter }
    val updateState by updateCenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updateCenter.notices.collect { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() }
    }
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
            // 账号（说明性） + 各分类入口
            AccountSection()
            CategoryEntriesSection(
                version = version,
                onOpenAppearance = onOpenAppearance,
                onOpenReading = onOpenReading,
                onOpenNetwork = onOpenNetwork,
                onOpenUpdate = onOpenUpdate,
                onOpenExperimental = onOpenExperimental,
                onOpenStorageSettings = onOpenStorageSettings,
                onOpenAbout = onOpenAbout,
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

/**
 * 分类入口分组（设置主页的核心）：每行 = 分类名 + 一句话说明 + 箭头，点击进对应二级页。
 * 顺序与 MIUIX 版保持一致，方便两套风格对照维护。
 */
@Composable
private fun CategoryEntriesSection(
    version: String?,
    onOpenAppearance: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        items = listOf(
            {
                CategoryEntry(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_section_appearance),
                    summary = stringResource(R.string.settings_category_appearance_summary),
                    onClick = onOpenAppearance,
                )
            },
            {
                CategoryEntry(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_section_reading),
                    summary = stringResource(R.string.settings_category_reading_summary),
                    onClick = onOpenReading,
                )
            },
            {
                CategoryEntry(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.settings_section_network),
                    summary = stringResource(R.string.settings_category_network_summary),
                    onClick = onOpenNetwork,
                )
            },
            {
                CategoryEntry(
                    icon = Icons.Filled.SystemUpdate,
                    title = stringResource(R.string.settings_section_update),
                    summary = stringResource(R.string.settings_category_update_summary),
                    onClick = onOpenUpdate,
                )
            },
            {
                CategoryEntry(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.settings_section_storage),
                    summary = stringResource(R.string.settings_category_storage_summary),
                    onClick = onOpenStorageSettings,
                )
            },
            {
                CategoryEntry(
                    icon = Icons.Filled.Animation,
                    title = stringResource(R.string.settings_section_experimental),
                    summary = stringResource(R.string.settings_category_experimental_summary),
                    onClick = onOpenExperimental,
                )
            },
            {
                CategoryEntry(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_section_about),
                    summary = stringResource(R.string.settings_category_about_summary),
                    onClick = onOpenAbout,
                )
            },
        ),
    )
}

@Composable
private fun CategoryEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
    )
}

// ------------------------------------------------------------------ //

/** 分类二级页的公共骨架：大顶栏（带返回）+ 可滚动内容。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = rememberExpressiveScrollBehavior()
    ExpressiveScaffold(
        topBar = {
            ExpressiveLargeTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 外观与主题（含通用/触觉：都属于"观感"这一类）。 */
@Composable
fun AppearanceSettingsPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CategoryPageScaffold(stringResource(R.string.settings_section_appearance), onBack) {
        AppearanceSection(rs = rs, vm = vm, context = context)
        if (!rs.dynamicColor) {
            ManualColorSection(rs = rs, vm = vm)
        }
        GeneralSection(rs = rs, vm = vm)
        if (rs.hapticsEnabled) {
            HapticsStrengthSection(rs = rs, vm = vm)
        }
    }
}

/** 网络（主站镜像切换）。 */
@Composable
fun NetworkSettingsPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    CategoryPageScaffold(stringResource(R.string.settings_section_network), onBack) {
        NetworkSection(rs = rs, vm = vm)
    }
}

/** 更新（启动检查 / 通道 / 更新源 / 手动检查）。 */
@Composable
fun UpdateSettingsPage(
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
    LaunchedEffect(Unit) {
        updateCenter.notices.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    CategoryPageScaffold(stringResource(R.string.settings_section_update), onBack) {
        UpdateSection(rs = rs, vm = vm, version = version, updateCenter = updateCenter)
    }
}

/** 实验性（UI 风格 / 悬浮底栏 / 液态玻璃）。 */
@Composable
fun ExperimentalSettingsPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    CategoryPageScaffold(stringResource(R.string.settings_section_experimental), onBack) {
        ExperimentalSection(rs = rs, vm = vm)
    }
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
        title = stringResource(R.string.tab_settings),
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
 * 递归解包 ContextWrapper 找宿主 Activity。
 *
 * `LocalContext` 通常是 Activity，但在预览/测试或被包装的 Context 场景下不是，
 * 直接 `as? Activity` 会静默失败（语言切换后既不重建也无提示）。
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 应用级单例容器：设置页需要直接读更新中心与网络客户端（二者由 Application 持有）。 */
private val Context.appContainer: AppContainer
    get() = (applicationContext as Wenku8Application).container

