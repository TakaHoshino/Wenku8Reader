package com.hoshino.wenku8reader.ui.theme

/**
 * 手动取色预设（单一来源）。
 *
 * 设置页的取色圆点与自定义页的色块行都消费此清单——此前 CustomizationScreen 另有一份
 * 逐字重复的 SEED_COLORS，两处随时可能漂移，故收敛到此处。
 */
val seedColorOptions: List<Long> = listOf(
    0xFF3F5BA9L, 0xFF3949ABL, 0xFF6A1B9AL, 0xFFC2185BL,
    0xFFD32F2FL, 0xFFF57C00L, 0xFF388E3CL, 0xFF00897BL,
    0xFF5D4037L, 0xFF455A64L,
)
