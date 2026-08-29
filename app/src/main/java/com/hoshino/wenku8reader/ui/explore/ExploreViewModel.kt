package com.hoshino.wenku8reader.ui.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.HomeBook
import com.hoshino.wenku8reader.data.HomeSection
import com.hoshino.wenku8reader.data.SearchResult
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A tag row on the Explore page: the tag name plus its recommended books. */
@Immutable
data class TagSection(val tag: String, val books: List<HomeBook>)

/** Explore sub-tabs: recommendations (home + search) vs the tag browser. */
enum class ExploreMode { RECOMMEND, TAGS }

/** Combined state for the Explore tab: browsing sections + search results. */
@Immutable
data class ExploreUiState(
    val homeLoading: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val homeError: UiText? = null,
    val searching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val searchError: UiText? = null,
    val tagsLoading: Boolean = false,
    val tagsLoaded: Boolean = false,
    val tagSections: List<TagSection> = emptyList(),
    val tagsError: UiText? = null,
)

class ExploreViewModel(private val repository: Wenku8Repository) : ViewModel() {

    private val _ui = MutableStateFlow(ExploreUiState())
    val ui: StateFlow<ExploreUiState> = _ui.asStateFlow()

    private var homeLoaded = false

    companion object {
        private const val MAX_TAGS = 20
    }

    fun loadHomeOnce() {
        if (homeLoaded || _ui.value.homeLoading) return
        refreshHome()
    }

    fun refreshHome() {
        viewModelScope.launch {
            _ui.update { it.copy(homeLoading = true, homeError = null) }
            repository.homepage()
                .onSuccess { sections ->
                    homeLoaded = true
                    _ui.update { it.copy(homeLoading = false, sections = sections) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            homeLoading = false,
                            homeError = UiText.DynamicString(e.message ?: ""),
                        )
                    }
                }
        }
    }

    fun loadTags(force: Boolean = false) {
        val state = _ui.value
        if (!force && (state.tagsLoaded || state.tagsLoading)) return
        viewModelScope.launch {
            _ui.update { it.copy(tagsLoading = true, tagsLoaded = false, tagsError = null) }
            val tagsResult = repository.tags()
            val tags = tagsResult.getOrDefault(emptyList()).take(MAX_TAGS)
            if (tagsResult.isFailure) {
                _ui.update {
                    it.copy(
                        tagsLoading = false,
                        tagsLoaded = true,
                        tagsError = UiText.DynamicString(
                            tagsResult.exceptionOrNull()?.message ?: "",
                        ),
                    )
                }
                return@launch
            }
            if (tags.isEmpty()) {
                _ui.update {
                    it.copy(
                        tagsLoading = false,
                        tagsLoaded = true,
                        tagsError = UiText.StringResource(R.string.explore_tags_blocked),
                    )
                }
                return@launch
            }
            val sections = mutableListOf<TagSection>()
            for (tag in tags) {
                val books = repository.tagBooks(tag).getOrDefault(emptyList()).take(6)
                if (books.isNotEmpty()) {
                    sections.add(TagSection(tag, books))
                    // 批量（每 4 个）再更新一次列表，避免每个标签都触发整列重组
                    if (sections.size % 4 == 0) {
                        _ui.update { it.copy(tagSections = sections.toList()) }
                    }
                }
            }
            _ui.update { it.copy(tagsLoading = false, tagsLoaded = true, tagSections = sections.toList()) }
        }
    }

    fun search(keyword: String, byAuthor: Boolean) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(searching = true, searchError = null) }
            repository.search(query, byAuthor)
                .onSuccess { results ->
                    _ui.update { it.copy(searching = false, results = results) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            searching = false,
                            searchError = UiText.DynamicString(e.message ?: ""),
                        )
                    }
                }
        }
    }
}
