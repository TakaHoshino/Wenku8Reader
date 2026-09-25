package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.CoverImage
import com.hoshino.wenku8reader.ui.explore.ExploreMode
import com.hoshino.wenku8reader.ui.explore.ExploreViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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

