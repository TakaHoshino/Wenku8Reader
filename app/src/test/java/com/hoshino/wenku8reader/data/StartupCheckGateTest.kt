package com.hoshino.wenku8reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 启动检查闸门：同一进程只放行一次，不同进程互不影响。 */
class StartupCheckGateTest {

    @Test
    fun `同一进程只放行一次`() {
        val gate = StartupCheckGate()
        assertTrue(gate.tryAcquire())
        // Activity 重建 / 其它入口再次调用时不应重复请求 GitHub
        assertFalse(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }

    @Test
    fun `不同进程实例互不影响`() {
        val first = StartupCheckGate()
        val second = StartupCheckGate()
        assertTrue(first.tryAcquire())
        assertTrue(second.tryAcquire())
    }
}
