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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.TonalCard

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
    vm: BookcaseViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    // 静态顶栏（64dp）：去掉折叠顶栏的逐帧布局级联，滚动更顺滑
    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookcase_title)) },
                actions = {
                    SortMenu(ui, onSelect = { vm.setSortType(it) }, onToggleReverse = { vm.setSortReversed(!ui.sortReversed) })
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        when {
            ui.isLoading && ui.entries.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            ui.error != null && ui.entries.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            ui.error?.asString(LocalContext.current) ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.load() }) { Text(stringResource(R.string.action_retry)) }
                    }
                }

            ui.entries.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.bookcase_empty_local),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                items(ui.entries, key = { it.bookId }) { entry ->
                    BookcaseCard(
                        entry = entry,
                        onOpenBook = onOpenBook,
                        modifier = Modifier.animateItem(),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SortMenu(
    ui: BookcaseUiState,
    onSelect: (BookcaseSortType) -> Unit,
    onToggleReverse: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.bookcase_sort))
        }
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
    modifier: Modifier = Modifier,
) {
    TonalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = { onOpenBook(entry.bookId) },
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
                            R.string.bookcase_progress,
                            entry.progressPos + 1,
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
