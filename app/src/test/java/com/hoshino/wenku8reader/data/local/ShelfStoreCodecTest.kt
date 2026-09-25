package com.hoshino.wenku8reader.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 书架清单 JSON 编解码单测。
 *
 * 这里的规则写错不会抛异常，只会让**用户新建的书架重启后不见了**，
 * 或者让历史脏数据在列表里变成点不开的幽灵条目。
 */
class ShelfStoreCodecTest {

    @Test
    fun `往返保持顺序`() {
        val shelves = listOf("轻小说", "待读", "已读完")
        assertEquals(shelves, decodeShelves(encodeShelves(shelves)))
    }

    @Test
    fun `缺失或空值解码为空清单`() {
        assertEquals(emptyList<String>(), decodeShelves(null))
        assertEquals(emptyList<String>(), decodeShelves(""))
    }

    @Test
    fun `坏 JSON 退化为空清单而不是抛异常`() {
        assertEquals(emptyList<String>(), decodeShelves("{不是数组"))
    }

    @Test
    fun `解码丢弃空名_默认同名_超长与重复项`() {
        val raw = encodeShelves(
            listOf("", "   ", DEFAULT_SHELF, "轻小说", "轻小说", "架".repeat(SHELF_NAME_MAX_LENGTH + 1)),
        )
        assertEquals(listOf("轻小说"), decodeShelves(raw))
    }

    @Test
    fun `解码时归一化首尾空白`() {
        assertEquals(listOf("轻小说"), decodeShelves("""["  轻小说  "]"""))
    }

    @Test
    fun `编码前同样过滤_避免坏数据落盘`() {
        val encoded = encodeShelves(listOf("", DEFAULT_SHELF, " 待读 ", "待读"))
        assertEquals(listOf("待读"), decodeShelves(encoded))
    }
}
