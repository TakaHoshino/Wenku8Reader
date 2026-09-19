package com.hoshino.wenku8reader.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 全局主题（Material 3 Expressive）：
 * - 动态取色（Android 12+）或手动种子色；
 * - 完整补齐 surfaceContainer* 系列角色（折叠大顶栏 / 底栏 / 卡片同色系）；
 * - AMOLED 纯黑模式（深色下 surface 系列压到真黑）；
 * - 形状刻度取 Expressive 的 large=20dp（卡片 / 列表组），动效由其 motion scheme 驱动。
 * - [uiStyle] = MIUIX 时切到 MIUIX（HyperOS）风格：`MiuixTheme` 提供配色与文字样式，
 *   其内部再嵌一层由 MIUIX 色板映射出的 Material 主题，保证尚未迁移的 M3 组件
 *  （Text/Slider/Dialog 等）颜色依然正确。
 *
 * 为什么用 [MaterialExpressiveTheme] 而不是 `MaterialTheme`：前者会把
 * motion scheme、形状与排版一并设为 Expressive 默认值（后者需要逐个显式传入）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Wenku8ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color(0xFF3F5BA9),
    amoled: Boolean = false,
    expressiveMotion: Boolean = true,
    uiStyle: UiStyle = UiStyle.MATERIAL3,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUiStyle provides uiStyle) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixRootTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                seedColor = seedColor,
                content = content,
            )
            return@CompositionLocalProvider
        }
        val m3Content = @Composable {
            val baseScheme = when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                else -> manualScheme(seedColor, darkTheme, amoled)
            }
            // AMOLED 纯黑对动态色板同样生效：只压黑 surface 系列，保留动态取色的主色。
            val colorScheme = if (amoled && darkTheme) baseScheme.amoledCopy() else baseScheme
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = if (expressiveMotion) MotionScheme.expressive() else MotionScheme.standard(),
                shapes = Wenku8Shapes,
                typography = Wenku8Typography,
                content = content,
            )
        }
        m3Content()
    }
}

/**
 * MIUIX 根主题。
 *
 * 配色来源：`ThemeController`。
 * - 开启动态取色（Android 12+）→ `Monet*` 模式，取系统壁纸色；
 * - 否则用设置里的种子色（MIUIX 由单个 key color 推导整套色板）。
 *
 * 内层再套一个由 MIUIX 色板映射的 Material 主题：本项目还有大量 Material 组件
 * （Slider、DropdownMenu、AlertDialog、阅读器的 ModalBottomSheet…），
 * 若不提供 M3 的 LocalContentColor，它们会退回默认黑色而在深色下不可读。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiuixRootTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    seedColor: Color,
    content: @Composable () -> Unit,
) {
    val monetSupported = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val mode = when {
        monetSupported && darkTheme -> ColorSchemeMode.MonetDark
        monetSupported -> ColorSchemeMode.MonetLight
        darkTheme -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.Light
    }
    val controller = remember(mode, seedColor) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = seedColor,
            isDark = darkTheme,
        )
    }
    MiuixTheme(controller = controller) {
        MaterialTheme(
            colorScheme = MiuixTheme.colorScheme.toMaterialColorScheme(darkTheme),
            typography = Wenku8Typography,
            shapes = Wenku8Shapes,
            content = content,
        )
    }
}

/**
 * MIUIX 色板 → Material 色板（只映射已存在的角色，其余保留 M3 默认值）。
 * 目的不是"看起来像 M3"，而是让残留的 M3 组件与 MIUIX 表面共用同一套颜色。
 */
private fun top.yukonga.miuix.kmp.theme.Colors.toMaterialColorScheme(
    dark: Boolean,
): ColorScheme = if (dark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = secondaryVariant,
        onTertiary = onSecondaryVariant,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceBright = surfaceContainerHighest,
        surfaceDim = surfaceContainer,
        outline = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = secondaryVariant,
        onTertiary = onSecondaryVariant,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceBright = surfaceContainerHighest,
        surfaceDim = surfaceContainer,
        outline = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
}

/**
 * 形状刻度：仅把 large 抬到 Expressive 的 20dp（卡片 / 分组列表容器），
 * 其余档位保持 M3 默认，避免菜单、对话框、FAB 等组件的圆角被一并改动。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val Wenku8Shapes = Shapes(
    large = ShapeDefaults.LargeIncreased,
)

/**
 * 把 surface/background 系列压到真黑，用于 OLED 省电。
 * 背景/主容器压到纯黑或近黑（<0x10），卡片仅保留极轻微抬升以便区分。
 */
