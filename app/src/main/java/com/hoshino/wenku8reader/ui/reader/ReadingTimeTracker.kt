package com.hoshino.wenku8reader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** 阅读时长计时器滴答间隔（毫秒）与每次累加的秒数，两者必须一致以免统计漂移。 */
private const val READING_TICK_MS = 5_000L
private const val READING_TICK_SECONDS = 5L

/** 阅读时长落盘阈值：累计满 60 秒才写一次，降低 I/O 与重组开销。 */
private const val READING_FLUSH_SECONDS = 60L

/**
 * 阅读时长统计（阅读热力图数据源），从 `ReaderScreen.kt` 拆出：
 * - 仅应用在前台（Lifecycle RESUMED）且阅读器可见时累计；
 * - 每 60 秒把整段时长写入 [ReadingStatsStore] 并持久化；
 * - 退出阅读器（组合销毁）时把不足 60 秒的余量也冲刷进去，保证不丢。
 * 聚合口径：每日/每书分钟数 = ceil(秒数 / 60)，不足 1 分钟按 1 分钟计（由 UI 层聚合）。
 */
@Composable
internal fun ReadingTimeTracker(
    bookId: Int,
    bookName: String,
    store: com.hoshino.wenku8reader.data.local.ReadingStatsStore,
) {
    if (bookId <= 0) return
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingSeconds by remember { mutableLongStateOf(0L) }
    // 书名可能在阅读器打开后由 openReader() 回填（首帧为空），
    // 用 rememberUpdatedState 取最新值，避免 LaunchedEffect(bookId) 的闭包长期写入空书名。
    val currentBookName by rememberUpdatedState(bookName)

    // 功耗优化：每 5s 计一次（每次累加 5s），较原 1s 滴答减少 5 倍 CPU 唤醒；
    // 仍满足「分钟级 + 向上取整」的统计精度。
    LaunchedEffect(bookId) {
        while (true) {
            delay(READING_TICK_MS)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                pendingSeconds += READING_TICK_SECONDS
                if (pendingSeconds >= READING_FLUSH_SECONDS) {
                    store.addSeconds(bookId, currentBookName, pendingSeconds)
                    store.persist()
                    pendingSeconds = 0
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 冲刷余量，避免退出阅读器时丢失最后不足 60 秒的阅读
            if (pendingSeconds > 0) {
                store.addSeconds(bookId, currentBookName, pendingSeconds)
                store.persist()
            }
        }
    }
}
