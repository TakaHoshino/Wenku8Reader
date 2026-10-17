package com.hoshino.wenku8reader.data.log

import android.content.Context
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/** 日志级别（只影响行首标签，不做过滤——文件本身有滚动上限）。 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * 应用日志（**重点记录网络访问**）。
 *
 * 设计取舍：
 * - **不用第三方库**：日志就是几行文本 + 一个 zip，引入 logging 框架不划算；
 * - **写文件而不是 logcat**：用户能导出、重启后仍在，logcat 会被系统清掉；
 * - **有上限**：`filesDir/logs/` 下最多 3 × 512 KiB（见 [RollingLogFile]），不会吃满存储；
 * - **可安全外发**：只写"方法 + 地址 + 状态码 + 耗时 + 错误类型"，地址经 [LogSanitizer] 脱敏；
 *   调用方**不得**把 Cookie、密码、请求体、章节正文传进来（见 `Wenku8Client` 的打点）；
 * - **不参与备份**：`backup_rules.xml` / `data_extraction_rules.xml` 排除了 `logs/`，
 *   只有用户主动导出（分享面板）时才会离开设备。
 *
 * 导出：`filesDir/logs/` 下的日志文件 + `device-info.txt` 打成 zip，放到 `cacheDir/log_exports/`。
 */
class AppLogger(context: Context) {

    private val appContext = context.applicationContext
    private val rolling = RollingLogFile(File(appContext.filesDir, DIR_NAME))
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val stampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    fun debug(tag: String, message: String) = write(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) = write(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = write(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = write(LogLevel.ERROR, tag, message)

    fun log(level: LogLevel, tag: String, message: String) = write(level, tag, message)

    fun totalBytes(): Long = rolling.totalBytes()

    fun clear() {
        rolling.clear()
        File(appContext.cacheDir, EXPORT_DIR).let { it.listFiles()?.forEach(File::delete) }
    }

    /**
     * 导出日志压缩包。
     *
     * @param extra 额外写进 `device-info.txt` 的上下文（主站镜像、UI 风格、实验开关等）。
     *   由调用方从当前设置里取——**不要**放用户名、Cookie 或任何凭据。
     * @return 生成的 zip（位于 `cacheDir/log_exports/`），失败时返回 [Result.failure]。
     */
    fun exportZip(extra: Map<String, String> = emptyMap()): Result<File> = runCatching {
        val exportDir = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }
        // 清掉一小时前的旧包，而不是无差别清空：用户可能已点开分享面板、
        // 但还没选好目标应用，此时删文件会让那次分享读到空 URI。
        exportDir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > EXPORT_MAX_AGE_MS) file.delete()
        }
        val zip = File(exportDir, "wenku8reader-logs-${LocalDateTime.now().format(stampFormat)}.zip")
        // 锁内取快照：与并发写入串行化，不会把半行日志打进包里
        val logs = rolling.snapshot()
        LogArchive.write(zip, logs, deviceInfo(extra, logs))
        // 打包之后再记一条：这条不会出现在本次压缩包里，但能证明"导出过"
        info(TAG, "export -> ${zip.name} (${zip.length()} bytes, ${logs.size} files)")
        zip
    }

    private fun write(level: LogLevel, tag: String, message: String) {
        val line = buildString {
            append(LocalDateTime.now().format(timeFormat))
            append(" [")
            append(level.name.padEnd(5))
            append("] [")
            append(tag.take(16))
            append("] ")
            append(LogSanitizer.sanitizeMessage(message))
        }
        rolling.append(line)
    }

    private fun deviceInfo(extra: Map<String, String>, logs: List<Pair<String, ByteArray>>): String = buildString {
        appendLine("Wenku8Reader 日志包")
        appendLine("导出时间: ${LocalDateTime.now().format(timeFormat)} (${TimeZone.getDefault().id})")
        appendLine("应用版本: ${appVersion()}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine(
            "设备: ${Build.MANUFACTURER} ${Build.MODEL} " +
                "(${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()})",
        )
        appendLine("语言: ${Locale.getDefault().toLanguageTag()}")
        appendLine("日志: ${logs.size} 个文件，共 ${logs.sumOf { it.second.size }} 字节")
        if (extra.isNotEmpty()) {
            appendLine()
            extra.forEach { (key, value) -> appendLine("$key: $value") }
        }
        appendLine()
        appendLine(
            "说明：日志只记录请求方法、地址、状态码、耗时与错误类型；不含 Cookie、密码、" +
                "请求体或章节正文，地址中的敏感参数已替换为 ***。",
        )
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    }.getOrDefault("unknown")

    companion object {
        /** 日志目录名（`filesDir/logs`）；存储统计与备份规则都按这个名字对齐。 */
        const val DIR_NAME = "logs"

        /** 导出目录名（`cacheDir/log_exports`，对应 `file_paths.xml` 的 `log_exports`）。 */
        const val EXPORT_DIR = "log_exports"

        /** 导出包的保留时长：给"分享面板还开着"留足时间，之后随缓存回收。 */
        private const val EXPORT_MAX_AGE_MS = 60L * 60 * 1000

        private const val TAG = "LOG"
    }
}
