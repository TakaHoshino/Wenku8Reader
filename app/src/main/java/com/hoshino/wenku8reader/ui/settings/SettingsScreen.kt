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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedDropdownItem
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.SegmentedSwitchItem
import com.hoshino.wenku8reader.ui.theme.seedColorOptions

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
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
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
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.settings_section_appearance),
                items = listOf(
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
