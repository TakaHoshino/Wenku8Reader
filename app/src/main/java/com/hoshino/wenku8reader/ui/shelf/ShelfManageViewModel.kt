package com.hoshino.wenku8reader.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.DEFAULT_SHELF
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.SHELF_NAME_MAX_LENGTH
import com.hoshino.wenku8reader.data.local.ShelfNameError
import com.hoshino.wenku8reader.data.local.ShelfStore
import com.hoshino.wenku8reader.data.local.isShelfDeletable
import com.hoshino.wenku8reader.data.local.normalizeShelfName
import com.hoshino.wenku8reader.data.local.shelfNames
import com.hoshino.wenku8reader.data.local.shelfOf
import com.hoshino.wenku8reader.data.local.validateShelfName
import com.hoshino.wenku8reader.data.local.withShelfCreated
import com.hoshino.wenku8reader.data.local.withShelfDeleted
import com.hoshino.wenku8reader.data.local.withShelfRenamed
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 管理页的一行：书架名 + 书籍数 + 是否允许删除。 */
data class ShelfRow(
    val name: String,
    val bookCount: Int,
    /** 默认书架恒为 false（它不可删、不可改名）。 */
    val deletable: Boolean,
)

/**
 * 正在编辑的对象：新建，或重命名某个已有书架。
 *
 * 两套 UI（Material / MIUIX）共用这个状态，避免各写一份含义相同的枚举。
 */
internal sealed interface ShelfEditorState {
    /** 正在重命名的书架名；null 表示"新建"。 */
    val from: String?

    data object New : ShelfEditorState {
        override val from: String? = null
    }

    data class Rename(override val from: String) : ShelfEditorState
}

data class ShelfManageUiState(
    val rows: List<ShelfRow> = emptyList(),
)

/**
 * 「管理书架」页的 ViewModel——**Material 与 MIUIX 两套页面共用**（本项目双风格约定：
 * UI 代码零复用，但 ViewModel 与数据层共享）。
 *
 * 增删改的顺序与边界都在这里收口：
 * - 重命名要**先改书的归属**（批量 UPDATE `shelf` 列）再改清单，否则那批书会指向
 *   一个不存在的书架；
 * - 删除要**先搬回默认书架**再删清单，顺序反了同样会让书架凭空清空；
 * - 校验统一走 `ShelfOps` 的纯函数（与 UI 侧的即时校验是同一份规则，不会漂移）。
 */
class ShelfManageViewModel(
    private val libraryStore: LibraryStore,
    private val shelfStore: ShelfStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShelfManageUiState())
    val ui: StateFlow<ShelfManageUiState> = _ui.asStateFlow()

    /** 校验失败等提示；由页面用各自风格的组件展示（Material 走 Snackbar，MIUIX 走弹窗）。 */
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(libraryStore.observeAll(), shelfStore.observe()) { books, customShelves ->
                // 书籍数按**归一化后**的归属统计：改名残留/脏数据的书算在默认书架上，
                // 与书架页看到的分布保持一致（否则两个页面的数字会对不上）。
                val counts = books.groupingBy { shelfOf(it.shelf, customShelves) }.eachCount()
                shelfNames(customShelves).map { name ->
                    ShelfRow(
                        name = name,
                        bookCount = counts[name] ?: 0,
                        deletable = isShelfDeletable(name),
                    )
                }
            }.collect { rows -> _ui.update { ShelfManageUiState(rows = rows) } }
        }
    }

    /** 新建书架。 */
    fun create(raw: String) {
        viewModelScope.launch {
            val custom = shelfStore.read()
            validateShelfName(raw, custom)?.let { _messages.tryEmit(it.toUiText()); return@launch }
            shelfStore.replace(withShelfCreated(custom, raw))
        }
    }

    /** 重命名书架（默认书架没有入口，这里也会被 `ShelfOps` 拦下）。 */
    fun rename(from: String, raw: String) {
        viewModelScope.launch {
            val custom = shelfStore.read()
            if (from !in custom) return@launch
            validateShelfName(raw, custom, renaming = from)
                ?.let { _messages.tryEmit(it.toUiText()); return@launch }
            // 顺序：先把书搬到新名字，再改清单。中途失败最坏是"清单还是旧名"（书已改名 →
            // 展示层兜底到默认书架），也不会丢书。
            val target = normalizeShelfName(raw)
            if (target != from) libraryStore.renameShelf(from, target)
            shelfStore.replace(withShelfRenamed(custom, from, raw))
        }
    }

    /** 删除书架：其中的书先搬回默认书架，再移除书架本身。 */
    fun delete(name: String) {
        viewModelScope.launch {
            val custom = shelfStore.read()
            if (!isShelfDeletable(name) || name !in custom) return@launch
            libraryStore.moveShelf(name, DEFAULT_SHELF)
            shelfStore.replace(withShelfDeleted(custom, name))
        }
    }
}

/** 校验错误 → 用户可见文案（与 UI 侧即时校验用的是同一批字符串）。 */
private fun ShelfNameError.toUiText(): UiText = when (this) {
    ShelfNameError.EMPTY -> UiText.StringResource(R.string.shelf_name_error_empty)
    ShelfNameError.TOO_LONG ->
        UiText.StringResource(R.string.shelf_name_error_too_long, SHELF_NAME_MAX_LENGTH)
    ShelfNameError.DUPLICATE -> UiText.StringResource(R.string.shelf_name_error_duplicate)
}
