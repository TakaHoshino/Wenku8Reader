package com.hoshino.wenku8reader

import com.hoshino.wenku8reader.data.SingleFlight
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [SingleFlight] 的单元测试。
 *
 * 这些用例真实开多个协程（[Dispatchers.Default]），不做虚拟时间：
 * 要验证的正是"两个调用者同时在跑"时的行为，用真实并发才有意义。
 * 所有等待都套 [withTimeout]——若实现把锁泄漏了，测试会以超时失败而不是永久挂住。
 */
class SingleFlightTest {

    /**
     * 模拟客户端用法：block 内部先查缓存，未命中才"请求"并写回缓存。
     * [entries] 统计"进入临界区"的次数，[realCalls] 统计"真正发生请求"的次数——
     * 两者之差正体现了 SingleFlight（只管并发）与缓存（管结果）的分工。
     */
    private suspend fun SingleFlight.loadWithCache(
        key: String,
        cache: ConcurrentHashMap<String, String>,
        entries: AtomicInteger,
        realCalls: AtomicInteger,
        value: () -> String,
    ): String = run(key) {
        entries.incrementAndGet()
        cache[key] ?: run {
            realCalls.incrementAndGet()
            // 模拟一次真实网络耗时，让并发真的重叠
            delay(120)
            value().also { cache[key] = it }
        }
    }

    @Test
    fun `同键并发只真正请求一次`() = runBlocking {
        val singleFlight = SingleFlight()
        val cache = ConcurrentHashMap<String, String>()
        val entries = AtomicInteger()
        val realCalls = AtomicInteger()

        val first = async(Dispatchers.Default) {
            singleFlight.loadWithCache("info_1", cache, entries, realCalls) { "v1" }
        }
        // 确保第一个已经进入 block（否则测的就不是"并发"场景）
        delay(30)
        val second = async(Dispatchers.Default) {
            singleFlight.loadWithCache("info_1", cache, entries, realCalls) { "v2" }
        }

        val results = withTimeout(5_000) { listOf(first.await(), second.await()) }

        assertEquals("两个调用者都拿到同一份结果", listOf("v1", "v1"), results)
        assertEquals("真实请求只应发生一次", 1, realCalls.get())
        assertEquals("两个调用者都进了临界区（后者命中缓存）", 2, entries.get())
    }

    @Test
    fun `不同键可以并行执行`() = runBlocking {
        val singleFlight = SingleFlight()
        val startedA = CompletableDeferred<Unit>()
        val startedB = CompletableDeferred<Unit>()

        // A 等 B 开始、B 等 A 开始：若两者被串行化，会互相等待直到超时失败
        val a = launch(Dispatchers.Default) {
            singleFlight.run("a") {
                startedA.complete(Unit)
                startedB.await()
            }
        }
        val b = launch(Dispatchers.Default) {
            singleFlight.run("b") {
                startedB.complete(Unit)
                startedA.await()
            }
        }

        withTimeout(5_000) {
            a.join()
            b.join()
        }
        assertTrue("不同键未被串行化", a.isCompleted && b.isCompleted)
    }

    @Test
    fun `不做结果缓存_并发结束后再调仍会进临界区`() = runBlocking {
        val singleFlight = SingleFlight()
        val cache = ConcurrentHashMap<String, String>()
        val entries = AtomicInteger()
        val realCalls = AtomicInteger()

        val results = withTimeout(5_000) {
            listOf(
                async(Dispatchers.Default) {
                    singleFlight.loadWithCache("k", cache, entries, realCalls) { "v" }
                },
                async(Dispatchers.Default) {
                    singleFlight.loadWithCache("k", cache, entries, realCalls) { "v" }
                },
            ).map { it.await() }
        }
        assertEquals(listOf("v", "v"), results)
        assertEquals(1, realCalls.get())

        // 并发结束后再调一次：确实又进了临界区（SingleFlight 不缓存结果），
        // 但缓存命中，所以不会产生新的真实请求——去重与缓存各司其职。
        val third = withTimeout(5_000) {
            singleFlight.loadWithCache("k", cache, entries, realCalls) { "v" }
        }
        assertEquals("v", third)
        assertEquals("第三次调用仍会进入临界区", 3, entries.get())
        assertEquals("缓存命中不应产生新的真实请求", 1, realCalls.get())
    }

    @Test
    fun `block 抛异常后锁会释放`() = runBlocking {
        val singleFlight = SingleFlight()

        val thrown = runCatching {
            withTimeout(5_000) {
                singleFlight.run("boom") { throw IllegalStateException("加载失败") }
            }
        }.exceptionOrNull()
        if (thrown !is IllegalStateException) {
            fail("应当把 block 的异常原样抛给调用方，实际：$thrown")
        }

        // 若异常路径没有释放锁，这里会超时
        val after = withTimeout(5_000) { singleFlight.run("boom") { "ok" } }
        assertEquals("锁已释放，后续调用可以正常进入", "ok", after)
    }

    @Test
    fun `分段索引始终落在合法范围内`() {
        val singleFlight = SingleFlight(stripes = 8)
        // -1 的 hashCode 即 -1：直接用绝对值会溢出成负数，实现必须仍给出合法索引
        listOf("info_1", "", "chap_1_c1", -1, Int.MIN_VALUE).forEach { key ->
            val stripe = singleFlight.stripeOf(key)
            assertTrue("key=$key 的段索引 $stripe 越界", stripe in 0 until 8)
        }
    }
}
