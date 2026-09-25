package com.hoshino.wenku8reader.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阅读器背景图的落盘逻辑。
 *
 * 这一段是**纯 IO/位图处理**，与 UI 风格无关，因此由 Material 版阅读设置页
 * （`ui/settings/CustomizationScreen.kt`）与 MIUIX 版（`ui/miuix/MiuixCustomizationPage.kt`）
 * 共用；两套界面只会共享数据层与这类工具，不共享任何组件。
 */

/** 背景图目标长边（px）：读取时整份解码，故落盘前先按此降采样，避免数十 MB 的原图常驻私有目录。 */
private const val BACKGROUND_MAX_EDGE = 2000

/** 压缩质量：背景图非精细素材，90 在体积与观感之间取得平衡。 */
private const val BACKGROUND_QUALITY = 90

/**
 * 把用户选择的图片复制到应用私有目录并返回路径（失败返回 null）。
 *
 * - 整体在 [Dispatchers.IO] 执行：原图复制/解码对几 MB～几十 MB 的文件足以阻塞主线程；
 * - 复制前先降采样（长边约 [BACKGROUND_MAX_EDGE]）再重新压缩，避免把整份原图存进私有目录；
 * - 解码失败（非位图/流损坏）时退回原样复制，保持与旧实现一致的可用性；
 * - 返回路径仍是 `filesDir/reader_background`，与 `ReaderSettings.backgroundImage` 的消费方式兼容。
 */
internal suspend fun copyReaderBackgroundToInternal(
    context: Context,
    uri: Uri,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dest = File(context.filesDir, "reader_background").apply { parentFile?.mkdirs() }
        // 先落临时文件：BitmapFactory 需要「先读尺寸、再解码」两次读取，而输入流不可重复读。
        val temp = File.createTempFile("reader_background_", ".tmp", context.cacheDir)
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null) return@runCatching null
            // 解码/压缩任一环节失败（非位图、格式不支持、写盘异常）都退回原样复制，
            // 保证背景图仍能设置成功，而不是留下一个半截的目标文件。
            val compressed = runCatching {
                compressDownsampled(temp, dest, keepAlpha = sourceKeepsAlpha(context, uri))
            }.getOrDefault(false)
            if (!compressed) {
                temp.copyTo(dest, overwrite = true)
            }
            dest.absolutePath
        } finally {
            temp.delete()
        }
    }.getOrNull()
}

/** 源图是否带透明通道（PNG/WebP/GIF）：JPEG 会把透明区域压成黑色，故这些格式仍按 PNG 保存。 */
private fun sourceKeepsAlpha(context: Context, uri: Uri): Boolean {
    val mime = context.contentResolver.getType(uri)?.lowercase() ?: return false
    return mime == "image/png" || mime == "image/webp" || mime == "image/gif"
}

/**
 * 按长边约 [BACKGROUND_MAX_EDGE] 降采样解码 [src] 并压缩写入 [dest]。
 * 返回 false 表示 [src] 不是可解码的位图（由调用方退回原样复制）。
 */
private fun compressDownsampled(src: File, dest: File, keepAlpha: Boolean): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(src.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    // inSampleSize 只能取 2 的幂：取「不会把长边压到目标以下」的最大档位，尽量贴近 2000px
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= BACKGROUND_MAX_EDGE) {
        sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
        src.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return false
    return try {
        dest.outputStream().use { output ->
            val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            bitmap.compress(format, BACKGROUND_QUALITY, output)
        }
    } finally {
        bitmap.recycle()
    }
}
