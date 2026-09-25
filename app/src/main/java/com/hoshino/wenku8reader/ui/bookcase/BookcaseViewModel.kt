package com.hoshino.wenku8reader.ui.bookcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.DEFAULT_SHELF
import com.hoshino.wenku8reader.data.local.LibraryBook
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.ReadingProgress
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ShelfStore
import com.hoshino.wenku8reader.data.local.shelfNames
import com.hoshino.wenku8reader.data.local.shelfOf
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
    /** 所属书架；多书架关闭时不用它筛选，但"移动到书架"要知道当前归属。 */
    val shelf: String = DEFAULT_SHELF,
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
)

class BookcaseViewModel(
    private val libraryStore: LibraryStore,
    private val progressStore: ReadingProgressStore,
    private val shelfStore: ShelfStore,
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
        selected: String,
        multiShelf: Boolean,
    ): ShelfView {
        val shelves = shelfNames(customShelves)
        return ShelfView(
            shelves = shelves,
            // 选中的书架可能已被删除（或开关刚被关掉）→ 回落默认，
            // 否则会停在一个永远空的"幽灵书架"上
            selected = if (multiShelf && selected in shelves) selected else DEFAULT_SHELF,
            enabled = multiShelf,
        )
    }

    init {
        // 数据源是数据库 Flow：详情页加/删收藏、阅读器写入进度之后，书架会自动刷新，
        // 不再依赖"重新进入页面时手动 load()"（漏调用就会一直显示过期数据）。
        // 多书架的筛选也放在这条 Flow 链里（而不是让 UI 二次过滤），
        // 这样 loading/error/empty 三态与"当前书架没有书"的判断只有一个来源。
        viewModelScope.launch {
            combine(
                libraryStore.observeAll(),
                progressStore.observeAll(),
                shelfStore.observe(),
                selectedShelfFlow,
                readerSettings.flow.map { it.multiShelfEnabled },
            ) { books, progress, customShelves, selected, multiShelf ->
                shelfView(customShelves, selected, multiShelf) to
                    books.map { it.toEntry(it.shelf, customShelves, progress) }
            }
                .collect { (view, entries) ->
                    natural = entries
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            multiShelfEnabled = view.enabled,
                            shelves = view.shelves,
                            selectedShelf = view.selected,
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
            val progress = progressStore.readAll()
            val customShelves = shelfStore.read()
            val view = shelfView(customShelves, _ui.value.selectedShelf, _ui.value.multiShelfEnabled)
            natural = libraryStore.all()
                .sortedByDescending { it.addedAt }
                .map { it.toEntry(it.shelf, customShelves, progress) }
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

    /** 切换书架（多书架开启时才有调用点）。未知名字忽略，避免选到一个不存在的书架。 */
    fun selectShelf(name: String) {
        if (name !in _ui.value.shelves) return
        selectedShelfFlow.value = name
    }

    /** 把一本书移到另一个书架；Flow 会自动把卡片从当前书架移走（无需手工刷新）。 */
    fun moveToShelf(bookId: Int, shelf: String) {
        viewModelScope.launch { libraryStore.moveToShelf(bookId, shelf) }
    }

    private fun LibraryBook.toEntry(
        rawShelf: String,
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
            // 归属读出来就地归一化：改名残留/历史脏数据一律落到默认书架，
            // 否则那些书会在所有书架里都看不到（"书消失了"）。
            shelf = shelfOf(rawShelf, customShelves),
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
            natural.filter { it.shelf == state.selectedShelf }
        } else {
            natural
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
