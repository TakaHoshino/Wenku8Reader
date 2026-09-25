package com.hoshino.wenku8reader.ui.bookcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.Wenku8Shelf
import com.hoshino.wenku8reader.data.local.AccountStore
import com.hoshino.wenku8reader.data.local.DEFAULT_SHELF
import com.hoshino.wenku8reader.data.local.WENKU8_SHELF
import com.hoshino.wenku8reader.data.local.wenku8ShelfVisible
import com.hoshino.wenku8reader.data.local.LibraryBook
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.ReadingProgress
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ShelfStore
import com.hoshino.wenku8reader.data.local.shelfNames
import com.hoshino.wenku8reader.data.local.normalizeMembership
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

enum class BookcaseSortType(val key: String, val labelRes: Int) {
    DEFAULT("default", R.string.bookcase_sort_default),
    LATEST("latest", R.string.bookcase_sort_latest),
    NAME("name", R.string.bookcase_sort_name),
    WORD_COUNT("word_count", R.string.bookcase_sort_word_count);

    companion object {
        fun map(key: String): BookcaseSortType =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Unified display entry for the on-device bookshelf. */
data class BookcaseEntry(
    val bookId: Int,
    val title: String,
    val author: String = "",
    val coverUrl: String? = null,
    val status: String = "",
    val lastUpdate: String = "",
    val wordCount: Int = 0,
    val addedAt: Long = 0L,
    val progressTotal: Int = 0,
    /** 已读章节数：目录页标记"已读"的章节（finishedChapters）数量。 */
    val readCount: Int = 0,
    /** 所属书架集合（一本书可同时在多个书架）；多书架关闭时不用它筛选。 */
    val shelves: Set<String> = setOf(DEFAULT_SHELF),
) {
    /** 已读进度 = 已读章节数 / 总章节数（基于目录"已读"标记，非阅读位置）。 */
    val progress: Float
        get() = if (progressTotal > 0) {
            (readCount.toFloat() / progressTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class BookcaseUiState(
    val sortType: BookcaseSortType = BookcaseSortType.DEFAULT,
    val sortReversed: Boolean = false,
    val isLoading: Boolean = false,
    val entries: List<BookcaseEntry> = emptyList(),
    val error: UiText? = null,
    /**
     * 多书架是否开启。关闭时 [entries] 与当前版本完全一致（整库、不筛选），
     * UI 也不显示切换条与"管理书架"入口。
     */
    val multiShelfEnabled: Boolean = false,
    /** 完整书架清单（含隐式默认，默认恒在首位）。 */
    val shelves: List<String> = listOf(DEFAULT_SHELF),
    /** 当前选中的书架；开关关闭时恒为默认。 */
    val selectedShelf: String = DEFAULT_SHELF,
    /** 当前选中的是站方虚拟书架（内容来自站点，不是本地书架）。 */
    val siteShelf: Boolean = false,
)

class BookcaseViewModel(
    private val libraryStore: LibraryStore,
    private val progressStore: ReadingProgressStore,
    private val shelfStore: ShelfStore,
    private val accountStore: AccountStore,
    private val wenku8Shelf: Wenku8Shelf,
    private val readerSettings: ReaderSettings,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        BookcaseUiState(
            sortType = BookcaseSortType.map(preferences.bookcaseSortType),
            sortReversed = preferences.bookcaseSortReversed,
        )
    )
    val ui: StateFlow<BookcaseUiState> = _ui.asStateFlow()

    /**
     * 当前选中的书架。**只存在内存里**，不持久化：重启回到默认书架更符合直觉，
     * 也少一个需要跟着书架清单一起维护/迁移的键。
     */
    private val selectedShelfFlow = MutableStateFlow(DEFAULT_SHELF)

    /** 整库条目（未按书架筛选）；筛选与排序都在 [applySort] 里统一做。 */
    private var natural: List<BookcaseEntry> = emptyList()

    /** 多书架视图状态（由 [shelfView] 统一算出，Flow 与一次性读取共用）。 */
    private data class ShelfView(
        val shelves: List<String>,
        val selected: String,
        val enabled: Boolean,
    )

    private fun shelfView(
        customShelves: List<String>,
        env: Env,
    ): ShelfView {
        // Wenku8 书架是**虚拟书架**（不在 ShelfStore 清单里、不进 LibraryStore），
        // 只有"账户开关 + 多书架开关 + 用户账户已登录"三者齐备时才出现在切换条末尾。
        val siteShelf = wenku8ShelfVisible(
            accountLoginEnabled = env.accountLoginEnabled,
            multiShelfEnabled = env.multiShelfEnabled,
            userAccountLoggedIn = env.activeUsername != null,
        )
        val shelves = shelfNames(customShelves) + if (siteShelf) listOf(WENKU8_SHELF) else emptyList()
        return ShelfView(
            shelves = shelves,
            // 选中的书架可能已被删除 / 开关刚被关掉 / 退出登录了 → 回落默认，
            // 否则会停在一个永远空的"幽灵书架"上
            selected = when {
                env.selected == WENKU8_SHELF && siteShelf -> WENKU8_SHELF
                env.multiShelfEnabled && env.selected in shelves -> env.selected
                else -> DEFAULT_SHELF
            },
            enabled = env.multiShelfEnabled,
        )
    }

    /** Flow 链需要的账户/站点环境（单独 combine 一层，避免主 combine 超出 5 路上限）。 */
    private data class Env(
        val selected: String,
        val multiShelfEnabled: Boolean,
        val accountLoginEnabled: Boolean,
        val activeUsername: String?,
        val site: Wenku8Shelf.State,
    )

    init {
        // 数据源是数据库 Flow：详情页加/删收藏、阅读器写入进度之后，书架会自动刷新，
        // 不再依赖"重新进入页面时手动 load()"（漏调用就会一直显示过期数据）。
        // 多书架的筛选也放在这条 Flow 链里（而不是让 UI 二次过滤），
        // 这样 loading/error/empty 三态与"当前书架没有书"的判断只有一个来源。
        viewModelScope.launch {
            val envFlow = combine(
                selectedShelfFlow,
                readerSettings.flow,
                accountStore.observe(),
                wenku8Shelf.state,
            ) { selected, settings, account, site ->
                Env(
                    selected = selected,
                    multiShelfEnabled = settings.multiShelfEnabled,
                    accountLoginEnabled = settings.accountLoginEnabled,
                    activeUsername = account.activeUsername,
                    site = site,
                )
            }
            combine(
                libraryStore.observeAll(),
                progressStore.observeAll(),
                shelfStore.observe(),
                envFlow,
            ) { books, progress, customShelves, env ->
                val view = shelfView(customShelves, env)
                Triple(
                    view,
                    // 选中虚拟书架时，内容是**站方**书架的条目（站点只给书名/最新章，
                    // 没有封面作者，也绝不为此逐本并发拉 bookInfo）
                    if (view.selected == WENKU8_SHELF) env.site.items.map { it.toSiteEntry() }
                    else books.map { it.toEntry(customShelves, progress) },
                    env.site,
                )
            }
                .collect { (view, entries, site) ->
                    natural = entries
                    val siteSelected = view.selected == WENKU8_SHELF
                    _ui.update {
                        it.copy(
                            isLoading = siteSelected && site.loading,
                            error = if (siteSelected && site.error != null) {
                                UiText.StringResource(R.string.wenku8_shelf_load_failed)
                            } else {
                                null
                            },
                            multiShelfEnabled = view.enabled,
                            shelves = view.shelves,
                            selectedShelf = view.selected,
                            siteShelf = siteSelected,
                        )
                    }
                    applySort()
                }
        }
    }

    /**
     * 下拉刷新 / 错误重试：数据本来由 Flow 持续驱动，这里重读一次只是让刷新手势有即时反馈
     * （并顺带等待一次性搬迁完成）。
     */
    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            if (_ui.value.siteShelf) {
                // 站方书架只能现场拉；loading/error 由 Wenku8Shelf 的状态驱动，这里直接返回
                wenku8Shelf.refresh()
                return@launch
            }
            val progress = progressStore.readAll()
            val customShelves = shelfStore.read()
            val view = shelfView(customShelves, currentEnv())
            natural = libraryStore.all()
                .sortedByDescending { it.addedAt }
                .map { it.toEntry(customShelves, progress) }
            // 注意不在这里写 multiShelfEnabled：开关的唯一来源是 ReaderSettings 那路 Flow，
            // 抢着写会让开关短暂闪回 false（下拉刷新时表现为切换条忽隐忽现）。
            _ui.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    shelves = view.shelves,
                    selectedShelf = view.selected,
                )
            }
            applySort()
        }
    }

