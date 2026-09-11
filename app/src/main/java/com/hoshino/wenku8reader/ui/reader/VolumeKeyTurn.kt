package com.hoshino.wenku8reader.ui.reader

/**
 * Bridge so MainActivity can forward volume-key presses to the open reader,
 * since Compose does not receive volume keys directly.
 *
 * 使用约束（阅读器侧必须遵守，否则会残留过期闭包）：
 * 1. 回调须由 `rememberUpdatedState` 包装后的最新 `turnPage` 转发，
 *    否则该单例会长期持有首次组合时的闭包（读到旧的页号/章节）；
 * 2. 注册与清理由 ReaderScreen 中**同一个** `DisposableEffect` 完成，
 *    避免与 `LaunchedEffect` 的执行顺序耦合；
 * 3. 关闭"音量键翻页"时应同时把 `enabled` 置 false 并清空两个回调。
 *    MainActivity 侧对"回调为 null"已做兜底（落回 `super.dispatchKeyEvent`，
 *    不会吞掉音量调节），但清空仍是必要的——避免退出阅读器后旧闭包被误触发。
 */
object VolumeKeyTurn {
    @Volatile var enabled: Boolean = false
    @Volatile var onVolumeUp: (() -> Unit)? = null
    @Volatile var onVolumeDown: (() -> Unit)? = null
}
