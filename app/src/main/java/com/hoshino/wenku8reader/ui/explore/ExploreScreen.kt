package com.hoshino.wenku8reader.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveToggleGroup
import com.hoshino.wenku8reader.ui.components.ExpressiveTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold

/*
 * 探索模块页面入口与组合层。
 *
 * 原先本文件同时承载 Explore 页 + Search 页 + 封面预取 + 4 个列表项组件（约 646 行），
 * 按评估报告 2.5-3 的职责拆分建议拆为同包文件：
 * - `ExploreHomeBody.kt`：推荐页签正文 + 封面预取
 * - `ExploreTagsBody.kt`：标签页签正文 + 标签行
 * - `ExploreSearchBody.kt`：独立搜索页正文
 * - `ExploreItems.kt`：可复用列表项（封面卡片）
 * 本文件只保留对外入口 ExplorePage / SearchScreen 与两页共用的顶栏搜索条、页签切换。
 */

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
    onSearch: (String, Boolean) -> Unit,
    vm: ExploreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var keyword by rememberSaveable { mutableStateOf("") }
    var byAuthor by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ExploreMode.RECOMMEND) }

    LaunchedEffect(Unit) { vm.loadHomeOnce() }
    LaunchedEffect(mode) { if (mode == ExploreMode.TAGS) vm.loadTags() }

    // 静态顶栏（64dp）：去掉折叠顶栏的逐帧布局级联，滚动更顺滑
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = stringResource(R.string.action_downloads),
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
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
                onSearch = {
                    if (keyword.isNotBlank()) onSearch(keyword, byAuthor)
                },
            )
            ExploreTabRow(
                mode = mode,
                onModeChange = { mode = it },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (mode) {
                    ExploreMode.RECOMMEND -> HomeBody(
                        ui,
                        onOpenBook,
                        onRefresh = vm::refreshHome,
                    )
                    ExploreMode.TAGS -> TagsBody(
                        ui,
                        onOpenBook,
                        onOpenTag,
                        onLoadTagPreview = vm::loadTagPreview,
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
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
    // M3 Expressive 按钮组：按下时被按项变宽、相邻项压缩
    ExpressiveToggleGroup(
        labels = listOf(
            stringResource(R.string.explore_tab_recommend),
            stringResource(R.string.explore_tab_tags),
        ),
        selectedIndex = if (mode == ExploreMode.RECOMMEND) 0 else 1,
        onSelect = { index ->
            onModeChange(if (index == 0) ExploreMode.RECOMMEND else ExploreMode.TAGS)
        },
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
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

    ExpressiveScaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = { Text(stringResource(R.string.action_search)) },
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
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
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
                onSearch = {
                    if (keyword.isNotBlank()) vm.search(keyword, byAuthor)
                },
            )
            SearchBody(
                ui = ui,
                onOpenBook = onOpenBook,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