    /** 一次性读取当前环境（下拉刷新路径用；Flow 路径由 combine 提供）。 */
    private suspend fun currentEnv(): Env = Env(
        selected = selectedShelfFlow.value,
        multiShelfEnabled = readerSettings.flow.value.multiShelfEnabled,
        accountLoginEnabled = readerSettings.flow.value.accountLoginEnabled,
        activeUsername = accountStore.read().activeUsername,
        site = wenku8Shelf.state.value,
    )

    /** 切换书架（多书架开启时才有调用点）。未知名字忽略，避免选到一个不存在的书架。 */
    fun selectShelf(name: String) {
        if (name !in _ui.value.shelves) return
        selectedShelfFlow.value = name
        // 站方书架不落库、只能现场拉：选中它时刷新一次（1 个请求，带互斥）
        if (name == WENKU8_SHELF) viewModelScope.launch { wenku8Shelf.refresh() }
    }

    /**
     * 覆盖一本书的归属（长按卡片 → 勾选所属书架）。
     *
     * 勾选变化后 Flow 会自动重画：从当前书架取消勾选，卡片就从这一栏消失（无需手工刷新）。
     */
    fun setShelves(bookId: Int, shelves: Collection<String>) {
        viewModelScope.launch { libraryStore.setShelves(bookId, shelves) }
    }

