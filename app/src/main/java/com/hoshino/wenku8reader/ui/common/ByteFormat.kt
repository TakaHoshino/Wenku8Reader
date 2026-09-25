package com.hoshino.wenku8reader.ui.common

import java.util.Locale

/**
 * 人类可读的文件大小（B / KB / MB / GB）。
 *
 * Material 版与 MIUIX 版的存储页各写了一份完全相同的实现（含同样的 `Locale.US` 处理），
 * 属"与 UI 风格无关的纯格式化"，收敛到一处；同时补了单测覆盖进位边界。
 * 数字固定用 [Locale.US]：避免在某些区域设置下出现逗号小数点，与系统设置的读数观感一致。
 */
internal fun formatByteSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        "%.1f GB".format(Locale.US, bytes / (1024f * 1024 * 1024))
    bytes >= 1024L * 1024 ->
        "%.1f MB".format(Locale.US, bytes / (1024f * 1024))
    bytes >= 1024L ->
        "%.0f KB".format(Locale.US, bytes / 1024f)
    else -> "$bytes B"
}
