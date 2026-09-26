package com.hoshino.wenku8reader.ui.bookcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.CoverImage
import com.hoshino.wenku8reader.ui.components.ExpressiveEmptyState
import com.hoshino.wenku8reader.ui.components.ExpressiveLoadingIndicator
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.ExpressiveTopAppBar
import com.hoshino.wenku8reader.ui.components.TonalCard
import com.hoshino.wenku8reader.ui.shelf.ShelfPickerDialog

/**
 * 书架页（主 Tab）。参考 SukiSU-Ultra：折叠大顶栏 + surfaceBright 卡片列表，
 * 排序 / 刷新收纳进顶栏操作区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookcasePage(
    onOpenBook: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenShelfManage: () -> Unit,
    vm: BookcaseViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    // 长按卡片 → 选择目标书架（多书架开启时才有入口）
    var movingEntry by remember { mutableStateOf<BookcaseEntry?>(null) }
    // 长按站方条目 → 从网站书架移出（与本地归属是两套独立状态）
    var removingSiteEntry by remember { mutableStateOf<BookcaseEntry?>(null) }

    // 静态顶栏（64dp）：去掉折叠顶栏的逐帧布局级联，滚动更顺滑
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.bookcase_title),
                actions = {
                    // 管理书架只属于多书架模式：关闭时顶栏与当前版本逐像素一致
                    if (ui.multiShelfEnabled) {
                        IconButton(onClick = onOpenShelfManage) {
                            Icon(
                                Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = stringResource(R.string.bookcase_manage_shelves),
                            )
                        }
                    }
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.action_stats))
                    }
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenDownloads) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.action_downloads))
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        when {
            ui.isLoading && ui.entries.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }

            ui.error != null && ui.entries.isEmpty() -> ExpressiveEmptyState(
                title = ui.error?.asString(LocalContext.current) ?: "",
                icon = Icons.Filled.Warning,
                shape = MaterialShapes.Boom,
                error = true,
                actionLabel = stringResource(R.string.action_retry),
                onAction = { vm.load() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            // 只有**关闭**多书架且整库为空时才用整屏空态（与上线前一致）。
            // 开启时不能这么做：整屏空态会把顶部的书架切换条一起盖掉，
            // 用户新建一个空书架后就再也切不回去，等于被困住。
            ui.entries.isEmpty() && !ui.multiShelfEnabled -> ExpressiveEmptyState(
                title = stringResource(R.string.bookcase_empty_local),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                shape = MaterialShapes.Cookie9Sided,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            else -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                if (ui.multiShelfEnabled) {
                    item(key = "shelf_switch") {
                        ShelfSwitcherRow(
                            shelves = ui.shelves,
                            selected = ui.selectedShelf,
                            onSelect = { vm.selectShelf(it) },
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                            ),
                        )
                    }
                }
                item(key = "sort_bar") {
                    // 排序：M3 Expressive SplitButton —— 主按钮选排序方式，尾随按钮切换正/倒序
                    BookcaseSortBar(
                        ui = ui,
                        onSelect = { vm.setSortType(it) },
                        onToggleReverse = { vm.setSortReversed(!ui.sortReversed) },
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
                if (ui.entries.isEmpty()) {
                    // 当前书架为空：空态作为**列表的一项**，切换条与排序条仍然可见可用。
                    // 固定高度是必要的——LazyColumn 的项在主轴上没有上界，fillMaxSize 无从生效。
                    item(key = "shelf_empty") {
                        ExpressiveEmptyState(
                            title = stringResource(
                                if (ui.siteShelf) R.string.wenku8_shelf_empty
                                else R.string.bookcase_shelf_empty,
                            ),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            shape = MaterialShapes.Cookie9Sided,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        )
                    }
                } else {
                    items(ui.entries, key = { it.bookId }) { entry ->
                        BookcaseCard(
                            entry = entry,
                            onOpenBook = onOpenBook,
                            onLongPress = {
                                // 站方书架：移出网站书架；本地书架：编辑所属书架
                                if (ui.siteShelf) removingSiteEntry = entry
                                else if (ui.multiShelfEnabled) movingEntry = entry
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    movingEntry?.let { entry ->
        ShelfPickerDialog(
            title = stringResource(R.string.shelf_picker_membership_title),
            // 只列**本地**书架：站方书架不是本地归属，列进去等于给一个勾了也不生效的复选框
            shelves = ui.localShelves,
            initial = entry.shelves,
            confirmLabel = stringResource(R.string.action_confirm),
            onDismiss = { movingEntry = null },
            onConfirm = { selected ->
                movingEntry = null
                // 勾选没变就不写库
                if (selected != entry.shelves) vm.setShelves(entry.bookId, selected)
            },
        )
    }

    removingSiteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { removingSiteEntry = null },
            title = { Text(stringResource(R.string.wenku8_shelf_remove)) },
            text = { Text(stringResource(R.string.wenku8_shelf_remove_message, entry.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removingSiteEntry = null
                        vm.removeFromSiteShelf(entry.bookId)
                    },
                ) { Text(stringResource(R.string.wenku8_shelf_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removingSiteEntry = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * 书架切换条（Material 版）：FilterChip 横向滚动。
 *
 * 用 FilterChip 而不是 SegmentedButton：书架数量不固定且可能很多，
 * 分段控件在条目变多后会挤成不可读的窄条。
 */
@Composable
private fun ShelfSwitcherRow(
    shelves: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shelves.forEach { name ->
            FilterChip(
                selected = name == selected,
                onClick = { onSelect(name) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun BookcaseSortBar(
    ui: BookcaseUiState,
    onSelect: (BookcaseSortType) -> Unit,
    onToggleReverse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(ui.sortType.labelRes), maxLines = 1)
                }
            },
            trailingButton = {
                // 倒序开关：tonal 尾随按钮本身就是 toggle 形态（checked 态由排序状态决定）
                SplitButtonDefaults.TonalTrailingButton(
                    checked = ui.sortReversed && ui.sortType != BookcaseSortType.DEFAULT,
                    enabled = ui.sortType != BookcaseSortType.DEFAULT,
                    onCheckedChange = { onToggleReverse() },
                ) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = stringResource(R.string.bookcase_sort_reverse),
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    )
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                stringResource(R.string.bookcase_sort),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp, bottom = 6.dp),
            )
            BookcaseSortType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = ui.sortType == type,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(type)
                    },
                )
            }
            val nonDefault = ui.sortType != BookcaseSortType.DEFAULT
            DropdownMenuItem(
                enabled = nonDefault,
                text = {
                    Text(stringResource(R.string.bookcase_sort_reverse), style = MaterialTheme.typography.bodyLarge)
                },
                leadingIcon = {
                    Checkbox(
                        checked = nonDefault && ui.sortReversed,
                        enabled = nonDefault,
                        onCheckedChange = null,
                    )
                },
                onClick = {
                    if (nonDefault) onToggleReverse()
                },
            )
        }
    }
}

@Composable
private fun BookcaseCard(
    entry: BookcaseEntry,
    onOpenBook: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = { onOpenBook(entry.bookId) },
        // 长按 → 移动到其他书架（TonalCard 原生支持，不会与原有点击冲突）
        onLongClick = onLongPress,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(116.dp)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            CoverImage(
                url = entry.coverUrl,
                width = 72.dp,
                height = 108.dp,
                contentDescription = entry.title,
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    entry.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (entry.author.isNotBlank()) {
                    Text(
                        entry.author,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (entry.progressTotal > 0) {
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(
                            R.string.bookcase_read_chapters,
                            entry.readCount,
                            entry.progressTotal,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
