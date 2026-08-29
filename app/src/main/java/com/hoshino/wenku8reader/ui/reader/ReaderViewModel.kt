package com.hoshino.wenku8reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.FlatChapter
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.data.local.ReadingStatsStore
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val preferences: AppPreferences,
    private val readerSettings: ReaderSettings,
    val readingStats: ReadingStatsStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle["id"] ?: 0

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    /** App-wide reader appearance settings (paper, text color, font, spacing, …). */
    val readerSettingsFlow: StateFlow<ReaderSettingsState> = readerSettings.flow

    /** Persists the chosen font size so it stays in sync with Settings. */
    fun setFontSize(size: Int) = readerSettings.setFontSize(size)
    fun setFontFamily(key: String) = readerSettings.setFontFamily(key)
    fun setFontWeight(weight: Int) = readerSettings.setFontWeight(weight)
    fun setLineSpacing(spacing: Float) = readerSettings.setLineSpacing(spacing)

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
            val resume = preferences.resumeCid(bookId)
            val target = flat.firstOrNull { it.cid == resume && it.name != "插图" }
                ?: flat.firstOrNull { it.name != "插图" }
                ?: flat.firstOrNull()
            target?.let { loadChapter(it.cid) }
        }
    }

    fun loadChapter(cid: String) {
        val gid = _ui.value.gid ?: return
        viewModelScope.launch {
            _ui.update { it.copy(chapterLoading = true, error = null) }
            val result = repository.chapterContent(gid, bookId, cid)
            val ch = result.getOrNull()
            if (ch == null) {
                _ui.update {
                    it.copy(
                        chapterLoading = false,
                        error = UiText.DynamicString(result.exceptionOrNull()?.message ?: ""),
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
            preferences.saveProgress(bookId, cid)
            val idx = _ui.value.flatChapters.indexOfFirst { it.cid == cid }
            if (idx >= 0) {
                preferences.saveProgressPosition(bookId, idx, _ui.value.flatChapters.size)
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
            _ui.update {
                it.copy(chapterLoading = false, currentCid = cid, currentChapter = display)
            }
        }
    }
}
