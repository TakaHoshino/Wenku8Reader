package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.bookcase.BookcaseEntry
import com.hoshino.wenku8reader.ui.bookcase.BookcaseSortType
import com.hoshino.wenku8reader.ui.bookcase.BookcaseViewModel
import com.hoshino.wenku8reader.ui.common.CoverImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 书架页（主 Tab）——与 Material 版（`ui/bookcase/BookcaseScreen.kt`）完全独立。
 *
 * 形态照 HyperOS 的列表页：大标题 + 顶栏图标按钮，列表是"每本书一张卡片"，
 * 卡片内用 miuix 的排版与进度条；排序用 miuix 下拉/开关行（放在列表顶部）。
 * 逻辑仍复用 [BookcaseViewModel]（加载、排序、进度），不共用任何 Material 组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuixBookcasePage(
    onOpenBook: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenShelfManage: () -> Unit,
    vm: BookcaseViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    var movingEntry by remember { mutableStateOf<BookcaseEntry?>(null) }

    MiuixPage(
        title = stringResource(R.string.bookcase_title),
        actions = {
            // 管理书架只属于多书架模式：关闭时顶栏与本功能上线前一致
            if (ui.multiShelfEnabled) {
                MiuixIconButton(
                    icon = Icons.Filled.Collections,
                    contentDescription = stringResource(R.string.bookcase_manage_shelves),
                    onClick = onOpenShelfManage,
                )
            }
            MiuixIconButton(
                icon = Icons.Filled.CalendarMonth,
                contentDescription = stringResource(R.string.action_stats),
                onClick = onOpenStats,
            )
            MiuixIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.action_refresh),
                onClick = { vm.load() },
            )
            MiuixIconButton(
                icon = Icons.Filled.Download,
                contentDescription = stringResource(R.string.action_downloads),
                onClick = onOpenDownloads,
            )
        },
    ) { inner ->
        when {
            ui.isLoading && ui.entries.isEmpty() -> MiuixLoading(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            ui.error != null && ui.entries.isEmpty() -> MiuixEmptyState(
                title = ui.error?.asString(androidx.compose.ui.platform.LocalContext.current) ?: "",
                error = true,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                actionText = stringResource(R.string.action_retry),
                onAction = { vm.load() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            ui.entries.isEmpty() -> MiuixEmptyState(
                // 开启多书架时"当前书架为空"与"本地书架为空"要分开说
                title = stringResource(
                    if (ui.multiShelfEnabled) R.string.bookcase_shelf_empty
                    else R.string.bookcase_empty_local,
                ),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp + LocalFloatingBarInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.multiShelfEnabled) {
                    item(key = "shelf_switch") {
                        MiuixShelfSwitcherRow(
                            shelves = ui.shelves,
                            selected = ui.selectedShelf,
                            onSelect = { vm.selectShelf(it) },
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 8.dp,
                            ),
                        )
                    }
                }
                item(key = "sort") {
                    MiuixSection(title = stringResource(R.string.bookcase_sort)) {
                        MiuixDropdownRow(
                            title = stringResource(R.string.bookcase_sort),
                            icon = Icons.AutoMirrored.Filled.Sort,
                            items = BookcaseSortType.entries.map { stringResource(it.labelRes) },
                            selectedIndex = BookcaseSortType.entries
                                .indexOf(ui.sortType).coerceAtLeast(0),
                            onSelected = { index ->
                                vm.setSortType(BookcaseSortType.entries[index])
                            },
                        )
                        MiuixRowDivider()
                        MiuixSwitchRow(
                            title = stringResource(R.string.bookcase_sort_reverse),
                            icon = Icons.Filled.SwapVert,
                            checked = ui.sortReversed,
                            enabled = ui.sortType != BookcaseSortType.DEFAULT,
                            onCheckedChange = { vm.setSortReversed(it) },
                        )
                    }
                }
                items(ui.entries, key = { it.bookId }) { entry ->
                    MiuixBookCard(
                        entry = entry,
                        onOpenBook = onOpenBook,
                        onLongPress = { if (ui.multiShelfEnabled) movingEntry = entry },
                    )
                }
            }
        }
    }

    movingEntry?.let { entry ->
        MiuixShelfPicker(
            title = stringResource(R.string.shelf_picker_move_title),
            shelves = ui.shelves,
            current = entry.shelf,
            onDismiss = { movingEntry = null },
            onPick = { shelf ->
                movingEntry = null
                vm.moveToShelf(entry.bookId, shelf)
            },
        )
    }
}

/**
 * 书架切换条（MIUIX 版）。
 *
 * miuiX 没有 chip/FilterChip：沿用 `MiuixExplorePage` 里"主题色胶囊文字表达选中态"的既有做法，
 * 横向可滚动以容纳任意数量的书架（`TabRow` 条目一多就会被挤成不可读的窄条）。
 */
@Composable
private fun MiuixShelfSwitcherRow(
    shelves: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shelves.forEach { name ->
            val active = name == selected
            Text(
                text = name,
                style = MiuixTheme.textStyles.footnote1,
                color = if (active) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent,
                    )
                    .clickable { onSelect(name) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 单本书卡片：封面 + 书名/作者 + 已读进度。
 * 用 miuix 的 `Card` + `LinearProgressIndicator`，文字走 `MiuixTheme.textStyles`。
 */
@Composable
private fun MiuixBookCard(
    entry: BookcaseEntry,
    onOpenBook: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        // miuix 的 Card 没有 onLongClick：点击与长按都放在外层 modifier 上。
        // 这里**不能**再给 Card 传 onClick——两者会各自响应，长按还会先触发一次打开详情。
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenBook(entry.bookId) },
                onLongClick = onLongPress,
            ),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                url = entry.coverUrl,
                width = 64.dp,
                height = 92.dp,
                contentDescription = entry.title,
                cornerRadius = 8.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.author,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.progressTotal > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = entry.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.bookcase_read_chapters,
                            entry.readCount,
                            entry.progressTotal,
                        ),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}
