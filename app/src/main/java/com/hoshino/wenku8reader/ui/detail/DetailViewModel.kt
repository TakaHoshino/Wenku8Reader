package com.hoshino.wenku8reader.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.DownloadJob
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import com.hoshino.wenku8reader.ui.common.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = false,
    val book: BookInfo? = null,
    val error: UiText? = null,
    val inLocalLibrary: Boolean = false,
    /**
     * 是否已有阅读进度。由 ViewModel 在 IO 线程读存储后写入，
     * UI 组合期只读 state（此前每次重组都要读一次 SharedPreferences）。
     */
    val hasProgress: Boolean = false,
)

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
    val downloadEngine: DownloadEngine,
    private val libraryStore: LibraryStore,
    private val progressStore: ReadingProgressStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle["id"] ?: 0

    val downloadJobs: StateFlow<Map<Int, DownloadJob>> = downloadEngine.jobs

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    /** Queued favorite feedback messages, shown one after another (never overwritten). */
    private val _favoriteMessages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val favoriteMessages: SharedFlow<UiText> = _favoriteMessages.asSharedFlow()

    init {
        load()
        observeLocalState()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            repository.bookInfo(bookId)
                .onSuccess { book ->
                    _ui.update { cur ->
                        cur.copy(loading = false, book = book, error = null)
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(loading = false, error = e.toUiText())
                    }
                }
        }
    }

    /**
     * 观察「是否已在书架 / 是否有阅读进度」。
     *
     * 旧实现是"进入组合时手动 refreshLocalState() 读一次 SharedPreferences"，从阅读器返回后
     * 全靠那次手动刷新；现在数据库 Flow 会在写入后自动推送，既没有漏刷新的风险，
     * 也不需要 `localFlagsGeneration` 那套"读期间被用户改过就丢弃"的代次防护。
     */
    private fun observeLocalState() {
        viewModelScope.launch {
            combine(
                libraryStore.observeContains(bookId),
                progressStore.observe(bookId),
            ) { inLibrary, progress -> inLibrary to progress.isStarted }
                .collect { (inLocalLibrary, hasProgress) ->
                    _ui.update {
                        it.copy(inLocalLibrary = inLocalLibrary, hasProgress = hasProgress)
                    }
                }
        }
    }

    fun download(format: String, encoding: String = "utf8") {
        val title = _ui.value.book?.title ?: return
        downloadEngine.enqueue(bookId, title, format, encoding)
    }

    // ------------------------------------------------------------------ //
    // favorite (local only)
    // ------------------------------------------------------------------ //
    fun toggleLocalFavorite() {
        val book = _ui.value.book ?: return
        val removing = _ui.value.inLocalLibrary
        viewModelScope.launch {
            // 按钮状态由 `observeLocalState` 的 Flow 驱动，这里只负责写库 + 提示，
            // 不再手工改 state（手改与 Flow 的推送会互相打架）。
            if (removing) {
                libraryStore.remove(bookId)
                _favoriteMessages.tryEmit(UiText.StringResource(R.string.detail_fav_local_removed))
            } else {
                libraryStore.add(book)
                _favoriteMessages.tryEmit(UiText.StringResource(R.string.detail_fav_local_done))
            }
        }
    }
}
