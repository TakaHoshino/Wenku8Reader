package com.hoshino.wenku8reader.ui.common

import android.content.Context
import androidx.annotation.StringRes

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
