package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.author.AuthorBooksViewModel
import com.hoshino.wenku8reader.ui.explore.TagBooksViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 作者作品页与标签书单页的 **MIUIX 版**。
 *
 * 两页形态相同（封面行列表 + 分页加载更多），因此放在一起；与 Material 版
 * （`ui/author/AuthorBooksScreen.kt`、`ui/explore/TagBooksScreen.kt`）完全独立：
 * 小标题顶栏、miuix 行、miuix 滚动条、miuix 加载态，逻辑仍复用各自的 ViewModel。
 * 行组件复用基础件 [MiuixCoverRow]。
 */

/** 作者作品页：`ui/author/AuthorBooksScreen.kt` 的 MIUIX 版本。 */
@Composable
fun MiuixAuthorBooksPage(
    authorName: String,
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    vm: AuthorBooksViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    MiuixSubPage(
        title = stringResource(R.string.author_books_title, authorName),
        onBack = onBack,
    ) { inner ->
        when {
            ui.loading -> MiuixLoading(Modifier.fillMaxSize().padding(inner))

            ui.books.isEmpty() -> MiuixEmptyState(
                title = ui.error?.asString(LocalContext.current)
                    ?: stringResource(R.string.author_books_empty),
                error = ui.error != null,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                actionText = if (ui.error != null) stringResource(R.string.action_retry) else null,
                onAction = if (ui.error != null) ({ vm.load() }) else null,
                modifier = Modifier.fillMaxSize().padding(inner),
            )

            else -> MiuixBookList(
                header = stringResource(R.string.author_books_count, ui.books.size),
                modifier = Modifier.fillMaxSize().padding(inner),
            ) {
                items(ui.books, key = { it.id }) { book ->
                    MiuixCoverRow(
                        coverUrl = book.coverUrl,
                        title = book.name,
                        onClick = { onOpenBook(book.id) },
                    )
                    MiuixRowDivider(startIndent = 76.dp)
                }
            }
        }
    }
}

/** 标签书单页：`ui/explore/TagBooksScreen.kt` 的 MIUIX 版本（含分页「加载更多」）。 */
@Composable
fun MiuixTagBooksPage(
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    vm: TagBooksViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    MiuixSubPage(title = vm.tag, onBack = onBack) { inner ->
        when {
            ui.loading -> MiuixLoading(Modifier.fillMaxSize().padding(inner))

            ui.error != null && ui.books.isEmpty() -> MiuixEmptyState(
                title = ui.error?.asString(LocalContext.current) ?: "",
                error = true,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                actionText = stringResource(R.string.action_retry),
                onAction = { vm.load() },
                modifier = Modifier.fillMaxSize().padding(inner),
            )

            ui.books.isEmpty() -> MiuixEmptyState(
                title = stringResource(R.string.home_empty),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                modifier = Modifier.fillMaxSize().padding(inner),
            )

            else -> MiuixBookList(modifier = Modifier.fillMaxSize().padding(inner)) {
                items(ui.books, key = { it.id }) { book ->
                    MiuixCoverRow(
                        coverUrl = book.coverUrl,
                        title = book.name,
                        onClick = { onOpenBook(book.id) },
                    )
                    MiuixRowDivider(startIndent = 76.dp)
                }
                if (ui.hasMore) {
                    item(key = "load_more") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ui.loadingMore) {
                                CircularProgressIndicator(progress = null)
                            } else {
                                Text(
                                    text = stringResource(
                                        R.string.tag_books_load_more,
                                        ui.books.size,
                                    ),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !ui.loading) { vm.loadMore() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 两页共用的列表骨架：可选的「共 N 本」标题 + 内容 + miuix 滚动条。
 * 长列表挂滚动条是 HyperOS 的习惯（拖动时会显示位置指示）。
 */
@Composable
private fun MiuixBookList(
    modifier: Modifier = Modifier,
    header: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    // 每页进入时回到顶部：从详情返回其它书单不会停在上一本的位置
    LaunchedEffect(Unit) { listState.scrollToItem(0) }

    Box(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = 24.dp + LocalFloatingBarInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (header != null) {
                item(key = "header") {
                    Text(
                        text = header,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
            }
            content()
            item(key = "tail") { Spacer(Modifier.height(16.dp)) }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
