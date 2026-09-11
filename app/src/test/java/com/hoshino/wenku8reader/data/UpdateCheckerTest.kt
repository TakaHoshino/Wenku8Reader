package com.hoshino.wenku8reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `UpdateChecker.isNewer` 的本地单元测试。
 *
 * 更新判定是纯逻辑，却直接决定用户会不会被提示升级（误判会导致反复弹窗或漏更新）。
 * 报告把它列为"天然易测"的四处之一，此前完全没有测试覆盖。
 */
class UpdateCheckerTest {

    private val checker = UpdateChecker()

    @Test
    fun identicalVersionIsNeverAnUpdate() {
        assertFalse(checker.isNewer("v1.0.0", "1.0.0"))
        assertFalse(checker.isNewer("1.0.0", "1.0.0"))
        // 完整版本（含 prerelease 后缀）相同 → 即便 allowEqual 也不能视为更新
        assertFalse(checker.isNewer("v0.3.0-dev.21", "0.3.0-dev.21", allowEqual = true))
    }

    @Test
    fun comparesVersionSegmentsNumerically() {
        assertTrue(checker.isNewer("v1.0.1", "1.0.0"))
        assertTrue(checker.isNewer("v1.1.0", "1.0.9"))
        assertTrue(checker.isNewer("v2.0.0", "1.99.99"))
        assertFalse(checker.isNewer("v1.0.0", "1.0.1"))
        // 数值比较而非字符串比较：1.9 必须小于 1.10
        assertFalse(checker.isNewer("v1.9.0", "1.10.0"))
    }

    @Test
    fun missingSegmentsAreTreatedAsZero() {
        assertTrue(checker.isNewer("v1.1", "1.0.9"))
        assertFalse(checker.isNewer("v1.0", "1.0.1"))
    }

    @Test
    fun prereleaseToStableOnSameBaseIsAnUpdate() {
        // 当前是测试版，正式版通道发布了同基础版本 → 正式版应覆盖它
        assertTrue(checker.isNewer("v0.3.0", "0.3.0-dev.21"))
    }

    @Test
    fun stableToPrereleaseOnSameBaseIsNotAnUpdate() {
        assertFalse(checker.isNewer("v0.3.0-dev.22", "0.3.0"))
    }

    @Test
    fun allowEqualOnlyAffectsSameBasePrereleaseBuilds() {
        // 测试版通道（allowEqual = true）：同基础的不同测试构建视为更新
        assertTrue(checker.isNewer("v0.3.0-dev.22", "0.3.0-dev.21", allowEqual = true))
        // 正式版通道（默认）：不得因此误报
        assertFalse(checker.isNewer("v0.3.0-dev.22", "0.3.0-dev.21"))
    }
}
