package com.hoshino.wenku8reader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 应用 UI 风格（实验性）：Material 3 Expressive 或 MIUIX（HyperOS 设计语言）。
 *
 * 参考 SukiSU-Ultra 的做法：**同一套页面代码**，由一个 CompositionLocal 决定
 * 每个公共组件走哪套实现（见 `ui/components/Expressive.kt` 的分发），
 * 主题层则在根部切换 `MaterialExpressiveTheme` / `MiuixTheme`。
 * 因此切换风格不需要重启，也不需要为两套风格各写一份页面。
 */
enum class UiStyle(val key: String) {
    MATERIAL3("material3"),
    MIUIX("miuix"),
    ;

    companion object {
        /** 设置值 → 风格；未知值回退 Material 3（与旧版本读取到缺省值的行为一致）。 */
        fun fromKey(key: String?): UiStyle =
            entries.firstOrNull { it.key == key } ?: MATERIAL3
    }
}

/** 当前 UI 风格；由根主题注入（默认 Material 3）。 */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.MATERIAL3 }

/** 是否为 MIUIX 风格（组件内部分发用）。 */
@Composable
@ReadOnlyComposable
fun isMiuixStyle(): Boolean = LocalUiStyle.current == UiStyle.MIUIX