    /**
     * 从站方书架移出一本（长按站方条目 → 确认）。
     *
     * 入参是**书 id**（卡片上的 bookId）；站点的移出接口要的是**书架记录 id**（`bid`），
     * 两者不相等，所以在状态里查一下再传——不让 UI 层接触这个站点细节。
     */
    fun removeFromSiteShelf(bookId: Int) {
        viewModelScope.launch {
            val bid = wenku8Shelf.state.value.itemOf(bookId)?.bid ?: return@launch
            wenku8Shelf.remove(bid)
        }
    }

    private fun LibraryBook.toEntry(
        customShelves: List<String>,
        progress: Map<Int, ReadingProgress>,
    ): BookcaseEntry {
        val p = progress[book.id]
        return BookcaseEntry(
            bookId = book.id,
            title = book.title,
            author = book.author,
            coverUrl = book.coverUrl,
            status = book.status,
            lastUpdate = book.lastUpdate,
            wordCount = parseWordCount(book.wordCount),
            addedAt = addedAt,
            progressTotal = p?.totalChapters ?: 0,
            readCount = p?.finishedCids?.size ?: 0,
            // 归属读出来就地归一化：已不存在的书架名一律丢弃、摘空则落回默认书架，
            // 否则那些书会在所有书架里都看不到（"书消失了"）。
            shelves = normalizeMembership(shelves, customShelves),
        )
    }

    fun setSortType(type: BookcaseSortType) {
        preferences.bookcaseSortType = type.key
        _ui.update { it.copy(sortType = type) }
        applySort()
    }

    fun setSortReversed(reversed: Boolean) {
        preferences.bookcaseSortReversed = reversed
        _ui.update { it.copy(sortReversed = reversed) }
        applySort()
    }

    private fun applySort() {
        val state = _ui.value
        // 关闭多书架时**不筛选**（整库视图，与改动前逐像素一致）；
        // 开启时按当前书架筛。筛选只此一处，避免 UI 层再过滤一次导致三态错乱。
        val visible = if (state.multiShelfEnabled) {
            // 多选归属：只要这本书属于当前书架就显示它
            natural.filter { state.selectedShelf in it.shelves }
        } else {
            natural
        }
        // 虚拟书架保持站点返回的顺序（站点按最近更新排），不套用本地排序——站方条目没有
        // 本地排序依赖的字段（更新时间/字数），硬排只会得到毫无意义的顺序。
        if (state.siteShelf) {
            _ui.update { it.copy(entries = visible) }
            return
        }
        val sorted = sortEntries(visible, state.sortType, state.sortReversed)
        _ui.update { it.copy(entries = sorted) }
    }

    private fun sortEntries(
        source: List<BookcaseEntry>,
        type: BookcaseSortType,
        reversed: Boolean,
    ): List<BookcaseEntry> {
        if (type == BookcaseSortType.DEFAULT) return source
        val collator = Collator.getInstance(Locale.CHINA)
        val sorted = when (type) {
            BookcaseSortType.LATEST ->
                source.sortedWith(
                    compareByDescending<BookcaseEntry> { it.lastUpdate }
                        .thenBy { it.bookId }
                )
            BookcaseSortType.NAME ->
                source.sortedWith { a, b -> collator.compare(a.title, b.title) }
            BookcaseSortType.WORD_COUNT ->
                source.sortedWith(
                    compareByDescending<BookcaseEntry> { it.wordCount }
                        .thenBy { it.bookId }
                )
            BookcaseSortType.DEFAULT -> source
        }
        return if (reversed) sorted.reversed() else sorted
    }

}
