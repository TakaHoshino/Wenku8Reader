package com.hoshino.wenku8reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.FlatChapter
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.data.local.ReadingStatsStore
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import com.hoshino.wenku8reader.ui.common.toUiTextOrUnknown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val title: String = "",
    val gid: Int? = null,
    val volumes: List<Volume> = emptyList(),
    val flatChapters: List<FlatChapter> = emptyList(),
    val tocLoading: Boolean = false,
    val currentChapter: ChapterContent? = null,
    val currentCid: String? = null,
    val chapterLoading: Boolean = false,
    val error: UiText? = null,
)

class ReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
    private val progressStore: ReadingProgressStore,
    private val readerSettings: ReaderSettings,
    val readingStats: ReadingStatsStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle["id"] ?: 0

    /** 目录页跳转指定章节时传入；为空则继续上次阅读位置。 */
    private val startCid: String? = savedStateHandle["cid"]

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    /** App-wide reader appearance settings (paper, text color, font, spacing, …). */
    val readerSettingsFlow: StateFlow<ReaderSettingsState> = readerSettings.flow

    /** Persists the chosen font size so it stays in sync with Settings. */
    fun setFontSize(size: Int) = readerSettings.setFontSize(size)
    fun setFontFamily(key: String) = readerSettings.setFontFamily(key)
    fun setFontWeight(weight: Int) = readerSettings.setFontWeight(weight)
    fun setLineSpacing(spacing: Float) = readerSettings.setLineSpacing(spacing)

    /** 章节读完（进度 100%）时标记为"已读"（目录页显示灰色 + 已读）。 */
    fun markChapterFinished(cid: String) {
        viewModelScope.launch { progressStore.markFinished(bookId, cid) }
    }

    fun setScrollMode(enabled: Boolean) = readerSettings.setScrollMode(enabled)
    fun setVolumeKeyTurnPage(enabled: Boolean) = readerSettings.setVolumeKeyTurnPage(enabled)
    fun setAutoNextChapter(enabled: Boolean) = readerSettings.setAutoNextChapter(enabled)
    fun setPageTurnDirection(leftToRight: Boolean) = readerSettings.setPageTurnDirection(leftToRight)
    fun setAutoTurnInterval(seconds: Int) = readerSettings.setAutoTurnInterval(seconds)
    fun setClickTurnPage(enabled: Boolean) = readerSettings.setClickTurnPage(enabled)
    fun setAutoPadding(enabled: Boolean) = readerSettings.setAutoPadding(enabled)
    fun setTopPadding(v: Int) = readerSettings.setTopPadding(v)
    fun setBottomPadding(v: Int) = readerSettings.setBottomPadding(v)
    fun setLeftPadding(v: Int) = readerSettings.setLeftPadding(v)
    fun setRightPadding(v: Int) = readerSettings.setRightPadding(v)

    init {
        openReader()
    }

    private fun flatten(volumes: List<Volume>): List<FlatChapter> {
        val out = mutableListOf<FlatChapter>()
        for (v in volumes) {
            for (c in v.chapters) {
                out.add(FlatChapter(out.size, c.cid, c.name))
            }
        }
        return out
    }

    fun openReader() {
        viewModelScope.launch {
            _ui.update { it.copy(error = null, tocLoading = true) }
            val info = repository.bookInfo(bookId).getOrNull()
            if (info == null) {
                _ui.update {
                    it.copy(
                        tocLoading = false,
                        error = UiText.StringResource(R.string.error_book_info),
                    )
                }
                return@launch
            }
            val gid = repository.groupIdOf(info)
            val vols = repository.chapters(bookId, gid).getOrDefault(emptyList())
            val flat = flatten(vols)
            if (flat.isEmpty()) {
                _ui.update {
                    it.copy(
                        tocLoading = false,
                        title = info.title,
                        gid = gid,
                        volumes = vols,
                        flatChapters = flat,
                        error = UiText.StringResource(R.string.error_chapter_index),
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    title = info.title,
                    gid = gid,
                    volumes = vols,
                    flatChapters = flat,
                    tocLoading = false,
                )
            }
            val resume = progressStore.read(bookId).resumeCid
            val target = startCid?.let { c -> flat.firstOrNull { it.cid == c } }
                ?: flat.firstOrNull { it.cid == resume && it.name != "插图" }
                ?: flat.firstOrNull { it.name != "插图" }
                ?: flat.firstOrNull()
            target?.let { loadChapter(it.cid) }
        }
    }

    /**
     * 当前章节加载任务。新的加载请求会取消上一个：
     * 目录快速连点或"上一章/下一章"连按会并发多个请求，
     * 先发后到的旧章节响应可能覆盖用户正在看的新章节。
     */
    private var loadChapterJob: Job? = null

    fun loadChapter(cid: String) {
        val gid = _ui.value.gid ?: return
        loadChapterJob?.cancel()
        loadChapterJob = viewModelScope.launch {
            _ui.update { it.copy(chapterLoading = true, error = null) }
            val result = repository.chapterContent(gid, bookId, cid)
            // 已被更新的请求取代：直接退出，不写任何状态
            if (!isActive) return@launch
            val ch = result.getOrNull()
            if (ch == null) {
                _ui.update {
                    it.copy(
                        chapterLoading = false,
                        // 异常 message 可能为 null，回退到通用文案避免空白提示
                        error = result.exceptionOrNull().toUiTextOrUnknown(),
                    )
                }
                return@launch
            }
            if (ch.title.isBlank() && ch.text.isBlank() && ch.images.isEmpty()) {
                _ui.update {
                    it.copy(
                        chapterLoading = false,
                        error = UiText.StringResource(R.string.error_chapter_load),
                    )
                }
                return@launch
            }
            // 只有确认拿到有效章节后才记录进度：若请求被取消（用户已切走），
            // 不应把"进入过该章"当作已读位置落盘。
            progressStore.savePosition(bookId, cid, totalChapters = _ui.value.flatChapters.size)
            // 重读机制：重复阅读已完成的章节 → 立即重置为未完成，直到再次读完才恢复"已读"
            if (cid in progressStore.read(bookId).finishedCids) {
                progressStore.resetFinished(bookId, cid)
            }
            val display = if (readerSettings.flow.value.traditionalChinese) {
                withContext(Dispatchers.Default) {
                    ch.copy(
                        title = ZhConverterUtil.toTraditional(ch.title),
                        text = ZhConverterUtil.toTraditional(ch.text),
                    )
                }
            } else {
                ch
            }
            // 繁简转换是挂起点，转换期间用户可能又切了章节
            if (!isActive) return@launch
            _ui.update {
                it.copy(chapterLoading = false, currentCid = cid, currentChapter = display)
            }
        }
    }
}
