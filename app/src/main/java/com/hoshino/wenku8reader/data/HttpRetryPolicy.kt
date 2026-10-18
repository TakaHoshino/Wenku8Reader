package com.hoshino.wenku8reader.data

/**
 * HTTP 重试策略（**纯函数**，`HttpRetryPolicyTest` 覆盖）。
 *
 * **403 不重试**：站点前置的 Cloudflare 对"非浏览器直连"是硬拦截，重试只会
 * 白等 1.5s + 3s 并继续打站点。2026-10-18 的设备日志里一天出现 433 次 403，
 * 每次都跑满 3 次尝试；紧接着 Cronet 一次就拿到 200。所以 403 应当立刻失败、
 * 交给三级绕过栈，而不是退避重试。
 *
 * 429 / 5xx 仍然重试：它们是限流或临时故障，退避后有机会成功。
 */
internal object HttpRetryPolicy {

    /** Cloudflare 的硬拦截状态码。 */
    const val BLOCKED = 403

    /** 可退避重试的状态码。 */
    private val RETRYABLE = setOf(429, 500, 502, 503, 504)

    fun shouldRetry(code: Int): Boolean = code in RETRYABLE
}
