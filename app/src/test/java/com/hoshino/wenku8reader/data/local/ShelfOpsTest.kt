package com.hoshino.wenku8reader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多书架纯逻辑单测（`ShelfOps.kt`）。
 *
 * 这些判断错了不会抛异常，表现是**静默的数据不可见/不可操作**：书从书架上消失、
 * 书架重名后点不进去、删书架把书一起弄丢。所以把边界逐条钉住。
 */
class ShelfOpsTest {

    // ------------------------------------------------------------------ //
    // 名称规范化与校验
    // ------------------------------------------------------------------ //

    @Test
    fun `名称只去首尾空白`() {
        assertEquals("轻小说", normalizeShelfName("  轻小说  "))
        // 内部空格是用户有意的命名，必须保留
        assertEquals("轻 小说", normalizeShelfName("轻 小说"))
    }

    @Test
    fun `空名与全空白名都判为空`() {
        assertEquals(ShelfNameError.EMPTY, validateShelfName("", emptyList()))
        assertEquals(ShelfNameError.EMPTY, validateShelfName("   ", emptyList()))
    }

    @Test
    fun `超长名被拒_边界值放行`() {
        assertNull(validateShelfName("架".repeat(SHELF_NAME_MAX_LENGTH), emptyList()))
        assertEquals(
            ShelfNameError.TOO_LONG,
            validateShelfName("架".repeat(SHELF_NAME_MAX_LENGTH + 1), emptyList()),
        )
    }

    @Test
    fun `重名被拒_默认书架永远占用`() {
        assertEquals(ShelfNameError.DUPLICATE, validateShelfName("轻小说", listOf("轻小说")))
        assertEquals(ShelfNameError.DUPLICATE, validateShelfName(DEFAULT_SHELF, emptyList()))
        // 归一化之后才算重名：带空格的"重复"同样要拦
        assertEquals(ShelfNameError.DUPLICATE, validateShelfName(" 轻小说 ", listOf("轻小说")))
        assertNull(validateShelfName("待读", listOf("轻小说")))
    }

    @Test
    fun `改名为自己不算重名`() {
        assertNull(validateShelfName("轻小说", listOf("轻小说"), renaming = "轻小说"))
        // 但改成别的已存在书架仍然是重名
        assertEquals(
            ShelfNameError.DUPLICATE,
            validateShelfName("待读", listOf("轻小说", "待读"), renaming = "轻小说"),
        )
    }

    // ------------------------------------------------------------------ //
    // 清单增删改
    // ------------------------------------------------------------------ //

    @Test
    fun `新建追加到末尾`() {
        assertEquals(listOf("轻小说", "待读"), withShelfCreated(listOf("轻小说"), " 待读 "))
        assertEquals(listOf("轻小说"), withShelfCreated(emptyList(), "轻小说"))
    }

    @Test
    fun `新建非法名时原样返回`() {
        val existing = listOf("轻小说")
        assertEquals(existing, withShelfCreated(existing, ""))
        assertEquals(existing, withShelfCreated(existing, "轻小说"))
        assertEquals(existing, withShelfCreated(existing, "架".repeat(SHELF_NAME_MAX_LENGTH + 1)))
    }

    @Test
    fun `重命名保持原位置`() {
        assertEquals(
            listOf("轻小说", "已读完"),
            withShelfRenamed(listOf("轻小说", "待读"), "待读", "已读完"),
        )
    }

    @Test
    fun `重命名到自己或不存在时无变化`() {
        val existing = listOf("轻小说", "待读")
        assertEquals(existing, withShelfRenamed(existing, "轻小说", " 轻小说 "))
        assertEquals(existing, withShelfRenamed(existing, "不存在", "新名"))
        assertEquals(existing, withShelfRenamed(existing, "待读", "轻小说"))
    }

    @Test
    fun `删除自建书架`() {
        assertEquals(listOf("待读"), withShelfDeleted(listOf("轻小说", "待读"), "轻小说"))
    }

    @Test
    fun `默认书架不可删_删不存在的名字也是无操作`() {
        val existing = listOf("轻小说")
        assertEquals(existing, withShelfDeleted(existing, DEFAULT_SHELF))
        assertEquals(existing, withShelfDeleted(existing, "不存在"))
        assertFalse(isShelfDeletable(DEFAULT_SHELF))
        assertTrue(isShelfDeletable("轻小说"))
    }

    // ------------------------------------------------------------------ //
    // 展示清单与归属兜底
    // ------------------------------------------------------------------ //

    @Test
    fun `展示清单把默认放在首位`() {
        assertEquals(listOf(DEFAULT_SHELF), shelfNames(emptyList()))
        assertEquals(listOf(DEFAULT_SHELF, "轻小说"), shelfNames(listOf("轻小说")))
    }

    @Test
    fun `归属查询_未知书架兜底到默认`() {
        val existing = listOf("轻小说")
        assertEquals("轻小说", shelfOf("轻小说", existing))
        assertEquals(DEFAULT_SHELF, shelfOf(DEFAULT_SHELF, existing))
        // 改名残留 / 历史脏数据 / 搬迁中途失败：宁可显示在默认书架，也不能让书消失
        assertEquals(DEFAULT_SHELF, shelfOf("已删除的书架", existing))
        assertEquals(DEFAULT_SHELF, shelfOf(null, existing))
        assertEquals(DEFAULT_SHELF, shelfOf("   ", existing))
    }
}
