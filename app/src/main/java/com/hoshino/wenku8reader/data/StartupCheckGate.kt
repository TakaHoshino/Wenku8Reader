package com.hoshino.wenku8reader.data

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动检查闸门：**每次进程启动只放行一次**。
 *
 * 产品要求"每次启动都检查更新"，所以不再按时间节流（原先 24 小时一次）。
 * 但同一个进程内仍要防重复：`MainScaffold` 的 `LaunchedEffect` 会在 Activity 重建时再跑一次，
 * 其它入口也可能重复调用 —— 那会平白多打几次 GitHub API。
 *
 * 用 [AtomicBoolean] 而不是普通 Boolean：`check()` 可能被主线程与设置页同时触发。
 */
internal class StartupCheckGate {

    private val used = AtomicBoolean(false)

    /** @return true 表示本次进程还没检查过（调用方应执行检查）；false 表示已检查过。 */
    fun tryAcquire(): Boolean = used.compareAndSet(false, true)
}
