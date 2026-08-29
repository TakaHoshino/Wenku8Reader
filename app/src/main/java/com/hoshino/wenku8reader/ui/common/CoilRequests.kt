package com.hoshino.wenku8reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 带防盗链 Referer 的封面图片请求。
 * 通过 Coil 的 size 提示按目标尺寸解码（不放大、超尺寸降采样），
 * 避免列表滚动时每张封面都以原图全尺寸解码造成的卡顿与内存占用。
 *
 * 列表场景默认 [crossfade] = false：滚动时批量出现的图片若逐个播放淡入动画，
 * 会与滚动帧竞争主线程导致掉帧；仅在详情等单图场景按需开启。
 */
@Composable
fun rememberCoverRequest(
    url: String?,
    width: Dp,
    height: Dp,
    crossfade: Boolean = false,
): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = with(density) { width.roundToPx() }
    val h = with(density) { height.roundToPx() }
    return remember(url, w, h, crossfade) {
        ImageRequest.Builder(context)
            .data(url)
            .size(w, h)
            .setHeader("Referer", "https://www.wenku8.net/")
            .apply { if (crossfade) crossfade(true) }
            .build()
    }
}

/**
 * 带 LNR 风格占位背景的封面图：加载中/失败时显示 `surfaceContainerHighest` 底色，
 * 视觉稳定、无弹出感。使用普通 [AsyncImage]（不用 SubcomposeAsyncImage），
 * 避免子组合开销影响列表滚动性能。
 */
@Composable
fun CoverImage(
    url: String?,
    width: Dp,
    height: Dp,
    contentDescription: String?,
    cornerRadius: Dp = 10.dp,
    crossfade: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = rememberCoverRequest(url, width, height, crossfade),
        contentDescription = contentDescription,
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}
