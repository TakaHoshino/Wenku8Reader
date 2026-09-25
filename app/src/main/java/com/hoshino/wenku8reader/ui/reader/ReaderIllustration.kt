package com.hoshino.wenku8reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.Wenku8Hosts

/**
 * 阅读器插图（正文里的图片统一走这个组件）。
 *
 * 组件化之前，插图是在正文里就地写了一段 `SubcomposeAsyncImage`：加载态、占位底色、
 * 防盗链请求头、长按行为都揉在页面代码里，分页模式与滚动模式各写一份、容易漂移。
 * 现在两条渲染路径共用这一个组件，交互（点击/长按）由调用方注入。
 *
 * - 请求头 `Referer` 取自 [Wenku8Hosts.IMAGE_REFERER] 单一来源，地址统一升级为 HTTPS；
 * - 占位底色用 `surfaceContainerHighest`：加载中/失败时尺寸稳定，不会把正文顶来顶去；
 * - 点击/长按检测只在调用方确实提供了回调时才挂载，避免无谓的指针处理开销。
 */
@Composable
fun ReaderIllustration(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(Wenku8Hosts.normalizeImageUrl(url))
            .setHeader("Referer", Wenku8Hosts.IMAGE_REFERER)
            .crossfade(true)
            .build()
    }
    val interaction = if (onClick != null || onLongPress != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = onLongPress,
        )
    } else {
        Modifier
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = stringResource(R.string.reader_illustration),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            // LNR 风格占位底色：加载中/失败时保持稳定视觉
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(interaction),
        contentScale = contentScale,
        loading = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        },
    )
}
