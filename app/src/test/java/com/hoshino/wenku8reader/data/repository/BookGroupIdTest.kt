package com.hoshino.wenku8reader.data.repository

import com.hoshino.wenku8reader.data.BookInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `gid` 回退规则：详情页给 groupId 就用它，否则按 `id / 1000` 回退（至少 1）。
 * 这条规则错了不会抛异常，只会让目录/正文/下载三处"取不到数据"，因此单独锁定。
 */
class BookGroupIdTest {

    @Test
    fun `详情页给了 groupId 时直接使用`() {
        assertEquals(7, bookGroupId(BookInfo(id = 12345, groupId = 7)))
        assertEquals(1, bookGroupId(BookInfo(id = 1, groupId = 1)))
    }

    @Test
    fun `缺 groupId 时按 id 除以 1000 回退`() {
        assertEquals(12, bookGroupId(BookInfo(id = 12345, groupId = null)))
        assertEquals(1, bookGroupId(BookInfo(id = 1000, groupId = null)))
    }

    @Test
    fun `小 id 回退结果至少为 1`() {
        // id < 1000 时整除得 0，而 /novel/0/... 是不存在的路径 → 必须钳到 1
        assertEquals(1, bookGroupId(BookInfo(id = 999, groupId = null)))
        assertEquals(1, bookGroupId(BookInfo(id = 1, groupId = null)))
        assertEquals(1, bookGroupId(BookInfo(id = 0, groupId = null)))
    }
}
