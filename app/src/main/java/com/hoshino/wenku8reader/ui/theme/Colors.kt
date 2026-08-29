package com.hoshino.wenku8reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** 手动取色预设（与 CustomizationScreen 的 SEED_COLORS 保持一致）。 */
val seedColorOptions: List<Long> = listOf(
    0xFF3F5BA9L, 0xFF3949ABL, 0xFF6A1B9AL, 0xFFC2185BL,
    0xFFD32F2FL, 0xFFF57C00L, 0xFF388E3CL, 0xFF00897BL,
    0xFF5D4037L, 0xFF455A64L,
)

/** 预设的 ARGB 种子色（用于快捷选择）。 */
val seedColorArgbOptions: List<Int> = seedColorOptions.map { Color(it).toArgb() }
