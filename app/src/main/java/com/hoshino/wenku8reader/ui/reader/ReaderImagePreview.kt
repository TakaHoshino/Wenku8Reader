package com.hoshino.wenku8reader.ui.reader

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.data.FileSaver
import com.hoshino.wenku8reader.data.Wenku8Hosts
import com.hoshino.wenku8reader.di.AppContainer
import com.hoshino.wenku8reader.ui.theme.isMiuixStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 预览的最大放大倍数：再大就是像素块了，也给复位留出明确手感。 */
private const val PREVIEW_MAX_SCALE = 5f

/** 双击放大到的倍数。 */
private const val PREVIEW_DOUBLE_TAP_SCALE = 2.5f

/**
 * 插图全屏预览。
 *
 * 交互（对应用户要求的三条）：
 * 1. **双指缩放 / 拖动平移**：`detectTransformGestures` 处理缩放与平移，缩放钳在
 *    1x..[PREVIEW_MAX_SCALE]，平移按"图片放大后超出的部分"钳制，不会把图片拖出屏幕；
 * 2. **长按图片 → 询问是否保存**：长按弹确认框，确认后在 IO 线程拉取原图字节并交给
 *    [FileSaver]（与下载 TXT/EPUB 同一套落盘逻辑，含同名覆盖处理）；
 * 3. **点击空白处返回**：只有点在图片**实际显示区域之外**（ContentScale.Fit 留下的黑边，
 *    已计入当前的缩放与平移）才关闭，点在图片上不会误退。
 *
 * 另外提供双击复位/放大，这是图片预览的通用习惯，不额外增加操作成本。
 */
@Composable
fun ReaderImagePreview(
    url: String,
    displayName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(Wenku8Hosts.normalizeImageUrl(url))
            .setHeader("Referer", Wenku8Hosts.IMAGE_REFERER)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // 图片在容器里"未缩放"时占的矩形（ContentScale.Fit）。intrinsicSize 未知（还没解码完）
    // 时退化为铺满容器：此时点在图片上不会被当成"点空白"，符合直觉。
    val fitSize: Size = remember(painter.intrinsicSize, containerSize) {
        val iw = painter.intrinsicSize.width
        val ih = painter.intrinsicSize.height
        val cw = containerSize.width.toFloat()
        val ch = containerSize.height.toFloat()
        if (iw <= 0f || ih <= 0f || cw <= 0f || ch <= 0f) {
            Size(cw, ch)
        } else {
            val fit = min(cw / iw, ch / ih)
            Size(iw * fit, ih * fit)
        }
    }

    /** 把平移钳制在"图片超出容器的那部分"之内；未放大时归零。 */
    fun clampOffset(candidate: Offset): Offset {
        val maxX = max(0f, (fitSize.width * scale - containerSize.width) / 2f)
        val maxY = max(0f, (fitSize.height * scale - containerSize.height) / 2f)
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
    }

    /** 触点是否落在当前（含缩放/平移）的图片显示区域内。 */
    fun isInsideImage(position: Offset): Boolean {
        if (containerSize.width == 0 || containerSize.height == 0) return true
        val center = Offset(
            containerSize.width / 2f + offset.x,
            containerSize.height / 2f + offset.y,
        )
        val halfWidth = fitSize.width * scale / 2f
        val halfHeight = fitSize.height * scale / 2f
        return abs(position.x - center.x) <= halfWidth &&
            abs(position.y - center.y) <= halfHeight
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .onSizeChanged { containerSize = it }
            // 点击/长按/双击：点击只负责"点空白返回"，长按询问保存
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { position -> if (!isInsideImage(position)) onDismiss() },
                    onLongPress = { showSaveDialog = true },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = PREVIEW_DOUBLE_TAP_SCALE
                        }
                    },
                )
            }
            // 缩放/平移：放在点击检测之后，短按不会触发它，双指手势也不会被当成点击
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, PREVIEW_MAX_SCALE)
                    scale = next
                    offset = if (next <= 1f) Offset.Zero else clampOffset(offset + pan)
                }
            },
    ) {
        // 复用上面的 painter：同一张图不再走第二次请求（Coil 会命中内存缓存，但没必要多发一次）
        Image(
            painter = painter,
            contentDescription = stringResource(R.string.reader_illustration),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }

    if (showSaveDialog) {
        SaveImageDialog(
            saving = saving,
            onDismiss = { if (!saving) showSaveDialog = false },
            onConfirm = {
                saving = true
                scope.launch {
                    val bytes = runCatching { context.appContainer.client.imageBytes(url) }.getOrNull()
                    val saved = bytes?.let {
                        FileSaver.saveDownload(
                            context = context,
                            displayName = "$displayName.${extensionOf(url)}",
                            mime = mimeOf(url),
                            bytes = it,
                        )
                    }
                    val message = if (saved != null) {
                        context.getString(R.string.reader_image_saved, saved)
                    } else {
                        context.getString(R.string.reader_image_save_failed)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    saving = false
                    showSaveDialog = false
                }
            },
        )
    }
}

/**
 * 保存确认框：按界面风格二选一（MIUIX 用 miuix `WindowDialog`，Material 用 M3 `AlertDialog`），
 * 与阅读器其他面板保持同一套分派口径。
 */
@Composable
private fun SaveImageDialog(
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.reader_image_save_title)
    val message = stringResource(R.string.reader_image_save_message)
    val confirmText = stringResource(R.string.reader_image_save)
    val cancelText = stringResource(R.string.action_cancel)

    if (isMiuixStyle()) {
        WindowDialog(
            show = true,
            title = title,
            summary = message,
            onDismissRequest = onDismiss,
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                Button(
                    onClick = onConfirm,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { MiuixText(confirmText) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { MiuixText(cancelText) }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !saving) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !saving) { Text(cancelText) }
            },
        )
    }
}

/** 从图片 URL 取扩展名（认不出就按 jpg 处理——站点插图绝大多数是 jpg）。 */
private fun extensionOf(url: String): String {
    val name = url.substringBefore('?').substringAfterLast('/')
    val ext = name.substringAfterLast('.', "").lowercase()
    return if (ext in setOf("jpg", "jpeg", "png", "webp", "gif")) ext else "jpg"
}

/** 与 [extensionOf] 对应的 MIME。 */
private fun mimeOf(url: String): String = when (extensionOf(url)) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}

private val Context.appContainer: AppContainer
    get() = (applicationContext as Wenku8Application).container
