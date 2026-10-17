package com.hoshino.wenku8reader.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 用系统分享面板把应用生成的文件发出去（目前用于日志 zip）。
 *
 * 为什么走 [FileProvider]：Android 7+ 直接传 `file://` 会抛 `FileUriExposedException`；
 * 这里只对这一次 Intent 授予读权限（`FLAG_GRANT_READ_URI_PERMISSION`），不会把目录暴露给别的应用。
 *
 * 用户可以选择发到聊天软件 / 邮箱 / 网盘——"导出"因此不需要申请存储权限，
 * 也不会在 Downloads 里留下第二份（旧文件由 `AppLogger` 在下次导出时清掉）。
 */
internal fun shareFile(
    context: Context,
    file: File,
    mimeType: String,
    subject: String,
    chooserTitle: String,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, chooserTitle)
    // Compose 的 LocalContext 通常是 Activity；若从非 Activity 上下文调用，需要 NEW_TASK
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
