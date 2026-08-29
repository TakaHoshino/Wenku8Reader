package com.hoshino.wenku8reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 参考 SukiSU-Ultra 的 Typography：正文加大行高、轻微字距，配合折叠大顶栏的
 * “Expressive” 风格；其余字型回落到 MD3 默认值。
 */
val Wenku8Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
