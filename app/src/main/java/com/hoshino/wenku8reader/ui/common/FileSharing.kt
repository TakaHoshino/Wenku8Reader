package com.hoshino.wenku8reader.ui.common

import android.content.Context
import android.net.Uri
import java.io.File

/** 将应用私有文件写入系统文件选择器返回的 Document URI。 */
internal fun saveFileToUri(context: Context, file: File, uri: Uri): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        } ?: return false
        true
    }.getOrDefault(false)
