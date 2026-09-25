package com.hoshino.wenku8reader.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 同键并发去重（single-flight）。
 *
 * 解决的问题：内存/磁盘缓存只能挡住"请求已经完成"之后的重复访问，
 * 挡不住"两个页面几乎同时发起同一个请求"——详情页与目录页同时要同一本书的信息、
 * 阅读器与下载器同时要同一份目录时，两个协程都会发现缓存为空，于是各发一次真实请求。
 * 站点有全局节流（约 600ms/次），这类重复请求会直接拖慢首屏并加重风控。
 *
 * 做法：把 `key` 映射到一把 [Mutex]（**分段锁**，见下），同一时刻同键只放行一个协程执行
 * [run] 的 block；调用方在 block 内部**先查一次缓存**，因此排队者在拿到锁后会命中
 * 前一个协程刚写好的缓存，不会重复请求。
 *
 * 为什么是分段锁而不是"每键一把锁 + 用完删除"：
 * - 前者内存有界（固定 [stripes] 把锁），不会随"用户访问过的书"无限增长；
 * - 后者的删除时机与等待者数量耦合，容易出现锁泄漏或误删。
 * 代价是偶发哈希冲突会让两个**不同**键的请求排队，只影响并发度、不影响正确性。
 *
 * 注意：这里**不做结果缓存**（缓存仍由各调用点的 `TimedCache` 负责），
 * 只做"同一时刻同键只跑一个"。block 抛异常时锁照常释放（[withLock] 的既有语义）。
 */
internal class SingleFlight(stripes: Int = DEFAULT_STRIPES) {

    private val locks: List<Mutex> = List(stripes.coerceAtLeast(1)) { Mutex() }

    /** 本键落在哪一段（对哈希取绝对值后取模，保证索引非负）。 */
    fun stripeOf(key: Any): Int = (key.hashCode() and Int.MAX_VALUE) % locks.size

    suspend fun <T> run(key: Any, block: suspend () -> T): T =
        locks[stripeOf(key)].withLock { block() }

    /** 分段数：32 段对"阅读器同时要几十个不同章节"这种场景足够错开，内存也可忽略。 */
    companion object {
        const val DEFAULT_STRIPES = 32
    }
}
