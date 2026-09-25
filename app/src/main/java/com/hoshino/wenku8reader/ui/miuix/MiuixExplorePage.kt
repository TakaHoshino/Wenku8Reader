package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.CoverImage
import com.hoshino.wenku8reader.ui.common.coverImageRequest
import com.hoshino.wenku8reader.ui.explore.ExploreMode
import com.hoshino.wenku8reader.ui.explore.ExploreUiState
import com.hoshino.wenku8reader.ui.explore.ExploreViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 探索页（主 Tab）与搜索页——与 Material 版（`ui/explore` 目录下的实现）**完全独立**。
 *
 * 形态照 HyperOS：大标题顶栏 + 圆角搜索框 + 分段控件切换「推荐 / 标签」，
 * 正文用 miuix `Card` 与 `MiuixTheme` 排版。逻辑（[ExploreViewModel] 的加载、
 * 标签按需预览、封面预取）与 Material 版一致，但 UI 代码一行不共用。
 *
 * 封面仍走 `ui/common/CoverImage`：它是防盗链 + 尺寸 + 占位色的统一入口（属 IO/数据层），
 * 不是可替换的界面组件；占位色取自当前主题，MIUIX 下即 miuix 色板。
 */
@Composable
fun MiuixExplorePage(
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    vm: ExploreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var keyword by rememberSaveable { mutableStateOf("") }
    var byAuthor by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ExploreMode.RECOMMEND) }

    LaunchedEffect(Unit) { vm.loadHomeOnce() }
    LaunchedEffect(mode) { if (mode == ExploreMode.TAGS) vm.loadTags() }

    MiuixPage(
        title = stringResource(R.string.app_name),
        actions = {
            MiuixIconButton(
                icon = Icons.Filled.Download,
                contentDescription = stringResource(R.string.action_downloads),
                onClick = onOpenDownloads,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            MiuixSearchRow(
                keyword = keyword,
                byAuthor = byAuthor,
                onKeywordChange = { keyword = it },
                onByAuthorChange = { byAuthor = it },
                onSearch = { if (keyword.isNotBlank()) onSearch(keyword, byAuthor) },
            )
            TabRow(
                tabs = listOf(
                    stringResource(R.string.explore_tab_recommend),
                    stringResource(R.string.explore_tab_tags),
                ),
                selectedTabIndex = if (mode == ExploreMode.RECOMMEND) 0 else 1,
                onTabSelected = { index ->
                    mode = if (index == 0) ExploreMode.RECOMMEND else ExploreMode.TAGS
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (mode) {
                    ExploreMode.RECOMMEND -> MiuixHomeBody(
                        ui = ui,
                        onOpenBook = onOpenBook,
                        onRefresh = vm::refreshHome,
                    )

                    ExploreMode.TAGS -> MiuixTagsBody(
                        ui = ui,
                        onOpenBook = onOpenBook,
                        onOpenTag = onOpenTag,
                        onLoadTagPreview = { tag -> vm.loadTagPreview(tag) },
                        onRetryTagPreview = { tag -> vm.loadTagPreview(tag, force = true) },
                        onRetryTags = { vm.loadTags(force = true) },
                    )
                }
            }
        }
    }
}

/**
 * MIUIX 独立搜索页（从探索页搜索条进入）。
 * `initialKeyword` 非空时进入即搜，与 Material 版行为一致。
 */
@Composable
fun MiuixSearchPage(
    initialKeyword: String,
    initialByAuthor: Boolean,
    onBack: () -> Unit,
    onOpenBook: (Int) -> Unit,
    vm: ExploreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var keyword by rememberSaveable(initialKeyword) { mutableStateOf(initialKeyword) }
    var byAuthor by rememberSaveable(initialByAuthor) { mutableStateOf(initialByAuthor) }

    LaunchedEffect(initialKeyword, initialByAuthor) {
        if (initialKeyword.isNotBlank()) vm.search(initialKeyword, initialByAuthor)
    }

    MiuixSubPage(title = stringResource(R.string.action_search), onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            MiuixSearchRow(
                keyword = keyword,
                byAuthor = byAuthor,
                onKeywordChange = { keyword = it },
                onByAuthorChange = { byAuthor = it },
                onSearch = { if (keyword.isNotBlank()) vm.search(keyword, byAuthor) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MiuixSearchBody(ui = ui, onOpenBook = onOpenBook)
            }
        }
    }
}

/** 圆角搜索框（miuix `TextField`）+ 作者筛选 + 搜索按钮。 */
@Composable
private fun MiuixSearchRow(
    keyword: String,
    byAuthor: Boolean,
    onKeywordChange: (String) -> Unit,
    onByAuthorChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.width(8.dp))
        // 作者筛选：miuix 没有 chip，用主题色的胶囊文字表达选中态
        Text(
            text = stringResource(R.string.search_by_author),
            style = MiuixTheme.textStyles.footnote1,
            color = if (byAuthor) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceSecondary
            },
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (byAuthor) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                )
                .clickable { onByAuthorChange(!byAuthor) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        MiuixIconButton(
            icon = Icons.Filled.Search,
            contentDescription = stringResource(R.string.action_search),
            onClick = onSearch,
        )
    }
}

