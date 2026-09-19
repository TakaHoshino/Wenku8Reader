package com.hoshino.wenku8reader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * MD3 motion: scales the content down slightly while pressed, with a ripple
 * feedback. Replaces a plain `Modifier.clickable { ... }`.
 *
 * 仅用于少量强调按钮/主操作：每次调用都会挂一个 pressed 状态观察者 + 一个动画，
 * 高密度列表项（如每行一个的分段列表行、封面卡片）请直接用普通
 * `Modifier.clickable`（波纹由全局 [LocalIndication] 提供即可），
 * 否则长列表会平白多出成百上千个状态与动画开销。
 */
@Composable
fun Modifier.pressClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按下缩放走主题的 motion scheme（Expressive = 快速空间弹簧，标准 = 线性/低弹）
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = scaleSpec,
        label = "pressScale",
    )
    return this
        // 用 graphicsLayer 的 lambda 读取缩放值：动画帧只让绘制阶段失效，
        // 不会像 Modifier.scale(scale) 那样每帧重跑整条 modifier 链的组合
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
}
