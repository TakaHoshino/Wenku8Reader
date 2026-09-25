package com.hoshino.wenku8reader.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.hoshino.wenku8reader.ui.common.CoverImage
import com.hoshino.wenku8reader.ui.components.ExpressiveEmptyState
import com.hoshino.wenku8reader.ui.components.ExpressiveLoadingIndicator
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem

/**
 * 独立搜索页的正文（从 `ExploreScreen.kt` 拆出，见评估报告 2.5-3）。
 * 只负责结果/加载/错误三态渲染，页面入口仍在 `ExploreScreen.kt`。
 *
 * 结果行沿用共享组件 [SegmentedListItem]（外观与分组圆角都由它决定），
 * 但把点击交给外层普通 `clickable`：该组件内部的按压缩放反馈（pressClickable）
 * 定义在 `ui/components/Expressive.kt`，属于本次改动范围之外，而搜索结果
 * 一屏几十行，逐行挂动画状态的开销没有必要（见报告 2.3-23 / 3.3-1）。
 */
@Composable
internal fun SearchBody(
    ui: ExploreUiState,
    onOpenBook: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when {
        ui.searching -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { ExpressiveLoadingIndicator() }

        ui.searchError != null -> Text(
            ui.searchError.asString(context),
            modifier = modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )

        ui.results.isNotEmpty() -> LazyColumn(modifier.fillMaxSize()) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    items = ui.results.map { r ->
                        {
                            SegmentedListItem(
                                onClick = { onOpenBook(r.id) },
                                headlineContent = { Text(r.name) },
                                supportingContent = {
                                    Text(stringResource(R.string.search_result_id, r.id))
                                },
                                leadingContent = {
                                    CoverImage(
                                        url = r.coverUrl,
                                        width = 48.dp,
                                        height = 68.dp,
                                        contentDescription = r.name,
                                        cornerRadius = 8.dp,
                                    )
                                },
                            )
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        else -> ExpressiveEmptyState(
            title = stringResource(R.string.search_press_to_search),
            icon = Icons.Filled.Search,
            shape = MaterialShapes.PuffyDiamond,
            modifier = modifier.fillMaxSize(),
        )
    }
}
