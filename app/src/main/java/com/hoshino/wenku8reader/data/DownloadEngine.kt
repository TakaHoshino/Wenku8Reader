package com.hoshino.wenku8reader.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

enum class JobStatus { PENDING, RUNNING, DONE, FAILED, CANCELLED }

@Immutable
data class DownloadJob(
    val bookId: Int,
    val bookName: String,
    val format: String,             // "txt" | "epub"
    val encoding: String = "utf8",  // "utf8" | "gbk" | "big5"
    val progress: Float = 0f,
    val status: JobStatus = JobStatus.PENDING,
    val filePath: String? = null,
    val error: String? = null,
)

/** Runs download jobs on Dispatchers.IO, exposing progress via StateFlow. */
class DownloadEngine(
    private val context: Context,
    private val client: Wenku8Client,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = mutableSetOf<Int>()
    private val cancelFlags = mutableMapOf<Int, Boolean>()

    private val _jobs = MutableStateFlow<Map<Int, DownloadJob>>(emptyMap())
    val jobs: StateFlow<Map<Int, DownloadJob>> = _jobs.asStateFlow()

    @Synchronized
    fun enqueue(bookId: Int, bookName: String, format: String,
                encoding: String = "utf8"): Boolean {
        if (bookId in active) return false
        active.add(bookId)
        _jobs.value = _jobs.value + (
            bookId to DownloadJob(bookId, bookName, format, encoding)
        )
        scope.launch { run(bookId, bookName, format, encoding) }
        return true
    }

    @Synchronized
    fun cancel(bookId: Int) {
        cancelFlags[bookId] = true
    }

    @Synchronized
    fun cancelAll() {
        cancelFlags.keys.forEach { cancelFlags[it] = true }
    }

    @Synchronized
    fun isActive(bookId: Int): Boolean = bookId in active

    private fun update(id: Int, transform: (DownloadJob) -> DownloadJob) {
        val cur = _jobs.value[id] ?: return
        _jobs.value = _jobs.value + (id to transform(cur))
    }

    // ------------------------------------------------------------------ //
    private suspend fun run(bookId: Int, bookName: String, format: String,
                            encoding: String) {
        update(bookId) { it.copy(status = JobStatus.RUNNING, progress = 0f) }
        try {
            val info = client.bookInfo(bookId)
            val gid = info.groupId ?: 1
            val bytes: ByteArray
            val fileName: String
            val mime: String

            if (format == "txt") {
                bytes = downloadTxt(info, gid, encoding) { p ->
                    update(bookId) { it.copy(progress = p) }
                }
                fileName = "${info.title}.txt"
                mime = "text/plain; charset=utf-8"
            } else {
                update(bookId) { it.copy(progress = 0.05f) }
                bytes = downloadEpub(info, gid) { p ->
                    update(bookId) { it.copy(progress = p) }
                }
                fileName = "${info.title}.epub"
                mime = "application/epub+zip"
            }
            checkCancelled(bookId)
            val path = withContext(Dispatchers.IO) {
                FileSaver.saveDownload(context, fileName, mime, bytes)
            }
            update(bookId) {
                it.copy(status = JobStatus.DONE, progress = 1f, filePath = path)
            }
        } catch (e: CancellationException) {
            update(bookId) { it.copy(status = JobStatus.CANCELLED, error = e.message) }
        } catch (e: Exception) {
            update(bookId) { it.copy(status = JobStatus.FAILED, error = e.message) }
        } finally {
            synchronized(this) {
                active.remove(bookId)
                cancelFlags.remove(bookId)
            }
        }
    }

    private suspend fun downloadTxt(
        info: BookInfo,
        gid: Int,
        encoding: String,
        onProgress: (Float) -> Unit,
    ): ByteArray {
        val dlType = when (encoding) {
            "big5" -> "big5"
            "gbk" -> "txt"
            else -> "utf8"
        }
        val raw = runCatching { client.downloadFullTxt(info.id, dlType) }
            .getOrNull()
        if (raw != null && raw.isNotEmpty()) return raw

        // fallback: fetch chapter by chapter
        val chapters = fetchAllChapters(info, gid, onProgress)
        val sb = StringBuilder().append(info.title).append("\n")
        if (info.author.isNotEmpty()) sb.append("作者：").append(info.author).append("\n")
        for (ch in chapters) {
            sb.append("\n").append(ch.title).append("\n\n").append(ch.text).append("\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun downloadEpub(
        info: BookInfo,
        gid: Int,
        onProgress: (Float) -> Unit,
    ): ByteArray {
        var chapters: List<ChapterContent>? = null
        val raw = runCatching { client.downloadFullTxt(info.id, "utf8") }
            .getOrNull()
        if (raw != null && raw.isNotEmpty()) {
            val txt = String(raw, Charsets.UTF_8)
            val vols = client.chapters(info.id, gid)
            chapters = Parsers.splitFullTxt(txt, vols)
            val total = vols.sumOf { it.chapters.size }.coerceAtLeast(1)
            val filled = chapters.count { it.text.isNotEmpty() }
            if (filled < (total * 0.6f).toInt().coerceAtLeast(1)) {
                chapters = null
            }
        }
        if (chapters == null) {
            onProgress(0f)
            chapters = fetchAllChapters(info, gid, onProgress)
        }
        onProgress(0.9f)
        return EpubBuilder.build(info.title, info.author, info.description, chapters)
    }

    private suspend fun fetchAllChapters(
        info: BookInfo,
        gid: Int,
        onProgress: (Float) -> Unit,
    ): List<ChapterContent> {
        val vols = client.chapters(info.id, gid)
        val total = vols.sumOf { it.chapters.size }.coerceAtLeast(1)
        val list = mutableListOf<ChapterContent>()
        var done = 0
        for (v in vols) {
            for (ch in v.chapters) {
                checkCancelled(info.id)
                list.add(client.chapterContent(gid, info.id, ch.cid))
                done++
                onProgress(done.toFloat() / total)
            }
        }
        return list
    }

    private fun checkCancelled(bookId: Int) {
        if (cancelFlags[bookId] == true) {
            throw CancellationException("已取消")
        }
    }
}