// ------------------------------------------------------------------ //
// 推荐页签
// ------------------------------------------------------------------ //

@Composable
private fun MiuixHomeBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 与 Material 版相同的封面预取：滚动时提前解码下面两个区块的封面，
    // 避免「区块组合 + 图片解码」挤在同一帧。
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember { Coil.imageLoader(context) }
    LaunchedEffect(ui.sections) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                val sectionIdx = index - 1
                if (sectionIdx in 0 until ui.sections.lastIndex) {
                    ui.sections.drop(sectionIdx + 1).take(2).forEach { section ->
                        section.books.forEach { book ->
                            book.coverUrl?.let { url ->
                                runCatching {
                                    imageLoader.enqueue(
                                        coverImageRequest(
                                            context = context,
                                            url = url,
                                            widthPx = with(density) { 104.dp.roundToPx() },
                                            heightPx = with(density) { 146.dp.roundToPx() },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    when {
        ui.homeLoading && ui.sections.isEmpty() -> MiuixLoading(Modifier.fillMaxSize())

        ui.homeError != null && ui.sections.isEmpty() -> MiuixEmptyState(
            title = stringResource(
                R.string.home_error,
                ui.homeError?.asString(LocalContext.current) ?: "",
            ),
            error = true,
            icon = Icons.Filled.Refresh,
            actionText = stringResource(R.string.action_retry),
            onAction = onRefresh,
            modifier = Modifier.fillMaxSize(),
        )

        ui.sections.isEmpty() -> MiuixEmptyState(
            title = stringResource(R.string.home_empty),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            modifier = Modifier.fillMaxSize(),
        )

        else -> Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                item(key = "subtitle") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        MiuixIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            onClick = onRefresh,
                        )
                    }
                }
                items(ui.sections, key = { it.title }, contentType = { "section" }) { section ->
                    MiuixHomeSectionBlock(section = section, onOpenBook = onOpenBook)
                }
                item(key = "tail") { Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current)) }
            }
            // 长列表右侧的 miuix 滚动条（与目录页、书单页一致）
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun MiuixHomeSectionBlock(section: HomeSection, onOpenBook: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
        )
        if (section.books.any { it.coverUrl != null }) {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(section.books.take(4), key = { it.id }) { book ->
                    MiuixCoverCard(book, onOpenBook)
                }
            }
        } else {
            // 纯文字榜单：miuix 卡片 + 序号列表
            MiuixRankCard(section = section, onOpenBook = onOpenBook)
        }
    }
}

