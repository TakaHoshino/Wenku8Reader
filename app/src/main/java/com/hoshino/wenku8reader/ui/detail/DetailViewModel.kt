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
    /**
     * 「Wenku8书架」是否可用：账户开 + 多书架开 + 用户账户已登录。
     * 决定收藏弹窗里要不要多出那个复选框（站方书架**没有**自己的按钮）。
     */
    val siteShelfAvailable: Boolean = false,
    /**
     * 这本书当前是否已在「Wenku8书架」里。
     *
     * **只在 [siteShelfAvailable] 为真时才有意义**：退出登录 / 关掉开关后站点镜像可能还没刷新，
     * 这里一律按"不在"处理，避免把上一个账户的书架状态显示给现在的用户。
     */
    val inSiteShelf: Boolean = false,
) {
    /**
     * 星标是否点亮：本地收藏**或**在站方书架里。
     *
     * 站方书架现在是收藏弹窗里的一个复选框，如果星标只看本地，那本"只加进了网站书架"的书
     * 会显示成未收藏，用户点开弹窗却看到一个已勾选的项——所以两种归属都要点亮星标。
     * 站方不可用时（未登录 / 开关关闭）它退化成原来的 `inLocalLibrary`。
     */
    val favoriteActive: Boolean get() = inLocalLibrary || inSiteShelf
}

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
                // 不可用时一律记 false：站点镜像里可能还留着上一个账户的书，别显示出来
                _ui.update { it.copy(siteShelfAvailable = available, inSiteShelf = available && inSite) }
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
    // favorite（本地归属 + 站方书架：同一个入口、同一个弹窗）
    // ------------------------------------------------------------------ //

    /** 多书架**关闭**时的收藏：点一下即刻收藏/移出（与多书架上线前逐像素一致）。 */
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
     * 收藏弹窗的落点：**本地归属与站方书架一次提交**。
     *
     * 两者在数据层仍是独立状态（站方书架不进 `LibraryStore`），只是共用一个入口——
     * 详情页只有星标一枚按钮，弹窗里「Wenku8书架」和本地书架一样是个复选框。
     *
     * - 本地：勾选了哪些书架就属于哪些；**一个都没勾 = 取消本地收藏**（沿用原来的语义）。
     * - 站方：勾上 → `addbookcase.php`（加入），取消勾选 → `bookcase.php?delid=`（移出），
     *   两者都是**真实的网络请求**，随后刷新站方书架，让勾选状态回到事实而不是本地猜测。
     *
     * 只有确实变化的项才会写库 / 发请求：打开弹窗直接点确定不会产生无意义的写库与网络请求。
     * 阅读进度不在这里动——它按 bookId 存在 `reading_progress`，同一本书在多个书架共用同一份。
     */
    fun applyFavorite(shelves: Collection<String>, inSiteShelf: Boolean) {
        val book = _ui.value.book ?: return
        viewModelScope.launch {
            val state = _ui.value
            val target = shelves.toSet()
            val localChanged = target != state.currentShelves
            if (localChanged) {
                if (target.isEmpty()) libraryStore.remove(book.id) else libraryStore.add(book, target)
            }

            val siteChanged = state.siteShelfAvailable && inSiteShelf != state.inSiteShelf
            val siteOk = if (siteChanged) setSiteShelf(inSiteShelf) else null

            // 一次只提示一条：站方失败优先报出来，否则本地那条成功提示会掩盖"网站书架其实没动"
            _favoriteMessages.tryEmit(
                when {
                    siteOk == false -> UiText.StringResource(R.string.detail_site_shelf_failed)
                    localChanged -> localFavoriteMessage(target)
                    siteOk == true -> UiText.StringResource(
                        if (inSiteShelf) R.string.detail_site_shelf_added
                        else R.string.detail_site_shelf_removed,
                    )
                    else -> return@launch
                },
            )
        }
    }

    /**
     * 站方书架的加入 / 移出（**发网络请求**）。
     *
     * @return 是否成功；调用方只在"勾选状态确实变了"时才会调它。
     */
    private suspend fun setSiteShelf(inSiteShelf: Boolean): Boolean = if (inSiteShelf) {
        wenku8Shelf.add(bookId)
    } else {
        // 移出要的是**书架记录 id**（不是书 id）——在这里换算，UI 不接触站点细节
        val bid = wenku8Shelf.state.value.itemOf(bookId)?.bid ?: return false
        wenku8Shelf.remove(bid)
    }

    /** 本地归属变化的提示文案（只勾了默认书架时沿用旧文案，避免"已收藏至「默认」"这种啰嗦说法）。 */
    private fun localFavoriteMessage(target: Set<String>): UiText = when {
        target.isEmpty() -> UiText.StringResource(R.string.detail_fav_local_removed)
        target == setOf(DEFAULT_SHELF) -> UiText.StringResource(R.string.detail_fav_local_done)
        else -> UiText.StringResource(R.string.detail_fav_local_done_shelf, target.joinToString("、"))
    }
}
