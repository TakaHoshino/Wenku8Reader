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
    val error: UiText? = null,
)

class TagBooksViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
) : ViewModel() {

    val tag: String = Uri.decode(savedStateHandle["tag"] ?: "")

    private val _ui = MutableStateFlow(TagBooksUiState())
    val ui: StateFlow<TagBooksUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.tagBooks(tag)
                .onSuccess { books ->
                    _ui.update { it.copy(loading = false, books = books) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(loading = false, error = UiText.DynamicString(e.message ?: ""))
                    }
                }
        }
    }
}
