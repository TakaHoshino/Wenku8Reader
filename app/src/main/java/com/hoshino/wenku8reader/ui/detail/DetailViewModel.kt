package com.hoshino.wenku8reader.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.DownloadJob
import com.hoshino.wenku8reader.data.Wenku8Shelf
import com.hoshino.wenku8reader.data.local.AccountStore
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.DEFAULT_SHELF
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.data.local.ShelfStore
import com.hoshino.wenku8reader.data.local.shelfNames
import com.hoshino.wenku8reader.data.local.normalizeMembership
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
import kotlinx.coroutines.flow.map
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
    /**
     * 多书架开关 + 书架清单：开启且**尚未收藏**时，点星标先弹"加入哪个书架"。
     * 关闭时这两个字段不参与任何判断，收藏行为与旧版完全一致（直接进默认书架）。
     */
    val multiShelfEnabled: Boolean = false,
    val shelves: List<String> = listOf(DEFAULT_SHELF),
    /**
     * 该书当前所在的书架集合（不在书架里时为空）。
     * 取消收藏的确认弹窗用它预勾选，让用户看清"从哪些书架取消"。
     */
    val currentShelves: Set<String> = emptySet(),
    /** 是否显示"网站书架"入口：账户开 + 多书架开 + 用户账户已登录。 */
    val siteShelfAvailable: Boolean = false,
    /** 这本书当前是否已在站方书架（登录用户的书架）。 */
    val inSiteShelf: Boolean = false,
)

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
    val downloadEngine: DownloadEngine,
    private val libraryStore: LibraryStore,
    private val progressStore: ReadingProgressStore,
    private val shelfStore: ShelfStore,
    private val accountStore: AccountStore,
    private val wenku8Shelf: Wenku8Shelf,
    private val readerSettings: ReaderSettings,
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
        observeSiteShelf()
    }

    /**
     * 站方书架状态（实验性）：是否显示入口、以及这本书是否已在里面。
     *
     * 单独一条 Flow 而不是并进 [observeLocalState]：那条已经有 4 路，再加会超出 `combine`
     * 的类型化重载上限；两者关注点本来也不同（本地书架 vs 远端站方书架）。
     */
    private fun observeSiteShelf() {
        viewModelScope.launch {
            combine(
                accountStore.observe(),
                readerSettings.flow,
                wenku8Shelf.state,
            ) { account, settings, site ->
                (settings.accountLoginEnabled && settings.multiShelfEnabled &&
                    account.activeUsername != null) to site.contains(bookId)
            }.collect { (available, inSite) ->
                _ui.update { it.copy(siteShelfAvailable = available, inSiteShelf = inSite) }
            }
        }
    }

    /** 加入 / 移出站方书架（同一枚按钮的两种含义，由 [DetailUiState.inSiteShelf] 决定）。 */
    fun toggleSiteShelf() {
        viewModelScope.launch {
            if (_ui.value.inSiteShelf) {
                // 移出要的是**书架记录 id**（不是书 id）——在这里换算，UI 不接触站点细节
                val bid = wenku8Shelf.state.value.itemOf(bookId)?.bid ?: return@launch
                val ok = wenku8Shelf.remove(bid)
                _favoriteMessages.tryEmit(
                    UiText.StringResource(
                        if (ok) R.string.detail_site_shelf_removed
                        else R.string.detail_site_shelf_failed,
                    ),
                )
            } else {
                val ok = wenku8Shelf.add(bookId)
                _favoriteMessages.tryEmit(
                    UiText.StringResource(
                        if (ok) R.string.detail_site_shelf_added
                        else R.string.detail_site_shelf_failed,
                    ),
                )
            }
        }
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
                // 用整条条目而不是观察 contains：一次订阅同时拿到"在不在书架"与"在哪个书架"
                libraryStore.observeBook(bookId),
                progressStore.observe(bookId),
                shelfStore.observe(),
                readerSettings.flow.map { it.multiShelfEnabled },
            ) { book, progress, customShelves, multiShelf ->
                LocalState(
                    inLocalLibrary = book != null,
                    hasProgress = progress.isStarted,
                    multiShelfEnabled = multiShelf,
                    shelves = shelfNames(customShelves),
                    currentShelves = book?.let { normalizeMembership(it.shelves, customShelves) }
                        ?: emptySet(),
                )
            }
                .collect { local ->
                    _ui.update {
                        it.copy(
                            inLocalLibrary = local.inLocalLibrary,
                            hasProgress = local.hasProgress,
                            multiShelfEnabled = local.multiShelfEnabled,
                            shelves = local.shelves,
                            currentShelves = local.currentShelves,
                        )
                    }
                }
        }
    }

    private data class LocalState(
        val inLocalLibrary: Boolean,
        val hasProgress: Boolean,
        val multiShelfEnabled: Boolean,
        val shelves: List<String>,
        val currentShelves: Set<String>,
    )

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

    /**
     * 覆盖归属：勾选了哪些书架就属于哪些（多书架开启时由弹层选择，可多选）。
     *
     * 与 [toggleLocalFavorite] 的分工：那个负责"移出/默认收藏"，这个负责"设置所属书架"。
     * 阅读进度不在这里动——它按 bookId 存在 `reading_progress`，同一本书在多个书架共用同一份。
     */
    fun setShelves(shelves: Collection<String>) {
        val book = _ui.value.book ?: return
        viewModelScope.launch {
            libraryStore.add(book, shelves)
            _favoriteMessages.tryEmit(
                // 只勾了默认书架时沿用旧文案，避免"已收藏至「默认」"这种啰嗦说法
                if (shelves.toSet() == setOf(DEFAULT_SHELF)) {
                    UiText.StringResource(R.string.detail_fav_local_done)
                } else {
                    UiText.StringResource(
                        R.string.detail_fav_local_done_shelf,
                        shelves.joinToString("、"),
                    )
                },
            )
        }
    }
}
