package com.hoshino.wenku8reader.ui.toc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TocUiState(
    val loading: Boolean = false,
    val title: String = "",
    val volumes: List<Volume> = emptyList(),
    /** 已完成的章节 cid 集合（灰色 + 已读）。 */
    val finished: Set<String> = emptySet(),
    /** 当前阅读中的章节 cid（高亮）。 */
    val currentCid: String? = null,
    /** 折叠状态的卷名集合。 */
    val collapsedVolumes: Set<String> = emptySet(),
    val error: UiText? = null,
)

/**
 * 目录页：分卷可折叠列表 + 章节已读状态 + 重读重置数据源。
 * 默认所有卷展开；某卷全部章节已读则首次加载自动折叠。
 */
class TocViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: Wenku8Repository,
    private val preferences: AppPreferences,
) : ViewModel() {

    val bookId: Int = savedStateHandle["id"] ?: 0

    private val _ui = MutableStateFlow(TocUiState(loading = true))
    val ui: StateFlow<TocUiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val info = repository.bookInfo(bookId).getOrNull()
            if (info == null) {
                _ui.update {
                    it.copy(
                        loading = false,
                        error = UiText.StringResource(R.string.error_chapter_index),
                    )
                }
                return@launch
            }
            // gid 统一走 repository.groupIdOf（缺字段时回退 id/1000）：
            // 此前直接用 info.groupId，为 null 时整个目录页必然报"章节索引加载失败"，
            // 而 Reader / DownloadEngine 走的是有回退的 groupIdOf，行为不一致。
            val gid = repository.groupIdOf(info)
            val vols = repository.chapters(bookId, gid).getOrElse { emptyList() }
            if (vols.isEmpty()) {
                _ui.update {
                    it.copy(
                        loading = false,
                        title = info.title,
                        error = UiText.StringResource(R.string.error_chapter_index),
                    )
                }
                return@launch
            }
            val finished = preferences.finishedChapters(bookId)
            val current = preferences.resumeCid(bookId)
            // 默认全部展开；全卷章节都已读 → 首次加载自动折叠
            val collapsed = vols
                .filter { v -> v.chapters.isNotEmpty() && v.chapters.all { it.cid in finished } }
                .mapTo(mutableSetOf()) { it.name }
            _ui.update {
                it.copy(
                    loading = false,
                    title = info.title,
                    volumes = vols,
                    finished = finished,
                    currentCid = current,
                    collapsedVolumes = collapsed,
                )
            }
        }
    }

    fun toggleVolume(name: String) {
        _ui.update { s ->
            s.copy(
                collapsedVolumes = if (name in s.collapsedVolumes) s.collapsedVolumes - name
                else s.collapsedVolumes + name,
            )
        }
    }

    fun expandAll() = _ui.update { it.copy(collapsedVolumes = emptySet()) }

    fun collapseAll() = _ui.update { it.copy(collapsedVolumes = it.volumes.mapTo(mutableSetOf()) { v -> v.name }) }
}
