package com.hoshino.wenku8reader.data.log

import java.io.File
import java.io.FileOutputStream

/**
 * 按大小滚动的日志文件（**不依赖 Android**，可直接用临时目录单测）。
 *
 * 布局（[keep] = 3 时）：
 * ```
 * app.log      最新（正在写）
 * app.1.log    上一份
 * app.2.log    再上一份（下一次滚动时被删掉）
 * ```
 * 总占用上限约 `maxBytes × keep`，所以日志不会无限增长；想要更久的历史就调大这两个常量，
 * 而不是取消上限（用户数据目录里最不该出现的就是一个几 GB 的日志）。
 *
 * 每次 [append] 打开 / 关闭一次文件（而不是长期持有 writer）：日志量以"每个请求一条"计，
 * 这点开销可以忽略；换来的是进程被系统杀掉时不丢缓冲区的日志。
 */
internal class RollingLogFile(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val keep: Int = DEFAULT_KEEP,
) {

    private val lock = Any()

    fun append(line: String) {
        synchronized(lock) {
            runCatching {
                dir.mkdirs()
                val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
                val current = currentFile()
                // 超限就滚动；单条就超过上限时仍写进去（否则这条日志会凭空消失）
                if (current.isFile && current.length() > 0 && current.length() + bytes.size > maxBytes) {
                    rotate()
                }
                FileOutputStream(currentFile(), true).use { it.write(bytes) }
            }
        }
    }

    /** 现存日志文件，**从旧到新**排列（导出时按这个顺序打进压缩包）。 */
    fun files(): List<File> = synchronized(lock) {
        (keep - 1 downTo 0)
            .map { File(dir, fileName(it)) }
            .filter { it.isFile }
    }

    fun totalBytes(): Long = synchronized(lock) {
        files().sumOf { it.length() }
    }

    /**
     * 在锁内读出全部日志内容（文件名 + 字节），**从旧到新**。
     *
     * 导出用它而不是直接读文件：并发写入时直接读可能拿到半行；这里与 [append] 共用同一把锁，
     * 拿到的是一份完整快照。
     */
    fun snapshot(): List<Pair<String, ByteArray>> = synchronized(lock) {
        files().map { it.name to it.readBytes() }
    }

    fun clear() {
        synchronized(lock) {
            (0 until keep).forEach { File(dir, fileName(it)).delete() }
        }
    }

    private fun currentFile(): File = File(dir, fileName(0))

    private fun rotate() {
        File(dir, fileName(keep - 1)).delete()
        for (i in keep - 2 downTo 1) {
            File(dir, fileName(i)).renameTo(File(dir, fileName(i + 1)))
        }
        currentFile().renameTo(File(dir, fileName(1)))
    }

    private fun fileName(index: Int): String = if (index == 0) "app.log" else "app.$index.log"

    companion object {
        /** 单个文件上限：512 KiB（约 3000~5000 条网络记录）。 */
        const val DEFAULT_MAX_BYTES: Long = 512L * 1024L

        /** 保留份数（含正在写的那份）。 */
        const val DEFAULT_KEEP: Int = 3
    }
}
