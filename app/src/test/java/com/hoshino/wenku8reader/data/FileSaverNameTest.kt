package com.hoshino.wenku8reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 重名下载判定单测。
//
// 这段逻辑决定「重新下载时删掉哪些既有文件」，判错等于误删用户放在
// Downloads/Wenku8 里的文件，因此把两种系统命名形态与几个反例都钉死。
class FileSaverNameTest {

    @Test
    fun `完全同名视为重复`() {
        assertTrue(isDuplicateDownloadName("魔法禁书目录.txt", "魔法禁书目录.txt"))
    }

    @Test
    fun `标准形态_扩展名前带序号`() {
        assertTrue(isDuplicateDownloadName("魔法禁书目录 (1).txt", "魔法禁书目录.txt"))
        assertTrue(isDuplicateDownloadName("魔法禁书目录 (12).txt", "魔法禁书目录.txt"))
        assertTrue(isDuplicateDownloadName("魔法禁书目录(2).txt", "魔法禁书目录.txt"))
    }

    @Test
    fun `部分 ROM 形态_序号追加在扩展名之后`() {
        assertTrue(isDuplicateDownloadName("魔法禁书目录.txt(1)", "魔法禁书目录.txt"))
        assertTrue(isDuplicateDownloadName("魔法禁书目录.txt (3)", "魔法禁书目录.txt"))
    }

    @Test
    fun `扩展名大小写不同仍视为重复`() {
        assertTrue(isDuplicateDownloadName("魔法禁书目录 (1).TXT", "魔法禁书目录.txt"))
    }

    @Test
    fun `无扩展名时只在结尾追加序号`() {
        assertTrue(isDuplicateDownloadName("魔法禁书目录 (1)", "魔法禁书目录"))
        assertFalse(isDuplicateDownloadName("魔法禁书目录2", "魔法禁书目录"))
    }

    @Test
    fun `不同书名不视为重复`() {
        assertFalse(isDuplicateDownloadName("刀剑神域.txt", "魔法禁书目录.txt"))
        assertFalse(isDuplicateDownloadName("魔法禁书目录 外传.txt", "魔法禁书目录.txt"))
    }

    @Test
    fun `扩展名不同不视为重复`() {
        assertFalse(isDuplicateDownloadName("魔法禁书目录 (1).epub", "魔法禁书目录.txt"))
        assertFalse(isDuplicateDownloadName("魔法禁书目录.epub(1)", "魔法禁书目录.txt"))
    }

    @Test
    fun `括号内不是数字不视为重复`() {
        assertFalse(isDuplicateDownloadName("魔法禁书目录 (上).txt", "魔法禁书目录.txt"))
        assertFalse(isDuplicateDownloadName("魔法禁书目录.txt(终)", "魔法禁书目录.txt"))
    }

    @Test
    fun `目标名自带序号时不反向匹配`() {
        assertFalse(isDuplicateDownloadName("X.txt", "X (1).txt"))
    }

    @Test
    fun `空字符串不误判`() {
        assertFalse(isDuplicateDownloadName("", "魔法禁书目录.txt"))
        assertFalse(isDuplicateDownloadName("魔法禁书目录.txt", ""))
        assertTrue(isDuplicateDownloadName("", ""))
    }
}
