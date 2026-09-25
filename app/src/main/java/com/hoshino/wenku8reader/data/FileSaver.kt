package com.hoshino.wenku8reader.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves downloaded books to the public Downloads/Wenku8 folder. */
object FileSaver {

    /** 相对路径（MediaStore 里以 `Download/Wenku8/` 形式存储）。 */
    private const val RELATIVE_DIR = "Wenku8"

    /** Returns a human readable location, or null on failure. */
    fun saveDownload(context: Context, displayName: String, mime: String,
                     bytes: ByteArray): String? {
        val safeName = sanitize(displayName)
        return if (Build.VERSION.SDK_INT >= 29) {
            saveMediaStore(context, safeName, mime, bytes)
        } else {
            saveAppDir(context, safeName, bytes)
        }
    }

    /**
     * MediaStore 落盘（API 29+）。
     *
     * 关键点：**插入前先删掉同名的旧文件**。
     * 同名文件存在时，MediaProvider 会自动把新文件改名为 `书名 (1).txt`，
     * 于是用户每重新下载一次就多一份副本（`(1)`、`(2)`…），既占空间又让人以为下载失败。
     * 这里连同历史遗留的 `(N)` 副本一起清掉（见 [isDuplicateDownloadName]），
     * 保证「重新下载」= 覆盖，而不是不断堆积。
     *
     * 另外按官方建议用 `IS_PENDING` 写入：写入期间对其他应用不可见，
     * 避免 Downloads/图库在写入中途看到半截文件。
     */
    private fun saveMediaStore(context: Context, name: String, mime: String,
                               bytes: ByteArray): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + RELATIVE_DIR

        removeExistingFiles(resolver, collection, relativePath, name)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        runCatching { resolver.update(uri, published, null, null) }
            .onFailure {
                // 无法解除 pending 的文件对其他应用不可见，等于坏文件：删掉并报失败
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
        return "Downloads/$RELATIVE_DIR/$name"
    }

    /**
     * 删除目标目录下所有「与 [name] 同名或属于其自动重命名副本」的记录。
     *
     * 只按 `RELATIVE_PATH` 过滤目录、名字在内存里比对：RELATIVE_PATH 在库中的
     * 尾部斜杠随版本而变（`Download/Wenku8` 与 `Download/Wenku8/` 都出现过），
     * 拼在 SQL 里匹配容易漏；而本目录内的文件数量很少，取回名字再比对更稳。
     */
    private fun removeExistingFiles(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
        name: String,
    ) {
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} IN (?, ?)"
        val args = arrayOf(relativePath, "$relativePath/")
        runCatching {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val existing = cursor.getString(nameCol) ?: continue
                    if (!isDuplicateDownloadName(existing, name)) continue
                    val id = cursor.getLong(idCol)
                    runCatching {
                        resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
                    }
                }
            }
        }
    }

    /** API 28 及以下的回退：写应用私有外部目录。 */
    private fun saveAppDir(context: Context, name: String, bytes: ByteArray): String? {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            RELATIVE_DIR,
        )
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, name)
        return try {
            f.writeBytes(bytes)
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitize(name: String): String {
        val s = name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_', '.')
        return s.ifEmpty { "novel" }
    }
}

/** " (1)" / "(1)" 结尾的自动重命名后缀（Android 标准形式）。 */
private val AUTO_RENAME_SUFFIX_BEFORE_EXT = Regex("""\s*\(\d+\)$""")

/** 追加在扩展名之后的 "(1)"（部分 ROM / 旧版本行为，即用户看到的 `XXX.txt(1)`）。 */
private val AUTO_RENAME_SUFFIX_AFTER_EXT = Regex("""^\s*\(\d+\)$""")

/**
 * [candidate] 是否是 [target] 的「同名文件或自动重命名副本」。
 *
 * 需要覆盖两种系统行为：
 * 1. **标准**：在扩展名前插入 ` (N)`，如 `书名.txt` → `书名 (1).txt`；
 * 2. **部分 ROM / 旧版本**：直接追加在末尾，如 `书名.txt` → `书名.txt(1)`
 *    （这正是用户报障里看到的形态）。
 *
 * 抽成纯函数是为了能脱离 Android 环境单测：这段逻辑决定**删哪些用户文件**，
 * 判错一次就可能误删别人手动放进 Downloads/Wenku8 的同名书。
 */
internal fun isDuplicateDownloadName(candidate: String, target: String): Boolean {
    if (candidate == target) return true

    // 形态 1：扩展名前带序号
    val targetStem = target.substringBeforeLast('.', target)
    val targetExt = target.substringAfterLast('.', "")
    val candidateStem = candidate.substringBeforeLast('.', candidate)
    val candidateExt = candidate.substringAfterLast('.', "")
    if (targetExt.isNotEmpty() && candidateExt.equals(targetExt, ignoreCase = true)) {
        val stripped = candidateStem.replace(AUTO_RENAME_SUFFIX_BEFORE_EXT, "")
        if (stripped != candidateStem && stripped == targetStem) return true
    }

    // 形态 2：扩展名后带序号
    if (candidate.startsWith(target)) {
        val rest = candidate.removePrefix(target)
        if (AUTO_RENAME_SUFFIX_AFTER_EXT.matches(rest)) return true
    }

    return false
}
