package com.hoshino.wenku8reader.data

import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 站方书架（Wenku8 账户的书架）的应用内状态。
 *
 * **不落库**：它本来就是远端数据的镜像，持久化只会带来"本地副本与站点不一致"的问题；
 * 数据源始终是 `bookcase.php`，需要时刷新即可。
 *
 * 容器级单例（见 `AppContainer`），因为书架页与详情页都要读它：前者渲染"Wenku8书架"这一栏，
 * 后者要显示某本书"是否已在网站书架"。
 */
class Wenku8Shelf(private val repository: Wenku8Repository) {

    data class State(
        val items: List<BookcaseItem> = emptyList(),
        /** 是否成功加载过（用于区分"还没加载"与"确实是空的"）。 */
        val loaded: Boolean = false,
        val loading: Boolean = false,
        /** 上次失败的原因；成功后清空。 */
        val error: Throwable? = null,
    ) {
        /** 某本书是否已在站方书架（按**书 id** 判断）。 */
        fun contains(bookId: Int): Boolean = items.any { it.aid == bookId }

        /** 该书在站方书架的记录（移出时要用它的 [BookcaseItem.bid]）。 */
        fun itemOf(bookId: Int): BookcaseItem? = items.firstOrNull { it.aid == bookId }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 刷新互斥：书架页手动刷新与"增删后的确认刷新"可能并发，串行化避免互相覆盖结果；
     * 也因此不会对站点产生突发请求（一次刷新 = 1 个请求）。
     */
    private val refreshMutex = Mutex()

    suspend fun refresh() {
        refreshMutex.withLock {
            _state.update { it.copy(loading = true) }
            val result = repository.bookcase()
            _state.update { cur ->
                result.fold(
                    onSuccess = { cur.copy(items = it, loaded = true, loading = false, error = null) },
                    onFailure = { cur.copy(loading = false, error = it) },
                )
            }
        }
    }

    /**
     * 加入站方书架，并**立刻刷新确认**（站点不返回可靠的成败信息，只能以刷新结果为准）。
     *
     * @return 是否成功（含"刷新后确实出现在书架里"这一步）。
     */
    suspend fun add(bookId: Int): Boolean {
        val ok = repository.addToSiteBookcase(bookId).isSuccess
        if (ok) refresh()
        return ok && _state.value.contains(bookId)
    }

    /** 从站方书架移出（按书架记录 id），同样以刷新结果为准。 */
    suspend fun remove(bookcaseId: Int): Boolean {
        val ok = repository.removeFromSiteBookcase(bookcaseId).isSuccess
        if (ok) refresh()
        return ok
    }
}
