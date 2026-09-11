package com.hoshino.wenku8reader.ui.explore

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import com.hoshino.wenku8reader.ui.common.toUiText
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

    /**
     * 已成功加载的**最后一页**（0 = 尚未加载）。
     * 语义单一的计数器：下一页恒为 [lastLoadedPage] + 1，
     * 避免"已加载页/待加载页"混用造成的偏一（跳过某页 → 书籍缺漏）。
     */
    private var lastLoadedPage = 0

    init {
        load()
    }

    fun load() {
        lastLoadedPage = 0
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            fetchPage(1)
        }
    }

    fun loadMore() {
        val state = _ui.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            fetchPage(lastLoadedPage + 1)
        }
    }

    /** 抓取指定页并追加（按 bookId 去重）。 */
    private suspend fun fetchPage(page: Int) {
        repository.tagBooks(tag, page = page)
            .onSuccess { next ->
                lastLoadedPage = page
                val current = _ui.value.books
                val known = current.mapTo(mutableSetOf()) { it.id }
                val fresh = next.filter { it.id !in known }
                _ui.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        books = current + fresh,
                        // 空页或整页都是重复 → 已到底
                        hasMore = next.isNotEmpty() && fresh.isNotEmpty(),
                    )
                }
            }
            .onFailure { e ->
                _ui.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        // 首屏失败才展示错误；翻页失败静默（用户可继续下滑重试）
                        error = if (page == 1) e.toUiText() else it.error,
                    )
                }
            }
    }
}
