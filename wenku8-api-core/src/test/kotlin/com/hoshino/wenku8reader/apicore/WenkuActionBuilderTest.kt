package com.hoshino.wenku8reader.apicore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WenkuActionBuilderTest {
    @Test
    fun `构造最近更新列表 Action`() {
        assertEquals(
            "action=novellist&sort=lastupdate&page=2&t=0",
            WenkuActionBuilder.novelListWithInfo(
                sortedBy = WenkuActionBuilder.Sort.LAST_UPDATE,
                page = 2,
            ),
        )
    }

    @Test
    fun `中文搜索词按 UTF-8 百分号编码`() {
        assertEquals(
            "action=search&searchtype=articlename&searchkey=%E5%8F%B2%E8%8E%B1%E5%A7%86&t=1",
            WenkuActionBuilder.searchByNovelName(
                query = "史莱姆",
                language = WenkuActionBuilder.Language.TC,
            ),
        )
    }

    @Test
    fun `章节正文带 aid cid 和语言`() {
        assertEquals(
            "action=book&do=text&aid=4358&cid=183108&t=0",
            WenkuActionBuilder.chapterContent(4358, 183108),
        )
    }

    @Test
    fun `评论正文被编码且不丢失换行`() {
        assertEquals(
            "action=review&do=reply&rid=9&content=%E7%AC%AC%E4%B8%80%E8%A1%8C%0A%E7%AC%AC%E4%BA%8C%E8%A1%8C",
            WenkuActionBuilder.replyToReview(9, "第一行\n第二行"),
        )
    }

    @Test
    fun `非法 ID 被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            WenkuActionBuilder.novelFullMeta(0)
        }
    }
}
