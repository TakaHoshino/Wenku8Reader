package com.hoshino.wenku8reader.ui.components

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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 全局点击振动 Indication（Compose 1.7 IndicationNodeFactory API）：
 * 把默认 ripple 节点作为 delegate 委托（保留视觉水波纹），
 * 并在按下（PressInteraction.Press）时触发一次轻振动（TextHandleMove）。
 * [enabled] 为设置开关（经 rememberUpdatedState 动态读取，切换即时生效）。
 */
class HapticIndication(
    private val enabled: () -> Boolean,
    private val haptic: HapticFeedback,
    private val delegate: Indication,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        // 真实 ripple 节点均为 Modifier.Node；若委托不是 node factory
        //（如 debug 的 DefaultDebugIndication），退化为仅振动、无波纹，不崩溃。
        val rippleNode = (delegate as? IndicationNodeFactory)?.create(interactionSource) as? Modifier.Node
        return HapticIndicationNode(enabled, haptic, rippleNode, interactionSource)
    }

    override fun equals(other: Any?): Boolean =
        other is HapticIndication && other.delegate == delegate

    override fun hashCode(): Int = delegate.hashCode()
}

private class HapticIndicationNode(
    private val enabled: () -> Boolean,
    private val haptic: HapticFeedback,
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
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }
}

/**
 * 在应用根部注入：把 [LocalIndication] 替换为 [HapticIndication]（委托原 ripple + 按下振动）。
 * 所有 `Modifier.clickable` / 按钮 / 列表项 / 开关自动获得点击振动，无需逐处修改。
 */
@Composable
fun HapticScope(enabled: Boolean, content: @Composable () -> Unit) {
    val delegate = LocalIndication.current
    val haptic = LocalHapticFeedback.current
    val enabledState = rememberUpdatedState(enabled)
    val indication = remember(delegate, haptic) {
        HapticIndication(enabled = { enabledState.value }, haptic = haptic, delegate = delegate)
    }
    CompositionLocalProvider(LocalIndication provides indication) { content() }
}
