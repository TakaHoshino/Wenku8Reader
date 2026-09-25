package com.hoshino.wenku8reader.ui.bookcase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 书架字数解析：站点会给出 `390K` / `1.2万` / `12M` / `千` 等多种写法，
 * 解析结果直接决定"按字数排序"的顺序——错了不会报错，只会看着不对，故用单测锁住。
 */
class WordCountTest {

    @Test
    fun `纯数字按原值`() {
        assertEquals(1234, parseWordCount("1234"))
        assertEquals(0, parseWordCount("0"))
    }

    @Test
    fun `K 与中文千都表示一千`() {
        assertEquals(390_000, parseWordCount("390K"))
        assertEquals(390_000, parseWordCount("390k"))
        assertEquals(390_000, parseWordCount("390 千"))
        assertEquals(1_500, parseWordCount("1.5K"))
    }

    @Test
    fun `万表示一万_M 表示一百万`() {
        assertEquals(12_000, parseWordCount("1.2万"))
        assertEquals(10_000, parseWordCount("1万"))
        assertEquals(12_000_000, parseWordCount("12M"))
        assertEquals(1_500_000, parseWordCount("1.5M"))
    }

    @Test
    fun `忽略千位分隔符与空白`() {
        assertEquals(1234, parseWordCount("1,234"))
        assertEquals(1234, parseWordCount(" 1234 "))
        assertEquals(1234, parseWordCount("1，234"))   // 中文全角逗号
    }

    @Test
    fun `空文本与非数字返回 0`() {
        assertEquals(0, parseWordCount(""))
        assertEquals(0, parseWordCount("未知"))
        assertEquals(0, parseWordCount("--"))
    }

    @Test
    fun `异常大值钳制到 Int 上限而不是溢出成负数`() {
        // "99999M" = 9.9999e10：不钳制会在 Double→Int 截断时得到负数，
        // 那样的书会被排到书架最前/最后，用户看到的是"排序乱跳"。
        assertEquals(Int.MAX_VALUE, parseWordCount("99999M"))
    }

    @Test
    fun `只取第一段数字_常见于站点把字数与更新时间连写`() {
        assertEquals(1234, parseWordCount("1234 字"))
        assertEquals(12_000, parseWordCount("1.2万 字"))
    }
}
