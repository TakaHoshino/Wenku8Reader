package com.hoshino.wenku8reader.ui.common

import androidx.compose.ui.text.font.FontFamily

/** Maps the persisted font-family key to a Compose [FontFamily]. */
fun fontFamilyFor(key: String): FontFamily = when (key) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "sans" -> FontFamily.SansSerif
    else -> FontFamily.Default
}
