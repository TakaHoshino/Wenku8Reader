package com.hoshino.wenku8reader.data.log

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志滚动：写满就换文件、总数有上限、最新内容一定保得住。
 *
 * 直接用临时目录跑真实文件 I/O——这段逻辑没有 Android 依赖，没必要为它引入 Robolectric。
 */
class RollingLogFileTest {

    @Test
    fun `按大小滚动_只保留最近 keep 份`() {
        val dir = Files.createTempDirectory("w8r-logs").toFile()
        try {
            val log = RollingLogFile(dir, maxBytes = 50, keep = 3)
            repeat(10) { log.append("line-$it-xxxxxxxx") }

            val files = log.files()
            assertEquals(3, files.size)
            // 从旧到新：app.2.log → app.1.log → app.log
            assertEquals(listOf("app.2.log", "app.1.log", "app.log"), files.map { it.name })
            assertTrue(files.last().readText().contains("line-9"))
            // 最早的内容已经被滚掉，而不是无限增长
            assertFalse(files.joinToString("") { it.readText() }.contains("line-0-"))
            assertTrue(log.totalBytes() <= 3 * 50L)
            // 导出用的快照：顺序与文件一致、内容完整
            val snapshot = log.snapshot()
            assertEquals(files.map { it.name }, snapshot.map { it.first })
            assertTrue(snapshot.last().second.toString(Charsets.UTF_8).contains("line-9"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `单条超过上限时仍然写入而不是丢弃`() {
        val dir = Files.createTempDirectory("w8r-logs").toFile()
        try {
            val log = RollingLogFile(dir, maxBytes = 10, keep = 2)
            log.append("a-very-long-single-line")
            assertEquals(1, log.files().size)
            assertTrue(log.files().single().readText().contains("a-very-long-single-line"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `清空后没有残留文件`() {
        val dir = Files.createTempDirectory("w8r-logs").toFile()
        try {
            val log = RollingLogFile(dir, maxBytes = 32, keep = 3)
            repeat(5) { log.append("line-$it") }
            log.clear()
            assertTrue(log.files().isEmpty())
            assertEquals(0L, log.totalBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `目录不存在时自动创建`() {
        val root = Files.createTempDirectory("w8r-logs").toFile()
        val nested = root.resolve("a/b/c")
        try {
            val log = RollingLogFile(nested, maxBytes = 1024, keep = 2)
            log.append("hello")
            assertTrue(nested.isDirectory)
            assertTrue(log.files().single().readText().contains("hello"))
        } finally {
            root.deleteRecursively()
        }
    }
}
