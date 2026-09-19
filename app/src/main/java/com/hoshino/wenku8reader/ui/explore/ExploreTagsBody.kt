package com.hoshino.wenku8reader.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.ui.components.ExpressiveEmptyState
import com.hoshino.wenku8reader.ui.components.ExpressiveLoadingIndicator

/**
 * 探索页「标签」页签正文。
 *
 * 加载策略（2026-09 调整）：标签清单来自内置常量（秒回），**每个标签的书籍预览在
 * 该行进入可见区域时才请求**（[LaunchedEffect] + LazyColumn 的按需组合）。
 * 原实现一进标签页就把 50 个标签全部抓一遍：请求量是用户实际浏览量的十几倍，
 * 且全部要过站点约 600ms/次的全局节流，首屏常常等十几秒。
 */
@Composable
internal fun TagsBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onLoadTagPreview: (String) -> Unit,
    onRetryTags: () -> Unit,
) {
    when {
        ui.tagsLoading && ui.tags.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExpressiveLoadingIndicator()
        }

        ui.tagsError != null -> ExpressiveEmptyState(
            title = ui.tagsError?.asString(LocalContext.current) ?: "",
            shape = MaterialShapes.Boom,
            error = true,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetryTags,
            modifier = Modifier.fillMaxSize(),
        )

        ui.tags.isEmpty() -> ExpressiveEmptyState(
            title = stringResource(R.string.explore_tags_empty),
            shape = MaterialShapes.SoftBurst,
            modifier = Modifier.fillMaxSize(),
        )

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(ui.tags, key = { "tag_$it" }) { tag ->
                TagRow(
                    tag = tag,
                    books = ui.tagBooks[tag].orEmpty(),
                    loading = tag in ui.loadingTags,
                    generation = ui.tagsGeneration,
                    onLoadTagPreview = onLoadTagPreview,
                    onOpenBook = onOpenBook,
                    onOpenTag = onOpenTag,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TagRow(
    tag: String,
    books: List<HomeBook>,
    loading: Boolean,
    generation: Int,
    onLoadTagPreview: (String) -> Unit,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    // 行进入组合 = 滚动到可见区域：这时才发起该标签的预览请求
    LaunchedEffect(tag, generation) { onLoadTagPreview(tag) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                // 同屏最多几行可见：用普通 clickable（波纹由全局 LocalIndication 提供）
                .clickable { onOpenTag(tag) }
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tag,
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
        when {
            books.isNotEmpty() -> LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(books, key = { it.id }) { b ->
                    HomeCoverCard(b, onOpenBook)
                }
            }

            loading -> CoverPlaceholderRow()

            // 已加载但没有书：只保留可点击的分类标题（可进入"查看全部"）
            else -> Unit
        }
    }
}

/** 预览加载中的占位封面：尺寸与 [HomeCoverCard] 一致，避免加载完成后布局跳动。 */
@Composable
private fun CoverPlaceholderRow() {
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
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}
