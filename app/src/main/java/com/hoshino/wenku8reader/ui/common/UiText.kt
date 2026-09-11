package com.hoshino.wenku8reader.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.hoshino.wenku8reader.R

/**
 * A UI message that is either a ready-to-display string or a string resource
 * plus format args. Lets ViewModels surface errors without holding a Context.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    class StringResource(
        @StringRes val resId: Int,
        vararg val args: Any,
    ) : UiText

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(resId, *args)
    }
}

/**
 * 把异常转成用户可见文案。
 *
 * 为什么需要它：此前各 ViewModel 普遍写 `UiText.DynamicString(e.message ?: "")`，
 * 而 `Throwable.message` 经常为 null（如 NPE、部分 IO 异常、取消异常），
 * 用户会看到一个**空白**的错误提示，不知道发生了什么。
 * 这里统一兜底到资源字符串，并把原始信息作为可读详情附上。
 */
fun Throwable.toUiText(): UiText =
    message?.takeIf { it.isNotBlank() }
        ?.let { UiText.StringResource(R.string.error_with_detail, it) }
        ?: UiText.StringResource(R.string.error_unknown)

/** 可空异常版本：为 null 时回退到通用错误文案（避免空白提示）。 */
fun Throwable?.toUiTextOrUnknown(): UiText = this?.toUiText() ?: UiText.StringResource(R.string.error_unknown)
