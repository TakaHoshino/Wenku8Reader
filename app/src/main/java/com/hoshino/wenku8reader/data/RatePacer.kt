package com.hoshino.wenku8reader.data

// 统一限流组件：从 Wenku8Client.kt 拆出。
// 它是本项目**全部**请求节流状态的唯一所有者（全局间隔 / 自适应速率 / 搜索硬间隔），
// 与网络请求编排无关，独立成类后也便于单测。

import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * 统一限流组件：本项目**全部**请求节流状态的唯一所有者。
 *
 * 三层语义各自独立、不可合并成一个延时：
 * 1. [pace] 全局请求间隔（间隔 = 基数 × 自适应速率），保护站点也保护自己；
 * 2. [adjust] 自适应速率（成功回落 / 失败放大，1.0~8.0），遇 429 自动降速；
 * 3. [paceSearch] 搜索硬间隔（站点硬性要求两次搜索 ≥5s，短于该值直接返回错误页）。
 * 此外 App API 另有 `Semaphore(1)` 串行约束（官方 App 行为），留在调用侧。
 */
internal class RatePacer(
    private val baseIntervalMs: Long,
    private val searchIntervalMs: Long,
) {
    private val lock = Any()
    private var lastRequest = 0L
    private var lastSearch = 0L
    private var rate = 1.0

    /** 请求结果反馈：成功逐步回落（×0.85），失败立即放大（×2），钳制在 1.0~8.0。 */
    fun adjust(ok: Boolean) {
        synchronized(lock) {
            rate = if (ok) max(1.0, rate * 0.85) else min(8.0, rate * 2)
        }
    }

    /** 全局请求间隔：必要时挂起补足等待。 */
    suspend fun pace() {
        val sleep = synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = lastRequest + (baseIntervalMs * rate).toLong() - now
            lastRequest = now
            wait
        }
        if (sleep > 0) delay(sleep)
    }

    /** 搜索硬间隔：站点要求两次搜索间隔 ≥ [searchIntervalMs]。 */
    suspend fun paceSearch() {
        val sleep = synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = lastSearch + searchIntervalMs - now
            lastSearch = now
            wait
        }
        if (sleep > 0) delay(sleep)
    }
}