private fun ColorScheme.amoledCopy(): ColorScheme = copy(
    background = Color.Black,
    onBackground = onSurface,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF1F1F1F),
)

/** 从单个种子色构建完整 MD3 色板（浅色 / 深色 / 深色纯黑）。 */
private fun manualScheme(seed: Color, dark: Boolean, amoled: Boolean): ColorScheme {
    val onPrimary = if (seed.luminance() > 0.55f) Color(0xFF1B1B1B) else Color.White

    return if (dark) {
        val surface = if (amoled) Color(0xFF000000) else Color(0xFF141218)
        val containerLowest = if (amoled) Color(0xFF000000) else Color(0xFF0F0D13)
        val containerLow = if (amoled) Color(0xFF080808) else Color(0xFF1D1B20)
        val container = if (amoled) Color(0xFF0D0D0D) else Color(0xFF211F26)
        val containerHigh = if (amoled) Color(0xFF171717) else Color(0xFF2B2930)
        val containerHighest = if (amoled) Color(0xFF1F1F1F) else Color(0xFF36343B)

        darkColorScheme(
            primary = seed.blend(Color.White, 0.22f),
            onPrimary = Color(0xFF1B1B1B),
            primaryContainer = seed.blend(Color.Black, 0.55f),
            onPrimaryContainer = seed.blend(Color.White, 0.72f),
            secondary = seed.shiftHue(30f, 0.6f).blend(Color.White, 0.12f),
            onSecondary = Color(0xFF1B1B1B),
            secondaryContainer = seed.shiftHue(30f, 0.6f).blend(Color.Black, 0.6f),
            onSecondaryContainer = seed.shiftHue(30f, 0.6f).blend(Color.White, 0.68f),
            tertiary = seed.shiftHue(-30f, 0.55f).blend(Color.White, 0.1f),
            onTertiary = Color(0xFF1B1B1B),
            tertiaryContainer = seed.shiftHue(-30f, 0.55f).blend(Color.Black, 0.62f),
            onTertiaryContainer = seed.shiftHue(-30f, 0.55f).blend(Color.White, 0.66f),
            background = surface,
            onBackground = Color(0xFFE6E1E5),
            surface = surface,
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = containerLow,
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceTint = seed.blend(Color.White, 0.22f),
            surfaceDim = if (amoled) Color(0xFF000000) else Color(0xFF141218),
            surfaceBright = containerHighest,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = onPrimary,
            primaryContainer = seed.blend(Color.White, 0.86f),
            onPrimaryContainer = seed.blend(Color.Black, 0.12f),
            secondary = seed.shiftHue(30f, 0.6f),
            onSecondary = Color.White,
            secondaryContainer = seed.shiftHue(30f, 0.6f).blend(Color.White, 0.84f),
            onSecondaryContainer = seed.shiftHue(30f, 0.6f).blend(Color.Black, 0.1f),
            tertiary = seed.shiftHue(-30f, 0.55f),
            onTertiary = Color.White,
            tertiaryContainer = seed.shiftHue(-30f, 0.55f).blend(Color.White, 0.82f),
            onTertiaryContainer = seed.shiftHue(-30f, 0.55f).blend(Color.Black, 0.1f),
            background = Color(0xFFFDF8F8),
            onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFDF8F8),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFF1EDF3),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceTint = seed,
            surfaceDim = Color(0xFFDED8E1),
            surfaceBright = Color(0xFFFFF8FE),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF8F2F7),
            surfaceContainer = Color(0xFFF2ECF2),
            surfaceContainerHigh = Color(0xFFECE6EC),
            surfaceContainerHighest = Color(0xFFE6E0E9),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        )
    }
}

private fun Color.luminance(): Float =
    red * 0.299f + green * 0.587f + blue * 0.114f

private fun Color.blend(target: Color, ratio: Float): Color = Color(
    red = red + (target.red - red) * ratio,
    green = green + (target.green - green) * ratio,
    blue = blue + (target.blue - blue) * ratio,
    alpha = alpha,
)

private fun Color.shiftHue(degrees: Float, saturationScale: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees).mod(360f)
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}
