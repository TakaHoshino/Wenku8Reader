package com.hoshino.wenku8reader.ui.explore

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagBooksUiState(
    val loading: Boolean = true,
    val books: List<HomeBook> = emptyList(),
    val loadingMore: Boolean = false,
    /** 是否还有下一页（false = 已全部加载）。 */
    val hasMore: Boolean = true,
    val error: UiText? = null,
)

/**
 * 标签书籍列表（"查看全部"页）：分页抓取 tags.php?t=xxx&page=N，
 * 逐页追加并按 bookId 去重；下一页为空或没有新书时停止。
 */
class TagBooksViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
) : ViewModel() {

    val tag: String = Uri.decode(savedStateHandle["tag"] ?: "")

    private val _ui = MutableStateFlow(TagBooksUiState())
    val ui: StateFlow<TagBooksUiState> = _ui.asStateFlow()

    private var page = 1

    init {
        load()
    }

    fun load() {
        page = 1
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.tagBooks(tag, page = 1)
                .onSuccess { books ->
                    page = 1
                    _ui.update {
                        it.copy(
                            loading = false,
                            books = books,
                            hasMore = books.isNotEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(loading = false, error = UiText.DynamicString(e.message ?: ""))
                    }
                }
        }
    }

    fun loadMore() {
        if (_ui.value.loading || _ui.value.loadingMore || !_ui.value.hasMore) return
        viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            repository.tagBooks(tag, page = page + 1)
                .onSuccess { next ->
                    val current = _ui.value.books
                    val known = current.mapTo(mutableSetOf()) { it.id }
                    val fresh = next.filter { it.id !in known }
                    page += 1
                    _ui.update {
                        it.copy(
                            loadingMore = false,
                            books = current + fresh,
                            // 空页或下一页没有新书 → 已到底
                            hasMore = next.isNotEmpty() && fresh.isNotEmpty(),
                        )
                    }
                }
                .onFailure {
                    _ui.update { it.copy(loadingMore = false) }
                }
        }
    }
}
