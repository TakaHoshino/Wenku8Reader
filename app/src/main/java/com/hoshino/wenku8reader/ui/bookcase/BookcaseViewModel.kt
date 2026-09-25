package com.hoshino.wenku8reader.ui.bookcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.LibraryBook
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.ReadingProgress
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.ui.common.UiText
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
)

class BookcaseViewModel(
    private val libraryStore: LibraryStore,
    private val progressStore: ReadingProgressStore,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        BookcaseUiState(
            sortType = BookcaseSortType.map(preferences.bookcaseSortType),
            sortReversed = preferences.bookcaseSortReversed,
        )
    )
    val ui: StateFlow<BookcaseUiState> = _ui.asStateFlow()

    private var natural: List<BookcaseEntry> = emptyList()

    init {
        // 数据源是数据库 Flow：详情页加/删收藏、阅读器写入进度之后，书架会自动刷新，
        // 不再依赖"重新进入页面时手动 load()"（漏调用就会一直显示过期数据）。
        viewModelScope.launch {
            combine(
                libraryStore.observeAll(),
                progressStore.observeAll(),
            ) { books, progress -> books.map { it.toEntry(progress[it.book.id]) } }
                .collect { entries ->
                    natural = entries
                    _ui.update { it.copy(isLoading = false, error = null) }
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
            natural = libraryStore.all()
                .sortedByDescending { it.addedAt }
                .map { it.toEntry(progress[it.book.id]) }
            _ui.update { it.copy(isLoading = false, error = null) }
            applySort()
        }
    }

    private fun LibraryBook.toEntry(progress: ReadingProgress?): BookcaseEntry = BookcaseEntry(
        bookId = book.id,
        title = book.title,
        author = book.author,
        coverUrl = book.coverUrl,
        status = book.status,
        lastUpdate = book.lastUpdate,
        wordCount = parseWordCount(book.wordCount),
        addedAt = addedAt,
        progressTotal = progress?.totalChapters ?: 0,
        readCount = progress?.finishedCids?.size ?: 0,
    )

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
        val sorted = sortEntries(natural, state.sortType, state.sortReversed)
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