/** 文字榜单卡片：序号 + 书名，整行可点。 */
@Composable
private fun MiuixRankCard(section: HomeSection, onOpenBook: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(vertical = 4.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            section.books.take(10).forEachIndexed { index, book ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBook(book.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = book.name,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ //
// 标签页签
// ------------------------------------------------------------------ //

@Composable
private fun MiuixTagsBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onLoadTagPreview: (String) -> Unit,
    onRetryTagPreview: (String) -> Unit,
    onRetryTags: () -> Unit,
) {
    when {
        ui.tagsLoading && ui.tags.isEmpty() -> MiuixLoading(Modifier.fillMaxSize())

        ui.tagsError != null -> MiuixEmptyState(
            title = ui.tagsError?.asString(LocalContext.current) ?: "",
            error = true,
            icon = Icons.Filled.Refresh,
            actionText = stringResource(R.string.action_retry),
            onAction = onRetryTags,
            modifier = Modifier.fillMaxSize(),
        )

        ui.tags.isEmpty() -> MiuixEmptyState(
            title = stringResource(R.string.explore_tags_empty),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            modifier = Modifier.fillMaxSize(),
        )

        else -> {
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(ui.tags, key = { "tag_$it" }) { tag ->
                        MiuixTagRow(
                            tag = tag,
                            books = ui.tagBooks[tag].orEmpty(),
                            loading = tag in ui.loadingTags,
                            failed = tag in ui.tagPreviewErrors,
                            generation = ui.tagsGeneration,
                            onLoadTagPreview = onLoadTagPreview,
                            onRetryPreview = { onRetryTagPreview(tag) },
                            onOpenBook = onOpenBook,
                            onOpenTag = onOpenTag,
                        )
                    }
                    item(key = "tail") {
                        Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
                    }
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun MiuixTagRow(
    tag: String,
    books: List<com.hoshino.wenku8reader.data.HomeBook>,
    loading: Boolean,
    failed: Boolean,
    generation: Int,
    onLoadTagPreview: (String) -> Unit,
    onRetryPreview: () -> Unit,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    // 行进入组合 = 滚动到可见区域：这时才发起该标签的预览请求（与 Material 版同策略）
    LaunchedEffect(tag, generation) { onLoadTagPreview(tag) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpenTag(tag) }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.explore_tag_all),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
        }
        when {
            books.isNotEmpty() -> LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    MiuixCoverCard(book, onOpenBook)
                }
            }

            loading -> MiuixCoverPlaceholderRow()

            // 加载失败：给一条明确的可点重试（以前与"没有书"长得一样，只能干等）
            failed -> Text(
                text = stringResource(R.string.explore_tag_preview_failed),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRetryPreview)
                    .padding(start = 16.dp, top = 2.dp, bottom = 10.dp),
            )

            // 已加载但没有书：只保留可点击的分类标题（可进入"查看全部"）
            else -> Unit
        }
    }
}

/** 预览加载中的占位：尺寸与 [MiuixCoverCard] 一致，避免加载完成后布局跳动。 */
@Composable
private fun MiuixCoverPlaceholderRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        repeat(4) {
            Box(
                Modifier
                    .padding(4.dp)
                    .width(104.dp)
                    .height(146.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

// ------------------------------------------------------------------ //
// 搜索结果页正文
// ------------------------------------------------------------------ //

@Composable
private fun MiuixSearchBody(ui: ExploreUiState, onOpenBook: (Int) -> Unit) {
    when {
        ui.searching -> MiuixLoading(Modifier.fillMaxSize())

        ui.searchError != null -> Text(
            text = ui.searchError?.asString(LocalContext.current) ?: "",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )

        ui.results.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
            items(ui.results, key = { it.id }) { result ->
                MiuixCoverRow(
                    coverUrl = result.coverUrl,
                    title = result.name,
                    summary = stringResource(R.string.search_result_id, result.id),
                    onClick = { onOpenBook(result.id) },
                )
                MiuixRowDivider(startIndent = 76.dp)
            }
            item(key = "tail") { Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current)) }
        }

        else -> MiuixEmptyState(
            title = stringResource(R.string.search_press_to_search),
            icon = Icons.Filled.Search,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ------------------------------------------------------------------ //
// 共用小件
// ------------------------------------------------------------------ //

/** 封面卡片：104×146 封面 + 两行书名（HyperOS 书城的卡片形态）。 */
@Composable
private fun MiuixCoverCard(
    book: com.hoshino.wenku8reader.data.HomeBook,
    onOpenBook: (Int) -> Unit,
) {
    Column(
        Modifier
            .width(112.dp)
            .padding(4.dp)
            .clickable { onOpenBook(book.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            url = book.coverUrl,
            width = 104.dp,
            height = 146.dp,
            contentDescription = book.name,
            cornerRadius = 12.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
