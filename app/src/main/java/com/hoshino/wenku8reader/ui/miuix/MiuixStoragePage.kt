package com.hoshino.wenku8reader.ui.miuix

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
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
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.settings.CacheActionResult
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.Locale

/**
 * MIUIX 存储设置页——与 Material 版（`ui/settings/StorageSettingsScreen.kt`）**完全独立**：
 * 只用 miuix 组件（Card / BasicComponent 系列 / WindowDialog / Slider-less 列表），
 * 分组卡片 + 缩进分隔线是 HyperOS 的形态。逻辑仍复用 [SettingsViewModel]（存储统计与清理动作）。
 */
@Composable
fun MiuixStoragePage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val stats by vm.storageStats.collectAsStateWithLifecycle()
    val cacheSizes by vm.cacheSizes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmCleanupHistory by remember { mutableStateOf(false) }

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
                        context.getString(R.string.settings_cache_freed, formatSize(result.freedBytes))
                    } else {
                        context.getString(R.string.settings_cache_nothing)
                    }
            }
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { vm.refreshStorageStats() }

    MiuixSubPage(title = stringResource(R.string.settings_storage), onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))

            // 占用合计（与系统设置同口径）
            MiuixSection(
                title = stringResource(R.string.settings_cache_storage_title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                MiuixRow(
                    title = stringResource(R.string.settings_cache_total_cache),
                    summary = formatSize(stats.cacheDirTotal) + " · " +
                        stringResource(R.string.settings_cache_total_cache_desc),
                    icon = Icons.Filled.Storage,
                )
                MiuixRowDivider()
                MiuixRow(
                    title = stringResource(R.string.settings_cache_total_data),
                    summary = formatSize(stats.dataDirTotal) + " · " +
                        stringResource(R.string.settings_cache_total_data_desc),
                )
                MiuixRowDivider()
                MiuixRow(
                    title = stringResource(R.string.settings_cache_code_cache),
                    summary = formatSize(stats.codeCache) + " · " +
                        stringResource(R.string.settings_cache_code_cache_desc),
                )
            }

            // 按类型清理
            MiuixSection(
                title = stringResource(R.string.settings_cache),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                MiuixClearRow(
                    title = stringResource(R.string.settings_cache_images),
                    summary = formatSize(stats.imageCache) + " · " +
                        stringResource(R.string.settings_cache_images_desc),
                    icon = Icons.Filled.Image,
                    onClear = vm::clearImageCache,
                )
                MiuixRowDivider()
                MiuixClearRow(
                    title = stringResource(R.string.settings_cache_updates),
                    summary = formatSize(stats.updatePackage) + " · " +
                        stringResource(R.string.settings_cache_updates_desc),
                    icon = Icons.Filled.SystemUpdate,
                    onClear = vm::clearUpdatePackages,
                )
                MiuixRowDivider()
                MiuixClearRow(
                    title = stringResource(R.string.settings_cache_other_title),
                    summary = formatSize(stats.webViewCache + stats.tempFiles + stats.otherCache) + " · " +
                        stringResource(R.string.settings_cache_other_desc),
                    icon = Icons.Filled.Public,
                    onClear = vm::clearOtherCaches,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_cache_clear_all),
                    summary = stringResource(R.string.settings_cache_clear_all_desc),
                    icon = Icons.Filled.Delete,
                    onClick = { confirmClearAll = true },
                )
            }

            // 网页离线缓存明细 + 上限
            MiuixSection(
                title = stringResource(R.string.settings_cache_html_detail),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                MiuixRow(
                    title = stringResource(R.string.settings_cache_size),
                    summary = stringResource(
                        R.string.settings_cache_size_summary,
                        formatSize(stats.htmlCache),
                        rs.cacheMaxMb,
                    ),
                )
                val maxOptions = listOf(30, 50, 100, 200, 500)
                MiuixRowDivider()
                MiuixDropdownRow(
                    title = stringResource(R.string.settings_cache_max),
                    summary = stringResource(R.string.settings_cache_max_summary),
                    icon = Icons.Filled.Storage,
                    items = maxOptions.map { "$it MB" },
                    selectedIndex = maxOptions.indexOf(rs.cacheMaxMb).coerceAtLeast(0),
                    onSelected = { index -> vm.setCacheMaxMb(maxOptions[index]) },
                )
                CacheCategory.entries.forEach { category ->
                    MiuixRowDivider()
                    MiuixClearRow(
                        title = stringResource(category.labelRes),
                        summary = formatSize(cacheSizes[category.key] ?: 0L),
                        onClear = { vm.clearCache(category.key) },
                    )
                }
            }

            // 本地数据与过期清理
            MiuixSection(
                title = stringResource(R.string.settings_cache_reading_data),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                MiuixRow(
                    title = stringResource(R.string.settings_cache_reading_data),
                    summary = formatSize(stats.preferences) + " · " +
                        stringResource(R.string.settings_cache_reading_data_desc),
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_cache_cleanup_reading),
                    summary = stringResource(
                        R.string.settings_cache_cleanup_reading_desc,
                        AppPreferences.DEFAULT_KEEP_DAYS,
                    ),
                    icon = Icons.Filled.History,
                    onClick = { confirmCleanupHistory = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // HyperOS 风格确认弹窗（miuix WindowDialog）
    if (confirmClearAll) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.settings_cache_confirm_clear_all_title),
            summary = stringResource(R.string.settings_cache_confirm_clear_all_message),
            onDismissRequest = { confirmClearAll = false },
        ) {
            MiuixDialogButtons(
                confirmText = stringResource(R.string.action_confirm),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = {
                    confirmClearAll = false
                    vm.clearAllCaches()
                },
                onDismiss = { confirmClearAll = false },
            )
        }
    }
    if (confirmCleanupHistory) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.settings_cache_confirm_cleanup_title),
            summary = stringResource(
                R.string.settings_cache_confirm_cleanup_message,
                AppPreferences.DEFAULT_KEEP_DAYS,
            ),
            onDismissRequest = { confirmCleanupHistory = false },
        ) {
            MiuixDialogButtons(
                confirmText = stringResource(R.string.action_confirm),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = {
                    confirmCleanupHistory = false
                    vm.cleanupReadingHistory()
                },
                onDismiss = { confirmCleanupHistory = false },
            )
        }
    }
}

