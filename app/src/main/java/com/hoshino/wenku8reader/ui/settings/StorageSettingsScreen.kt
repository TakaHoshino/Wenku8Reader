package com.hoshino.wenku8reader.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.CacheCategory
import com.hoshino.wenku8reader.ui.common.formatByteSize
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedDropdownItem
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior

/**
 * 存储设置二级页：占用合计（与系统设置同口径）、按类型清理、过期阅读记录、缓存上限。
 *
 * 为什么独立成页：主设置页要承载账号/外观/网络/更新/阅读/关于等十余个分组，
 * 存储明细（3 个合计值 + 6 类缓存 + 分类明细）会把页面拉得很长；
 * 主设置页只保留一个入口行（见 SettingsScreen 的 StorageSection）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val scrollBehavior = rememberExpressiveScrollBehavior()

    // 清理结果提示：ViewModel 只回传「发生了什么 + 释放了多少」，文案在此渲染
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.cacheResults.collect { result ->
            val text = when (result.kind) {
                CacheActionResult.Kind.READING_HISTORY ->
                    if (result.affectedBooks > 0) {
                        context.getString(R.string.settings_cache_reading_cleaned, result.affectedBooks)
                    } else {
                        context.getString(R.string.settings_cache_reading_nothing)
                    }

                else ->
                    if (result.freedSomething) {
                        context.getString(R.string.settings_cache_freed, formatByteSize(result.freedBytes))
                    } else {
                        context.getString(R.string.settings_cache_nothing)
                    }
            }
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // 进入页面刷新存储占用统计（统计在 ViewModel 的 IO 线程执行）
    LaunchedEffect(Unit) { vm.refreshStorageStats() }

    ExpressiveScaffold(
        topBar = {
            ExpressiveLargeTopAppBar(
                title = stringResource(R.string.settings_storage),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            StorageSettingsContent(vm = vm, rs = rs)
            Spacer(Modifier.height(24.dp))
        }
    }
}


/**
 * 缓存与存储管理分组。
 *
 * 关键点：**口径必须和系统设置一致**。系统「应用信息 → 存储」把 `cacheDir` 记为「缓存」、
 * 数据目录记为「用户数据」；旧实现只统计 `filesDir/html_cache` 一个目录，于是出现
 * "系统 33MB/36MB、应用内 2.8MB"的落差。这里先给出两个合计值（可与系统设置直接对照），
 * 再按类型列出可清理项与单项清理入口。
 */
@Composable
private fun StorageSettingsContent(
    vm: SettingsViewModel,
    rs: ReaderSettingsState,
) {
    val cacheSizes by vm.cacheSizes.collectAsStateWithLifecycle()
    val stats by vm.storageStats.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmCleanupHistory by remember { mutableStateOf(false) }
    // 上限选项：默认 30 含在其中，其余按常用档位
    val maxOptions = listOf(30, 50, 100, 200, 500)

    // ---- 占用合计（与系统设置对照）----
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_cache_storage_title),
        items = listOf(
            {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_total_cache)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.cacheDirTotal) + " · " +
                                stringResource(R.string.settings_cache_total_cache_desc),
                        )
                    },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.settings_cache_total_data)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.dataDirTotal) + " · " +
                                stringResource(R.string.settings_cache_total_data_desc),
                        )
                    },
                )
            },
            {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.settings_cache_code_cache)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.codeCache) + " · " +
                                stringResource(R.string.settings_cache_code_cache_desc),
                        )
                    },
                )
            },
        ),
    )

    // ---- 按类型清理 ----
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_cache),
        items = buildList {
            add {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_images)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.imageCache) + " · " +
                                stringResource(R.string.settings_cache_images_desc),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { vm.clearImageCache() }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                )
            }
            add {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_updates)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.updatePackage) + " · " +
                                stringResource(R.string.settings_cache_updates_desc),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { vm.clearUpdatePackages() }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                )
            }
            add {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.Public, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_other_title)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.webViewCache + stats.tempFiles + stats.otherCache) + " · " +
                                stringResource(R.string.settings_cache_other_desc),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { vm.clearOtherCaches() }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                )
            }
            add {
                SegmentedListItem(
                    leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_reading_data)) },
                    supportingContent = {
                        Text(
                            formatByteSize(stats.preferences) + " · " +
                                stringResource(R.string.settings_cache_reading_data_desc),
                        )
                    },
                )
            }
            add {
                // 与上一行分开：上一行是"这类数据占了多少"，这一行才是"能清理什么"。
                // 只清理过期记录（本地数据里的书架、设置、Cookie 一律不动）。
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.settings_cache_cleanup_reading)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.settings_cache_cleanup_reading_desc,
                                AppPreferences.DEFAULT_KEEP_DAYS,
                            ),
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { confirmCleanupHistory = true }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                )
            }
            add {
                SegmentedListItem(
                    onClick = { confirmClearAll = true },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_cache_clear_all)) },
                    supportingContent = { Text(stringResource(R.string.settings_cache_clear_all_desc)) },
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

    // ---- 网页离线缓存分类明细（可单项清理）----
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(R.string.settings_cache_html_detail),
        items = buildList {
            // 分类顺序固定展示；缺失/为 0 的类别显示 0B
            CacheCategory.entries.forEach { category ->
                add {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(category.labelRes)) },
                        supportingContent = { Text(formatByteSize(cacheSizes[category.key] ?: 0L)) },
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
                    headlineContent = { Text(stringResource(R.string.settings_cache_size)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_cache_size_summary, formatByteSize(stats.htmlCache), rs.cacheMaxMb))
                    },
                    trailingContent = {
                        TextButton(onClick = { vm.clearCache(null) }) {
                            Text(stringResource(R.string.settings_cache_clear))
                        }
                    },
                )
            }
        },
    )

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.settings_cache_confirm_clear_all_title)) },
            text = { Text(stringResource(R.string.settings_cache_confirm_clear_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        vm.clearAllCaches()
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmCleanupHistory) {
        AlertDialog(
            onDismissRequest = { confirmCleanupHistory = false },
            title = { Text(stringResource(R.string.settings_cache_confirm_cleanup_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_cache_confirm_cleanup_message,
                        AppPreferences.DEFAULT_KEEP_DAYS,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCleanupHistory = false
                        vm.cleanupReadingHistory()
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanupHistory = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

