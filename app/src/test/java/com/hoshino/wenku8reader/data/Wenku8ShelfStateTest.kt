package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站方书架的应用内状态：**按书 id（aid）判断归属**，而不是按书架记录 id（bid）。
 *
 * 这两个 id 不相等，混用会让"这本书是否已在网站书架"整体判错（详情页星标状态出错，
 * 或长按移出时拿错 bid），所以用单测固定下来。
 */
class Wenku8ShelfStateTest {

    private val state = Wenku8Shelf.State(
        items = listOf(
            BookcaseItem(aid = 2896, name = "甲书", latestName = "第 3 章", bid = 12786589),
            BookcaseItem(aid = 3311, name = "乙书", bid = 12786600),
        ),
        loaded = true,
    )

    @Test
    fun `按书 id 判断是否已在站方书架`() {
        assertTrue(state.contains(2896))
        assertFalse(state.contains(12786589))   // 那是书架记录 id，不是书
        assertFalse(state.contains(9999))
    }

    @Test
    fun `查得到条目时同时给出移出要用的书架记录 id`() {
        assertEquals(12786589, state.itemOf(2896)?.bid)
        assertEquals("第 3 章", state.itemOf(2896)?.latestName)   // 站点给的最新章要一起带出来
        assertNull(state.itemOf(4242))
    }

    @Test
    fun `空书架不包含任何书`() {
        val empty = Wenku8Shelf.State()
        assertFalse(empty.contains(2896))
        assertNull(empty.itemOf(2896))
        assertFalse(empty.loaded)
    }
}
