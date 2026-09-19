package com.hoshino.wenku8reader.ui.explore

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.components.ExpressiveEmptyState
import com.hoshino.wenku8reader.ui.components.ExpressiveLoadingIndicator

/**
 * 探索页「标签」页签正文（从 `ExploreScreen.kt` 拆出，见评估报告 2.5-3）。
 * 每个标签一行（标题 + 一排封面），最多 50 行，属高密度重复项。
 */
@Composable
internal fun TagsBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onRetryTags: () -> Unit,
) {
    when {
        ui.tagsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

        ui.tagSections.isEmpty() -> ExpressiveEmptyState(
            title = stringResource(R.string.explore_tags_empty),
            shape = MaterialShapes.SoftBurst,
            modifier = Modifier.fillMaxSize(),
        )

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(ui.tagSections, key = { "tag_${it.tag}" }) { section ->
                TagRow(section, onOpenBook, onOpenTag)
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
                // 同屏最多 50 个分类标题行：用普通 clickable（波纹由全局 LocalIndication 提供），
                // 不再为每一行常驻一份 pressed 状态 + 缩放动画
                .clickable { onOpenTag(section.tag) }
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
