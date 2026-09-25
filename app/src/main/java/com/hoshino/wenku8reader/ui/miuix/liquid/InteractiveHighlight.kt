// 移植自 SukiSU-Ultra 的 ui/component/miuix/animation/InteractiveHighlight.kt
// （其改编自 compose-miuix-ui 示例，Apache-2.0）。
//
// 与原实现的唯一差别：`android.graphics.RuntimeShader` 是 API 33+ 才有的类，
// 原实现无条件构造它（低版本会直接抛 NoClassDefFoundError）。这里加了口径判断——
// 不支持时整块高光自动降级为"无"（底栏其余效果不受影响）。

package com.hoshino.wenku8reader.ui.miuix.liquid

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 触摸点高光：手指按在玻璃上时，按压位置会出现一团柔和白光，
 * 并随着手指移动而移动（SukiSU 液态玻璃"被手指点亮"的观感）。
 */
internal class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {

    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createShader() else null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createShader(): RuntimeShader = RuntimeShader(
        """
        uniform float2 size;
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;

        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            return color * intensity;
        }""",
    )

    @Suppress("NewApi")
    val modifier: Modifier =
        Modifier.drawWithContent {
            val shader = shader
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                // 整块玻璃轻微提亮（Plus 叠加，不会变灰）
                drawRect(
                    Color.White.copy(0.06f * progress),
                    blendMode = BlendMode.Plus,
                )
                if (shader != null) {
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                        setFloatUniform("radius", size.minDimension * 1.2f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height),
                        )
                    }
                    drawRect(
                        ShaderBrush(shader),
                        blendMode = BlendMode.Plus,
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch {
                            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
                        }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch {
                            pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                        }
                        launch {
                            positionAnimation.animateTo(startPosition, positionAnimationSpec)
                        }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch {
                            pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                        }
                        launch {
                            positionAnimation.animateTo(startPosition, positionAnimationSpec)
                        }
                    }
                },
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
