package com.hoshino.wenku8reader.data

import android.content.Context
import androidx.compose.runtime.Immutable
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
    /**
     * 经仓库层取数（而非直接持有 [Wenku8Client]）：
     * 仓库是本项目「网络与解析」的唯一出口，此前下载引擎绕过它直连客户端，
     * 使 `Wenku8Repository.downloadFullTxt` 沦为无人调用的摆设，分层收益被削弱。
     */
    private val repository: Wenku8Repository,
    /** 应用级协程作用域（由 AppContainer 注入并统一管理）；省略时自建，保证可独立构造。 */
    appScope: CoroutineScope? = null,
) {
    private val scope: CoroutineScope =
        appScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 正在处理的书 id（并发安全集合：enqueue / run / finally 跨线程访问）。 */
    private val active: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /**
     * 取消标记。使用 [ConcurrentHashMap] 而非普通 map：`checkCancelled` 在工作线程
     * **非同步**读取，而 `cancel`/`enqueue` 在调用线程写入，普通 map 的跨线程可见性
     * 只能依赖偶发的锁，存在漏读取消信号的风险。
     */
    private val cancelFlags = ConcurrentHashMap<Int, Boolean>()

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

    private fun update(id: Int, transform: (DownloadJob) -> DownloadJob) {
        val cur = _jobs.value[id] ?: return
        _jobs.value = _jobs.value + (id to transform(cur))
    }

    // ------------------------------------------------------------------ //
    private suspend fun run(bookId: Int, bookName: String, format: String,
                            encoding: String) {
        update(bookId) { it.copy(status = JobStatus.RUNNING, progress = 0f) }
        try {
            val info = repository.bookInfo(bookId).getOrThrow()
            // gid 统一走 groupIdOf（缺字段时回退 id/1000）；原先这里写死 `?: 1`，
            // 与 Reader/Toc 的推导方式不一致，取不到 gid 时会请求到错误目录。
            val gid = repository.groupIdOf(info)
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
            if (path == null) {
                // 落盘失败（存储不可写 / 空间不足）绝不能标记为完成，
                // 否则 UI 显示「已保存 · 完成」而实际没有文件。
                update(bookId) {
                    it.copy(status = JobStatus.FAILED, error = "保存到下载目录失败")
                }
            } else {
                update(bookId) {
                    it.copy(status = JobStatus.DONE, progress = 1f, filePath = path)
                }
            }
        } catch (e: CancellationException) {
            update(bookId) { it.copy(status = JobStatus.CANCELLED, error = e.message) }
        } catch (e: Exception) {
            update(bookId) { it.copy(status = JobStatus.FAILED, error = e.message) }
        } finally {
            // active / cancelFlags 均为并发安全集合，无需再套 synchronized
            active.remove(bookId)
            cancelFlags.remove(bookId)
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
        // 仓库层已保证不吞协程取消，可安全用 getOrNull 做「拿不到就回退逐章抓取」的判断
        val raw = repository.downloadFullTxt(info.id, dlType).getOrNull()
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
        val raw = repository.downloadFullTxt(info.id, "utf8").getOrNull()
        if (raw != null && raw.isNotEmpty()) {
            val txt = String(raw, Charsets.UTF_8)
            val vols = repository.chapters(info.id, gid).getOrThrow()
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
        val vols = repository.chapters(info.id, gid).getOrThrow()
        val total = vols.sumOf { it.chapters.size }.coerceAtLeast(1)
        val list = mutableListOf<ChapterContent>()
        var done = 0
        for (v in vols) {
            for (ch in v.chapters) {
                checkCancelled(info.id)
                list.add(repository.chapterContent(gid, info.id, ch.cid).getOrThrow())
                done++
                onProgress(done.toFloat() / total)
            }
        }
        return list
    }

    /**
     * 取消检查。读取 [ConcurrentHashMap]（见其 KDoc），跨线程可见性有保证，
     * 不会因为普通 map 的偶发锁而漏读取消信号。
     */
    private fun checkCancelled(bookId: Int) {
        if (cancelFlags[bookId] == true) {
            throw CancellationException("已取消")
        }
    }
}
