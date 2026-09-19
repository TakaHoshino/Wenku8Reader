package com.hoshino.wenku8reader.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.CoverImage
import com.hoshino.wenku8reader.ui.components.ExpressiveEmptyState
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveLoadingIndicator
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior

/**
 * 标签书单页（子页）：Expressive Flexible 大顶栏（返回）+ 分组卡片列表 + 分页加载全部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBooksScreen(
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    vm: TagBooksViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scrollBehavior = rememberExpressiveScrollBehavior()

    ExpressiveScaffold(
        topBar = {
            ExpressiveLargeTopAppBar(
                title = { Text(vm.tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        when {
            ui.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) { ExpressiveLoadingIndicator() }

            ui.error != null && ui.books.isEmpty() -> ExpressiveEmptyState(
                title = ui.error?.asString(LocalContext.current) ?: "",
                icon = Icons.Filled.MenuBook,
                shape = MaterialShapes.SoftBurst,
                error = true,
                actionLabel = stringResource(R.string.action_retry),
                onAction = { vm.load() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            ui.books.isEmpty() -> ExpressiveEmptyState(
                title = stringResource(R.string.home_empty),
                icon = Icons.Filled.MenuBook,
                shape = MaterialShapes.SoftBurst,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            else -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                item {
                    SegmentedColumn(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        items = ui.books.map { book ->
                            {
                                SegmentedListItem(
                                    headlineContent = {
                                        Text(book.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingContent = {
                                        CoverImage(
                                            url = book.coverUrl,
                                            width = 48.dp,
                                            height = 68.dp,
                                            contentDescription = book.name,
                                            cornerRadius = 8.dp,
                                        )
                                    },
                                    onClick = { onOpenBook(book.id) },
                                )
                            }
                        },
                    )
                }
                if (ui.hasMore) {
                    item {
                        // 分页加载更多（"查看全部"展示该标签下所有书）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ui.loadingMore) {
                                ExpressiveLoadingIndicator(size = 32.dp)
                            } else {
                                Text(
                                    stringResource(R.string.tag_books_load_more, ui.books.size),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !ui.loading) { vm.loadMore() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
