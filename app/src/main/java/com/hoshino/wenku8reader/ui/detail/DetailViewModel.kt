package com.hoshino.wenku8reader.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.DownloadJob
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.LocalLibraryStore
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = false,
    val book: BookInfo? = null,
    val error: UiText? = null,
    val inLocalLibrary: Boolean = false,
)

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
    val downloadEngine: DownloadEngine,
    private val preferences: AppPreferences,
    private val localLibrary: LocalLibraryStore,
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
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            repository.bookInfo(bookId)
                .onSuccess { book ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            book = book,
                            error = null,
                            inLocalLibrary = localLibrary.contains(bookId),
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

    fun download(format: String, encoding: String = "utf8") {
        val title = _ui.value.book?.title ?: return
        downloadEngine.enqueue(bookId, title, format, encoding)
    }

    fun hasProgress(): Boolean = preferences.hasProgress(bookId)

    // ------------------------------------------------------------------ //
    // favorite (local only)
    // ------------------------------------------------------------------ //
    fun toggleLocalFavorite() {
        val book = _ui.value.book ?: return
        if (_ui.value.inLocalLibrary) {
            localLibrary.remove(bookId)
            _ui.update { it.copy(inLocalLibrary = false) }
            _favoriteMessages.tryEmit(UiText.StringResource(R.string.detail_fav_local_removed))
        } else {
            localLibrary.add(book)
            _ui.update { it.copy(inLocalLibrary = true) }
            _favoriteMessages.tryEmit(UiText.StringResource(R.string.detail_fav_local_done))
        }
    }
}
