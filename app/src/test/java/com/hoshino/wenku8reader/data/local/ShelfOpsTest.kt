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

    @Test
    fun `Wenku8书架的名字被保留_自建书架不能占用`() {
        // 选中态按名字记：同名书架会让本地那一栏永远点不到（见 validateShelfName 的说明）
        assertEquals(ShelfNameError.DUPLICATE, validateShelfName(WENKU8_SHELF, emptyList()))
        assertEquals(ShelfNameError.DUPLICATE, validateShelfName(" $WENKU8_SHELF ", listOf("轻小说")))
        assertEquals(
            ShelfNameError.DUPLICATE,
            validateShelfName(WENKU8_SHELF, listOf("轻小说"), renaming = "轻小说"),
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
        // 站方书架同样不可删：删本地这一栏不会动站方数据，只会让人以为书架没了
        assertFalse(isShelfDeletable(WENKU8_SHELF))
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

    // ------------------------------------------------------------------ //
    // 归属（集合语义：一本书可同时在多个书架）
    // ------------------------------------------------------------------ //

    @Test
    fun `归属解析兼容旧格式单书架名与新格式 JSON 数组`() {
        // 旧格式：多书架改造前写入的裸字符串
        assertEquals(listOf("默认"), parseMembership("默认"))
        assertEquals(listOf("轻小说"), parseMembership("  轻小说  "))
        // 新格式：JSON 数组（保序）
        assertEquals(listOf("默认", "轻小说"), parseMembership("""["默认","轻小说"]"""))
        // 空值
        assertEquals(emptyList<String>(), parseMembership(null))
        assertEquals(emptyList<String>(), parseMembership("   "))
    }

    @Test
    fun `书架名本身像 JSON 时不会被误解析`() {
        // 用户完全可能把书架命名为「[测试]」——它无法解析成 JSON 数组，应当整串当作名字
        assertEquals(listOf("[测试]"), parseMembership("[测试]"))
    }

    @Test
    fun `归属编码与解析互为逆运算`() {
        val shelves = setOf("默认", "轻小说", "待读")
        // 先确认编码器输出就是标准 JSON（否则下面的往返失败会看不出是哪一步错了）
        assertEquals("""["默认","轻小说","待读"]""", encodeMembership(shelves))
        // 归属编解码必须**保留默认书架**：默认在归属里是显式的，只在书架清单里才是隐式的
        assertEquals(shelves, shelvesOf(encodeMembership(shelves), listOf("轻小说", "待读")))
    }

    @Test
    fun `归属查询_未知书架被丢弃_全无则兜底默认`() {
        val existing = listOf("轻小说", "待读")
        // 多归属：两个都还在 → 原样保留
        assertEquals(
            setOf(DEFAULT_SHELF, "轻小说"),
            shelvesOf("""["默认","轻小说"]""", existing),
        )
        // 其中一个书架已被删除 → 只保留还存在的那个（不能整条丢掉）
        assertEquals(setOf("轻小说"), shelvesOf("""["轻小说","已删除的书架"]""", existing))
        // 改名残留 / 历史脏数据 / 搬迁中途失败：宁可显示在默认书架，也不能让书消失
        assertEquals(setOf(DEFAULT_SHELF), shelvesOf("已删除的书架", existing))
        assertEquals(setOf(DEFAULT_SHELF), shelvesOf(null, existing))
        assertEquals(setOf(DEFAULT_SHELF), shelvesOf("   ", existing))
    }

    @Test
    fun `从归属里摘掉一个书架_摘空则回退默认`() {
        // 同时还属于别的书架 → 只摘掉目标那个
        assertEquals(setOf("轻小说"), withShelfRemoved(setOf("默认", "轻小说"), "默认"))
        // 摘空 → 回退默认书架（删书架的承诺是"其中的书移回默认书架"）
        assertEquals(setOf(DEFAULT_SHELF), withShelfRemoved(setOf("轻小说"), "轻小说"))
        // 本来就不属于 → 原样
        assertEquals(setOf("轻小说"), withShelfRemoved(setOf("轻小说"), "别的书架"))
    }

    @Test
    fun `书架改名后归属同步改名`() {
        assertEquals(
            setOf("默认", "已读完"),
            withShelfRenamedIn(setOf("默认", "待读"), "待读", "已读完"),
        )
        // 不属于该书架时原样
        assertEquals(setOf("默认"), withShelfRenamedIn(setOf("默认"), "待读", "已读完"))
    }
}
