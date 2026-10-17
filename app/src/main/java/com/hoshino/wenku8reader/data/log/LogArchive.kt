package com.hoshino.wenku8reader.data.log

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志压缩包的写入（**纯文件操作、不依赖 Android**，`LogArchiveTest` 覆盖）。
 *
 * 结构固定为：
 * ```
 * logs/app.2.log   最旧
 * logs/app.1.log
 * logs/app.log     最新
 * device-info.txt  导出时间 / 版本 / 机型 / 当前设置 / 隐私说明
 * ```
 * 之所以保留 `logs/` 前缀：用户解压后能和 `device-info.txt` 区分开，多个日志文件也不会散落在根目录。
 */
internal object LogArchive {

    /**
     * @param entries 日志文件名 → 内容，**按给定顺序**写入（调用方负责从旧到新排序）。
     * @param info 写入 `device-info.txt` 的文本。
     */
    fun write(dest: File, entries: List<Pair<String, ByteArray>>, info: String) {
        ZipOutputStream(dest.outputStream().buffered()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry("logs/$name"))
                out.write(bytes)
                out.closeEntry()
            }
            out.putNextEntry(ZipEntry("device-info.txt"))
            out.write(info.toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
    }
}
