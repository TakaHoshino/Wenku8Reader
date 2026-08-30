package com.hoshino.wenku8reader.ui.components

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 全局点击振动 Indication（Compose 1.7 IndicationNodeFactory API）：
 * 把默认 ripple 节点作为 delegate 委托（保留视觉水波纹），
 * 并在按下时直接调用系统 [Vibrator] 按强度振动（Compose 的 performHapticFeedback
 * 只映射固定系统常量、无法调幅度，故改用 VibrationEffect.createOneShot）。
 */
class HapticIndication(
    private val vibrator: Vibrator?,
    private val enabled: () -> Boolean,
    private val strength: () -> Int,          // 0-100
    private val delegate: Indication,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        // 真实 ripple 节点均为 Modifier.Node；若委托不是 node factory
        //（如 debug 的 DefaultDebugIndication），退化为仅振动、无波纹，不崩溃。
        val rippleNode = (delegate as? IndicationNodeFactory)?.create(interactionSource) as? Modifier.Node
        return HapticIndicationNode(vibrator, enabled, strength, rippleNode, interactionSource)
    }

    override fun equals(other: Any?): Boolean =
        other is HapticIndication && other.delegate == delegate

    override fun hashCode(): Int = delegate.hashCode()
}

private class HapticIndicationNode(
    private val vibrator: Vibrator?,
    private val enabled: () -> Boolean,
    private val strength: () -> Int,
    delegateNode: Modifier.Node?,
    private val interactionSource: InteractionSource,
) : DelegatingNode() {

    init {
        delegateNode?.let { delegate(it) } // 委托绘制（默认 ripple），保留水波纹视觉
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                if (enabled() && interaction is PressInteraction.Press) {
                    vibrate(strength().coerceIn(0, 100))
                }
            }
        }
    }

    /** 按 0-100 强度振动：映射到 VibrationEffect 幅度 1-255（API 26+ 支持幅度控制）。 */
    private fun vibrate(strength: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator() || strength <= 0) return
        val amplitude = if (vib.hasAmplitudeControl()) {
            (strength * 255 / 100).coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        runCatching { vib.vibrate(VibrationEffect.createOneShot(20, amplitude)) }
    }
}

/**
 * 在应用根部注入：把 [LocalIndication] 替换为 [HapticIndication]（委托原 ripple + 按强度振动）。
 * 所有 `Modifier.clickable` / 按钮 / 列表项 / 开关自动获得点击振动，无需逐处修改。
 */
@Composable
fun HapticScope(
    enabled: Boolean,
    strength: Int,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // 类型化 getSystemService(Class)（API 23+），避免 Context.VIBRATOR_SERVICE 弃用
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val delegate = LocalIndication.current
    val enabledState = rememberUpdatedState(enabled)
    val strengthState = rememberUpdatedState(strength)
    val indication = remember(delegate, vibrator) {
        HapticIndication(
            vibrator = vibrator,
            enabled = { enabledState.value },
            strength = { strengthState.value },
            delegate = delegate,
        )
    }
    CompositionLocalProvider(LocalIndication provides indication) { content() }
}
