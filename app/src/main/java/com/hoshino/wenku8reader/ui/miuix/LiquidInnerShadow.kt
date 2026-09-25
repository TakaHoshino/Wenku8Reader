// 内阴影（"玻璃被压下去"的深度感）。
// 改编自 Kyant0/AndroidLiquidGlass（Apache-2.0），经 compose-miuix-ui 官方示例转发；
// SukiSU-Ultra 的液态玻璃滑块用的是同一份实现，这里按本项目包名移植并保留出处说明。

package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** 内阴影参数：半径越大，边缘往里压得越深。 */
@Immutable
data class InnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    companion object {
        @Stable
        val Default: InnerShadow = InnerShadow()
    }
}

/**
 * 在 [shape] 内缘绘制一层阴影：[shadow] 返回 null 时不绘制（例如按压进度为 0）。
 * 用 lambda 而不是直接传值，是为了让"按压力度"这类逐帧变化的状态不会导致重组。
 */
fun Modifier.innerShadow(
    shape: Shape,
    shadow: () -> InnerShadow?,
): Modifier = this then InnerShadowElement(shape, shadow)

private class InnerShadowElement(
    val shape: Shape,
    val shadow: () -> InnerShadow?,
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode = InnerShadowNode(shape, shadow)

    override fun update(node: InnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        if (shape != other.shape) return false
        if (shadow != other.shadow) return false
        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

private class InnerShadowNode(
    var shape: Shape,
    var shadow: () -> InnerShadow?,
) : Modifier.Node(),
    DrawModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    private var prevRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        drawContent()

        val shadow = shadow() ?: return
        val layer = shadowLayer ?: return

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()

        val outline = shape.createOutline(size, layoutDirection, this)
        clipPath.reset()
        when (outline) {
            is Outline.Rectangle -> clipPath.addRect(outline.rect)
            is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect)
            is Outline.Generic -> clipPath.addPath(outline.path)
        }

        paint.color = shadow.color
        layer.alpha = shadow.alpha
        layer.blendMode = shadow.blendMode
        if (prevRadius != radius) {
            layer.renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
            prevRadius = radius
        }

        layer.record {
            drawContext.canvas.let { canvas ->
                canvas.save()
                canvas.clipPath(clipPath)
                canvas.drawOutline(outline, paint)
                canvas.translate(offsetX, offsetY)
                canvas.drawOutline(outline, ShadowMaskPaint)
                canvas.translate(-offsetX, -offsetY)
                canvas.restore()
            }
        }

        drawContext.canvas.let { canvas ->
            canvas.save()
            canvas.clipPath(clipPath)
            drawLayer(layer)
            canvas.restore()
        }
    }

    override fun onAttach() {
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        shadowLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            shadowLayer = null
        }
    }
}

private val ShadowMaskPaint: Paint = Paint().apply {
    blendMode = BlendMode.Clear
}
