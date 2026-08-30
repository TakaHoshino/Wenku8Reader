package com.hoshino.wenku8reader.ui.author

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthorBooksUiState(
    val loading: Boolean = true,
    val books: List<SearchResult> = emptyList(),
    val error: UiText? = null,
)

/** 作者书籍列表页：复用站内"按作者搜索"接口，展示该作者全部作品。 */
class AuthorBooksViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
) : ViewModel() {

    val authorName: String = savedStateHandle["name"] ?: ""

    private val _ui = MutableStateFlow(AuthorBooksUiState())
    val ui: StateFlow<AuthorBooksUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (authorName.isBlank()) {
            _ui.update { it.copy(loading = false, books = emptyList()) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.search(authorName, byAuthor = true)
                .onSuccess { books -> _ui.update { it.copy(loading = false, books = books) } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            error = UiText.DynamicString(e.message ?: ""),
                        )
                    }
                }
        }
    }
}
