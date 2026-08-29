package com.hoshino.wenku8reader.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * 全局主题。参考 SukiSU-Ultra 的 Material 侧设计语言：
 * - 动态取色（Android 12+）或手动种子色；
 * - 完整补齐 surfaceContainer* 系列角色（折叠大顶栏 / 底栏 / 卡片同色系）；
 * - AMOLED 纯黑模式（深色下 surface 系列压到真黑）。
 */
@Composable
fun Wenku8ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color(0xFF3F5BA9),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Wenku8Typography,
        content = content,
    )
}

/** 把 surface/background 系列压到纯黑，用于 OLED 省电。 */
private fun ColorScheme.amoledCopy(): ColorScheme = copy(
    background = Color.Black,
    onBackground = onSurface,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1F1F1F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF2A2A2A),
)

/** 从单个种子色构建完整 MD3 色板（浅色 / 深色 / 深色纯黑）。 */
private fun manualScheme(seed: Color, dark: Boolean, amoled: Boolean): ColorScheme {
    val onPrimary = if (seed.luminance() > 0.55f) Color(0xFF1B1B1B) else Color.White

    return if (dark) {
        val surface = if (amoled) Color(0xFF000000) else Color(0xFF141218)
        val containerLowest = if (amoled) Color(0xFF000000) else Color(0xFF0F0D13)
        val containerLow = if (amoled) Color(0xFF111111) else Color(0xFF1D1B20)
        val container = if (amoled) Color(0xFF1A1A1A) else Color(0xFF211F26)
        val containerHigh = if (amoled) Color(0xFF242424) else Color(0xFF2B2930)
        val containerHighest = if (amoled) Color(0xFF2E2E2E) else Color(0xFF36343B)

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
