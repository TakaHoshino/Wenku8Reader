package com.hoshino.wenku8reader.data.log

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出包必须是一个**标准、可解压**的 zip：日志按顺序放在 `logs/` 下，另有 `device-info.txt`。
 */
class LogArchiveTest {

    @Test
    fun `打成可解压的 zip_包含日志与设备信息`() {
        val dir = Files.createTempDirectory("w8r-archive").toFile()
        try {
            val zip = File(dir, "logs.zip")
            LogArchive.write(
                dest = zip,
                entries = listOf(
                    "app.2.log" to "old-line\n".toByteArray(Charsets.UTF_8),
                    "app.log" to "new-line\n".toByteArray(Charsets.UTF_8),
                ),
                info = "设备信息：测试",
            )

            ZipFile(zip).use { archive ->
                val names = archive.entries().toList().map { it.name }
                assertEquals(listOf("logs/app.2.log", "logs/app.log", "device-info.txt"), names)
                assertEquals(
                    "old-line\n",
                    archive.getInputStream(archive.getEntry("logs/app.2.log"))
                        .bufferedReader(Charsets.UTF_8).readText(),
                )
                assertTrue(
                    archive.getInputStream(archive.getEntry("device-info.txt"))
                        .bufferedReader(Charsets.UTF_8).readText()
                        .contains("设备信息：测试"),
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