/** 带"清理"按钮的行（miuix 行 + 可点击文本，颜色与字号取自 MiuixTheme）。 */
@Composable
private fun MiuixClearRow(
    title: String,
    summary: String?,
    onClear: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    MiuixRow(
        title = title,
        summary = summary,
        icon = icon,
        trailing = {
            Text(
                text = stringResource(R.string.settings_cache_clear),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
    )
}

/** 弹窗内的两枚 miuix 按钮（确认 / 取消）。 */
@Composable
private fun MiuixDialogButtons(
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxSize().height(44.dp),
        ) { Text(confirmText) }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxSize().height(44.dp),
        ) { Text(dismissText) }
    }
}

/**
 * 磁盘缓存类别：`key` 必须与 `HtmlDiskCache` 的文件名前缀一致
 * （`{category}_{md5}.html`，见 `HtmlDiskCache.put`）。
 */
private enum class CacheCategory(val key: String, @StringRes val labelRes: Int) {
    HOME("home", R.string.settings_cache_home),
    BOOK("book", R.string.settings_cache_book),
    CHAPTER("chapter", R.string.settings_cache_chapter),
    TAG("tag", R.string.settings_cache_tag),
    OTHER("other", R.string.settings_cache_other),
    LEGACY("legacy", R.string.settings_cache_legacy),
}

/** 人类可读的文件大小（与 Material 版一致的实现，数字固定 Locale.US）。 */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        "%.1f GB".format(Locale.US, bytes / (1024f * 1024 * 1024))
    bytes >= 1024L * 1024 ->
        "%.1f MB".format(Locale.US, bytes / (1024f * 1024))
    bytes >= 1024L ->
        "%.0f KB".format(Locale.US, bytes / 1024f)
    else -> "$bytes B"
}
