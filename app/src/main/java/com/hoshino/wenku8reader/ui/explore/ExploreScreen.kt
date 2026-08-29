package com.hoshino.wenku8reader.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.rememberCoverRequest
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.TonalCard
import com.hoshino.wenku8reader.ui.components.expressiveLargeTopAppBarColors
import com.hoshino.wenku8reader.ui.components.pressClickable

/**
 * 探索页（主 Tab）。参考 SukiSU-Ultra 首页：折叠大顶栏 + 圆角搜索条 +
 * surfaceBright 卡片列表；推荐 / 标签用 SegmentedButton 切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorePage(
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    vm: ExploreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var keyword by rememberSaveable { mutableStateOf("") }
    var byAuthor by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ExploreMode.RECOMMEND) }

    LaunchedEffect(Unit) { vm.loadHomeOnce() }
    LaunchedEffect(mode) { if (mode == ExploreMode.TAGS) vm.loadTags() }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = stringResource(R.string.action_downloads),
                        )
                    }
                },
                colors = expressiveLargeTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            SearchRow(
                keyword = keyword,
                byAuthor = byAuthor,
                onKeywordChange = { keyword = it },
                onByAuthorChange = { byAuthor = it },
                onSearch = { vm.search(keyword, byAuthor) },
            )
            ExploreTabRow(
                mode = mode,
                onModeChange = { mode = it },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                when (mode) {
                    ExploreMode.RECOMMEND -> {
                        if (keyword.isNotBlank()) {
                            SearchBody(ui, onOpenBook)
                        } else {
                            HomeBody(ui, onOpenBook, onRefresh = vm::refreshHome)
                        }
                    }
                    ExploreMode.TAGS -> TagsBody(
                        ui,
                        onOpenBook,
                        onOpenTag,
                        onRetryTags = { vm.loadTags(force = true) },
                    )
                }
            }
        }
    }
}

/** 圆角搜索条：搜索图标 + 输入框 + 作者筛选 Chip + 搜索按钮。 */
@Composable
private fun SearchRow(
    keyword: String,
    byAuthor: Boolean,
    onKeywordChange: (String) -> Unit,
    onByAuthorChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
                BasicTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    decorationBox = { inner ->
                        if (keyword.isEmpty()) {
                            Text(
                                stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                FilterChip(
                    selected = byAuthor,
                    onClick = { onByAuthorChange(!byAuthor) },
                    label = { Text(stringResource(R.string.search_by_author)) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
        }
    }
}

@Composable
private fun ExploreTabRow(
    mode: ExploreMode,
    onModeChange: (ExploreMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = mode == ExploreMode.RECOMMEND,
            onClick = { onModeChange(ExploreMode.RECOMMEND) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.explore_tab_recommend)) }
        SegmentedButton(
            selected = mode == ExploreMode.TAGS,
            onClick = { onModeChange(ExploreMode.TAGS) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.explore_tab_tags)) }
    }
}

@Composable
private fun TagsBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onRetryTags: () -> Unit,
) {
    when {
        ui.tagsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        ui.tagsError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ui.tagsError?.asString(LocalContext.current) ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetryTags) { Text(stringResource(R.string.action_retry)) }
            }
        }

        ui.tagSections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.explore_tags_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(ui.tagSections, key = { "tag_${it.tag}" }) { section ->
                TagRow(section, onOpenBook, onOpenTag)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SearchBody(ui: ExploreUiState, onOpenBook: (Int) -> Unit) {
    val context = LocalContext.current
    when {
        ui.searching -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        ui.searchError != null -> Text(
            ui.searchError?.asString(context) ?: "",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )

        ui.results.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    items = ui.results.map { r ->
                        {
                            SegmentedListItem(
                                headlineContent = { Text(r.name) },
                                supportingContent = {
                                    Text(stringResource(R.string.search_result_id, r.id))
                                },
                                onClick = { onOpenBook(r.id) },
                            )
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        else -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.search_press_to_search),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        ui.homeLoading && ui.sections.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        ui.homeError != null && ui.sections.isEmpty() ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(
                        R.string.home_error,
                        ui.homeError?.asString(LocalContext.current) ?: "",
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
            }

        ui.sections.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.home_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_subtitle),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            }
            items(ui.sections, key = { it.title }, contentType = { "section" }) { section ->
                HomeSectionBlock(section, onOpenBook)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TagRow(
    section: TagSection,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressClickable { onOpenTag(section.tag) }
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.tag,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.explore_tag_all),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(section.books, key = { it.id }) { b ->
                HomeCoverCard(b, onOpenBook)
            }
        }
    }
}

@Composable
private fun HomeSectionBlock(section: HomeSection, onOpenBook: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            section.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        if (section.books.any { it.coverUrl != null }) {
            // 每行最多 6 本：首页图片总量减半，显著降低滚动时的解码批量回调
            // 与 Tab 切换时整页绘制的节点数（gfxinfo 实测 UI 线程瓶颈）
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(section.books.take(6), key = { it.id }) { b ->
                    HomeCoverCard(b, onOpenBook)
                }
            }
        } else {
            // 纯文字榜单：surfaceBright 卡片
            TonalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    section.books.take(10).forEachIndexed { i, b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressClickable { onOpenBook(b.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                b.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCoverCard(b: HomeBook, onOpenBook: (Int) -> Unit) {
    Column(
        Modifier
            .width(104.dp)
            .padding(4.dp)
            .pressClickable { onOpenBook(b.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = rememberCoverRequest(b.coverUrl, 104.dp, 146.dp),
            contentDescription = b.name,
            modifier = Modifier
                .size(104.dp, 146.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            b.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
