package com.hoshino.wenku8reader.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.ui.common.CoverImage

/**
 * 探索页可复用的列表项组件（从 `ExploreScreen.kt` 拆出，见评估报告 2.5-3）。
 *
 * 这些组件在同屏内会重复出现几十次（首页每区块 4 张、标签页每分类一排），
 * 因此统一使用低开销的普通 [clickable]（见报告 2.3-23 / 3.3-1）：
 * 波纹由全局 `LocalIndication` 提供，不额外挂 pressed 状态与按压缩放动画。
 */
@Composable
internal fun HomeCoverCard(b: HomeBook, onOpenBook: (Int) -> Unit) {
    Column(
        Modifier
            .width(104.dp)
            .padding(4.dp)
            // 高频组合项：用普通 clickable，避免 pressClickable 的动画状态开销
            .clickable { onOpenBook(b.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            url = b.coverUrl,
            width = 104.dp,
            height = 146.dp,
            contentDescription = b.name,
            // 封面圆角对齐 M3 Expressive 形状刻度的 medium（12dp）
            cornerRadius = 12.dp,
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
