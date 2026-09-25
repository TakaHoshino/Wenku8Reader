// 液态玻璃的折射与增艳效果。
// 改编自 Kyant0/AndroidLiquidGlass（Apache-2.0），经 compose-miuix-ui 官方示例转发；
// SukiSU-Ultra 的悬浮底栏用的是同一份实现，这里只做移植（包名/注释按本项目调整），
// 因此保留原出处说明以满足许可要求。

package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 玻璃边缘的镜面高光（双峰 Bloom），与 SukiSU 悬浮底栏使用同一组参数。
 *
 * 它和 `Highlight.GlassStroke*` 预设的区别在于有**两个光源**且 `dualPeak = true`：
 * 上下两条亮边强度不同，玻璃看起来有厚度，而不只是描了一圈边。
 */
val MiuixGlassSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

// 主光源的参考位置与"重力方向可忽略"的阈值（|g_xy| > 0.1，约 6° 倾斜），
// 与 miuix 内部 HighlightStyle 的 LIGHT_REF 保持一致。
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/**
 * 让 [base] 的主光源**跟随手机倾角**旋转（可再叠加 [extraDegrees] 的固定偏角）。
 *
 * 这是 SukiSU 液态玻璃"活起来"的关键：手机倾斜时玻璃边缘的高光会跟着移动，
 * 观感上真的像一块有厚度的玻璃，而不是一张静态渐变图。
 * 倾角来自 miuix 的 [rememberDeviceTilt]（基于重力传感器，无需权限）。
 */
@Composable
fun rememberTiltBrightHighlight(
    base: Highlight = MiuixGlassSpecular,
    extraDegrees: Float = 0f,
): Highlight {
    val baseStyle = base.style as? BloomStroke ?: return base
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val magnitudeSq = gx * gx + gy * gy
        val (lx0, ly0) = if (magnitudeSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val invMagnitude = 1f / sqrt(magnitudeSq)
            (gx * invMagnitude) to (gy * invMagnitude)
        } else {
            // 放平时用"正上方"作为默认光向，避免水平时高光乱跳
            0f to -1f
        }
        val radians = extraDegrees * PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        val lx = c * lx0 - s * ly0
        val ly = s * lx0 + c * ly0
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + lx,
                y = LIGHT_REF_Y + ly,
                z = basePrimary.position.z,
            ),
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

/**
 * 增艳：提高模糊后内容的饱和度。
 *
 * 没有这一步，模糊结果会向中间灰收敛，观感是"蒙了一层雾"；提饱和之后透出来的内容仍有颜色，
 * 才接近液态玻璃那种"内容被压在玻璃下面"的感觉。
 */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}

/**
 * 折射（"液态"的核心）：靠近边缘的地方把背景按圆角形状向内挤压，模拟厚玻璃边缘的透镜。
 *
 * - 只在形状是 [CornerBasedShape] 时生效（胶囊请用 `RoundedCornerShape(高/2)`，
 *   而不是 `CircleShape`——`CircleShape` 不是 CornerBasedShape，会直接跳过折射）；
 * - Android 13 以下没有 `RuntimeShader`，[isRuntimeShaderSupported] 为 false 时自动不做折射，
 *   模糊与高光仍然保留；
 * - [refractionAmount] 会写入 scope 的 `padding`，让采样区域向外扩张，
 *   否则边缘折射会采到被裁掉的像素（表现为边缘拉丝）。
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val radii = roundedRectCornerRadii() ?: return

    val dispersionEnabled = chromaticAberration > 0f
    val shaderString = if (dispersionEnabled) {
        ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
    } else {
        ROUNDED_RECT_REFRACTION_SHADER
    }
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    val scaledSizeW = size.width / sf
    val scaledSizeH = size.height / sf
    val scaledPadding = padding / sf
    val scaledRefractionHeight = refractionHeight / sf
    val scaledRefractionAmount = refractionAmount / sf
    val scaledRadii = FloatArray(radii.size) { radii[it] / sf }

    runtimeShaderEffect(
        key = key,
        shaderString = shaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", scaledSizeW, scaledSizeH)
        setFloatUniform("offset", -scaledPadding, -scaledPadding)
        setFloatUniform("cornerRadii", scaledRadii)
        setFloatUniform("refractionHeight", scaledRefractionHeight)
        setFloatUniform("refractionAmount", -scaledRefractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) {
            setFloatUniform("chromaticAberration", chromaticAberration)
        }
    }
}

/** 取圆角矩形四个角的半径（px）；形状不是圆角矩形时返回 null（折射直接跳过）。 */
private fun BackdropEffectScope.roundedRectCornerRadii(): FloatArray? {
    val cornerShape = shape as? CornerBasedShape ?: return null
    val sizePx = size
    val maxRadius = sizePx.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft =
        if (isLtr) cornerShape.topStart.toPx(sizePx, this) else cornerShape.topEnd.toPx(sizePx, this)
    val topRight =
        if (isLtr) cornerShape.topEnd.toPx(sizePx, this) else cornerShape.topStart.toPx(sizePx, this)
    val bottomRight = if (isLtr) {
        cornerShape.bottomEnd.toPx(sizePx, this)
    } else {
        cornerShape.bottomStart.toPx(sizePx, this)
    }
    val bottomLeft = if (isLtr) {
        cornerShape.bottomStart.toPx(sizePx, this)
    } else {
        cornerShape.bottomEnd.toPx(sizePx, this)
    }
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius),
    )
}

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
