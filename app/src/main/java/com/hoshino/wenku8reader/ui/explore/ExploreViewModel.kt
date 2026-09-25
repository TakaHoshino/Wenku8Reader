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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** 全部标签名（内置清单，秒回）；每行的书籍预览按需加载，见 [tagBooks]。 */
    val tags: List<String> = emptyList(),
    /** 已加载的标签预览：tag → 前若干本书。 */
    val tagBooks: Map<String, List<HomeBook>> = emptyMap(),
    /** 正在加载预览的标签（用于占位与去重）。 */
    val loadingTags: Set<String> = emptySet(),
    /**
     * 预览加载失败的标签。
     *
     * 必须单独记一份：以前失败被压成空列表写进 [tagBooks]，于是"加载失败"和
     * "这个分类确实没有书"在界面上完全一样，而且 [tagBooks] 已有该 key 会让
     * 后续滚动回来也**不再重试**——用户看到的是永久空白。现在失败不留空列表、
     * 该行显示可点重试，重试成功后错误标记被清掉。
     */
    val tagPreviewErrors: Set<String> = emptySet(),
    /** 强制刷新计数：可见行以它为 key 重新触发加载（见 TagsBody）。 */
    val tagsGeneration: Int = 0,
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

        /** 每个标签行展示的预览书籍数量。 */
        private const val TAG_PREVIEW_COUNT = 6
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
        // 强制刷新（重试）时取消上一轮清单请求，并清掉已缓存的预览：
        // generation 自增会让可见行的 LaunchedEffect 重新触发加载，
        // 不必再靠"重跑全量遍历"来刷新。
        tagsJob?.cancel()
        tagsJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    tagsLoading = true,
                    tagsLoaded = false,
                    tagsError = null,
                    tagBooks = if (force) emptyMap() else it.tagBooks,
                    loadingTags = if (force) emptySet() else it.loadingTags,
                    tagsGeneration = if (force) it.tagsGeneration + 1 else it.tagsGeneration,
                )
            }
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
            // 到这里只准备"标签清单"（内置常量，无网络请求）。
            // 各标签的书籍预览改为按需加载（见 loadTagPreview）——
            // 原来一进标签页就 50 个标签全量抓取：请求数是浏览行为的 10 倍以上，
            // 还会把站点限流（约 600ms/次）摊到"用户根本没看"的分类上。
            _ui.update { it.copy(tagsLoading = false, tagsLoaded = true, tags = tags) }
        }
    }

    /**
     * 按需加载单个标签的书籍预览（由标签行进入可见区域时触发）。
     *
     * 并发度天然受"同时可见的行数"约束（一屏 3~4 行），无需再自建信号量；
     * 已加载或正在加载的标签直接返回，滚动来回不会重复请求。
     *
     * [force] 供"加载失败后点重试"使用：失败的行不会随滚动自动重试
     * （否则一个持续失败的分类会随滚动反复压向站点）。
     */
    fun loadTagPreview(tag: String, force: Boolean = false) {
        val state = _ui.value
        if (tag in state.loadingTags) return
        if (!force && (state.tagBooks.containsKey(tag) || tag in state.tagPreviewErrors)) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loadingTags = it.loadingTags + tag,
                    tagPreviewErrors = it.tagPreviewErrors - tag,
                )
            }
            val result = repository.tagBooks(tag)
            _ui.update {
                if (result.isFailure) {
                    it.copy(
                        loadingTags = it.loadingTags - tag,
                        tagPreviewErrors = it.tagPreviewErrors + tag,
                        // 刻意不写入空列表：留条重试的路（见 tagPreviewErrors 的说明）
                        tagBooks = it.tagBooks - tag,
                    )
                } else {
                    it.copy(
                        loadingTags = it.loadingTags - tag,
                        tagBooks = it.tagBooks + (
                            tag to result.getOrDefault(emptyList()).take(TAG_PREVIEW_COUNT)
                            ),
                    )
                }
            }
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
