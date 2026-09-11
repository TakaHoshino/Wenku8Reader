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
import com.hoshino.wenku8reader.ui.common.toUiText
import com.hoshino.wenku8reader.ui.common.toUiTextOrUnknown
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

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

    /** 当前进行中的标签抓取任务：重试时先取消，避免两轮遍历同时压向站点。 */
    private var tagsJob: Job? = null

    companion object {
        // 内置分类共 50 个，全部展示（与 LightNovelReader 展示 ~48 个分类一致）
        private const val MAX_TAGS = 50

        /**
         * 标签抓取并发度。站点对请求有全局节流（约 600ms/次）并对高频访问返回限流码，
         * 且直连失败后的回退路径会在主线程创建隐藏 WebView，并发过高既无收益又易触发封禁，
         * 因此这里只放到 2（协议约定的上限 3 之内）。
         */
        private const val TAG_FETCH_CONCURRENCY = 2
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
                            homeError = e.toUiText(),
                        )
                    }
                }
        }
    }

    fun loadTags(force: Boolean = false) {
        val state = _ui.value
        if (!force && (state.tagsLoaded || state.tagsLoading)) return
        // 强制刷新（重试）时取消上一轮：否则两轮并发遍历会同时压向站点，
        // 且两次局部刷新互相覆盖，最终列表可能缺项。
        tagsJob?.cancel()
        tagsJob = viewModelScope.launch {
            _ui.update { it.copy(tagsLoading = true, tagsLoaded = false, tagsError = null) }
            val tagsResult = repository.tags()
            val tags = tagsResult.getOrDefault(emptyList()).take(MAX_TAGS)
            if (tagsResult.isFailure) {
                _ui.update {
                    it.copy(
                        tagsLoading = false,
                        tagsLoaded = true,
                        tagsError = tagsResult.exceptionOrNull().toUiTextOrUnknown(),
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
            // 受控并发抓取（原来是 50 个标签串行 await，首屏要等数十秒）。
            // 用 Slot 数组按标签原始下标落位，保证最终/中途的展示顺序都与 tags 一致；
            // 每个标签完成即刻 update 一次状态，配合 LazyColumn 的 tag 作为 key，
            // 只有新出现的行参与组合，不再像之前那样每 4 个全量 toList() 复制。
            val slots = arrayOfNulls<TagSection>(tags.size)
            val slotLock = Mutex()
            coroutineScope {
                val gate = Semaphore(TAG_FETCH_CONCURRENCY)
                tags.forEachIndexed { index, tag ->
                    launch {
                        gate.withPermit {
                            val books = repository.tagBooks(tag).getOrDefault(emptyList()).take(6)
                            if (books.isEmpty()) return@withPermit
                            // 在锁内同时改写槽位并发布状态：发布顺序与槽位写入严格一致，
                            // tagSections 只会单调增长，不会因旧快照后到而"回退"少几行
                            slotLock.withLock {
                                slots[index] = TagSection(tag, books)
                                val snapshot = slots.filterNotNull()
                                _ui.update { it.copy(tagSections = snapshot) }
                            }
                        }
                    }
                }
            }
            _ui.update { it.copy(tagsLoading = false, tagsLoaded = true, tagSections = slots.filterNotNull()) }
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
                            searchError = e.toUiText(),
                        )
                    }
                }
        }
    }
}
