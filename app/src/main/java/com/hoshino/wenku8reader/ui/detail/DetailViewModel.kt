package com.hoshino.wenku8reader.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.DownloadJob
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.LocalLibraryStore
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import com.hoshino.wenku8reader.ui.common.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val preferences: AppPreferences,
    private val localLibrary: LocalLibraryStore,
) : ViewModel() {

    val bookId: Int = savedStateHandle["id"] ?: 0

    val downloadJobs: StateFlow<Map<Int, DownloadJob>> = downloadEngine.jobs

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    /** Queued favorite feedback messages, shown one after another (never overwritten). */
    private val _favoriteMessages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val favoriteMessages: SharedFlow<UiText> = _favoriteMessages.asSharedFlow()

    /**
     * 本地状态被用户操作（加入/移出书架）修改的次数。加载/刷新协程在读取存储前记下代次，
     * 若读取期间发生过用户操作，就丢弃这次可能已过期的结果，
     * 避免把刚点出来的书架状态又改回去。
     */
    private var localFlagsGeneration = 0

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            repository.bookInfo(bookId)
                .onSuccess { book ->
                    val generation = localFlagsGeneration
                    val local = readLocalFlags()
                    _ui.update { cur ->
                        val fresh = generation == localFlagsGeneration
                        cur.copy(
                            loading = false,
                            book = book,
                            error = null,
                            inLocalLibrary = if (fresh) local.first else cur.inLocalLibrary,
                            hasProgress = if (fresh) local.second else cur.hasProgress,
                        )
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
     * 重新读取「是否已在书架 / 是否有阅读进度」并写回 [DetailUiState]。
     * 详情页（含从阅读器返回后）重新进入组合时调用：这段时间进度可能已变化，
     * 而组合期已不再直接访问存储，需要靠这里刷新，保证按钮文案即时正确。
     */
    fun refreshLocalState() {
        val generation = localFlagsGeneration
        viewModelScope.launch {
            val local = readLocalFlags()
            if (generation == localFlagsGeneration) {
                _ui.update { it.copy(inLocalLibrary = local.first, hasProgress = local.second) }
            }
        }
    }

    /**
     * 存储读取（[LocalLibraryStore.contains] 会全量 JSON 解析 + [AppPreferences.hasProgress]
     * 读 SharedPreferences）一律切到 [Dispatchers.IO]：
     * `viewModelScope` 默认跑在主线程调度器上，直接调用会阻塞首帧。
     */
    private suspend fun readLocalFlags(): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        localLibrary.contains(bookId) to preferences.hasProgress(bookId)
    }

    fun download(format: String, encoding: String = "utf8") {
        val title = _ui.value.book?.title ?: return
        downloadEngine.enqueue(bookId, title, format, encoding)
    }

    /**
     * 是否已有阅读进度。读取自 [DetailUiState]（存储访问已在 IO 线程完成），
     * 组合期调用它也不会触发磁盘 I/O。
     */
    fun hasProgress(): Boolean = _ui.value.hasProgress

    // ------------------------------------------------------------------ //
    // favorite (local only)
    // ------------------------------------------------------------------ //
    fun toggleLocalFavorite() {
        val book = _ui.value.book ?: return
        // 这里保持同步写：书架是「读出 map → 改 → 整份写回」的读改写操作，
        // 丢到协程里并发执行会互相覆盖；单次写入量很小，且同步更新 state 才能保证按钮即时刷新。
        localFlagsGeneration++
        if (_ui.value.inLocalLibrary) {
            localLibrary.remove(bookId)
            _ui.update { it.copy(inLocalLibrary = false) }
            _favoriteMessages.tryEmit(UiText.StringResource(R.string.detail_fav_local_removed))
        } else {
            localLibrary.add(book)
            _ui.update { it.copy(inLocalLibrary = true) }
            _favoriteMessages.tryEmit(UiText.StringResource(R.string.detail_fav_local_done))
        }
    }
}
