package com.hoshino.wenku8reader.ui.bookcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.LocalLibraryStore
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val progressPos: Int = 0,
    val progressTotal: Int = 0,
) {
    val progress: Float
        get() = if (progressTotal > 0) {
            ((progressPos + 1).toFloat() / progressTotal).coerceIn(0f, 1f)
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
    private val localLibrary: LocalLibraryStore,
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

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            natural = localLibrary.all()
                .sortedByDescending { it.addedAt }
                .map { lb ->
                    val (pos, total) = preferences.progressPosition(lb.book.id)
                    BookcaseEntry(
                        bookId = lb.book.id,
                        title = lb.book.title,
                        author = lb.book.author,
                        coverUrl = lb.book.coverUrl,
                        status = lb.book.status,
                        lastUpdate = lb.book.lastUpdate,
                        wordCount = parseWordCount(lb.book.wordCount),
                        addedAt = lb.addedAt,
                        progressPos = pos,
                        progressTotal = total,
                    )
                }
            _ui.update { it.copy(isLoading = false, error = null) }
            applySort()
        }
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

    private fun parseWordCount(raw: String): Int {
        val s = raw.trim().uppercase().replace(",", "").replace("，", "")
        val m = Regex("([0-9.]+)\\s*([KM千]|万)?").find(s) ?: return 0
        val num = m.groupValues[1].toDoubleOrNull() ?: return 0
        val mult = when (m.groupValues[2]) {
            "K", "千" -> 1000
            "M" -> 1_000_000
            "万" -> 10_000
            else -> 1
        }
        return (num * mult).toInt()
    }
}
