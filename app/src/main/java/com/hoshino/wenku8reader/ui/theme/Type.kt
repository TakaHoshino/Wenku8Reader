@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.hoshino.wenku8reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局排版：以 Material 3 Expressive 的默认排版为基线，只做两件事：
 *
 * 1. **标题/标签启用 Expressive 的 emphasized 字重**（`titleLargeEmphasized` 等）——
 *    这是 M3 Expressive 规范里"强调文本更重"的核心表达，页面标题、分组标题、
 *    按钮与标签据此获得更明确的层级；
 * 2. **中文排版修正**：把字距收敛到 0（拉丁文排版用 0.5sp 字距尚可，中文会显得松散），
 *    并略增正文字号与行高，长文阅读更稳。
 *
 * 正文以外的字型（display/headline）保持 M3 默认，避免在大标题上叠加过多变化。
 */
private val BaseTypography = Typography()

val Wenku8Typography = BaseTypography.copy(
    // 页面标题 / 对话框标题：Expressive 强调字重
    titleLarge = BaseTypography.titleLargeEmphasized.copy(letterSpacing = 0.sp),
    titleMedium = BaseTypography.titleMediumEmphasized.copy(letterSpacing = 0.sp),
    titleSmall = BaseTypography.titleSmallEmphasized.copy(letterSpacing = 0.sp),
    // 正文：字号 16sp / 行高 25sp，字距 0（中文）
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(letterSpacing = 0.sp),
    bodySmall = BaseTypography.bodySmall.copy(letterSpacing = 0.sp),
    // 按钮 / 标签：Expressive 强调字重，保留一点点字距以便与小字号正文区分
    labelLarge = BaseTypography.labelLargeEmphasized.copy(letterSpacing = 0.2.sp),
    labelMedium = BaseTypography.labelMediumEmphasized.copy(letterSpacing = 0.3.sp),
    labelSmall = BaseTypography.labelSmall.copy(letterSpacing = 0.3.sp),
)
