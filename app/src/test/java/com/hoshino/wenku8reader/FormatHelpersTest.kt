package com.hoshino.wenku8reader

import androidx.compose.ui.graphics.Color
import com.hoshino.wenku8reader.ui.common.formatByteSize
import com.hoshino.wenku8reader.ui.common.heatmapCellColor
import com.hoshino.wenku8reader.ui.common.weekdayColor
import com.hoshino.wenku8reader.ui.common.weekendColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存储页与热力图共用的纯格式化/色阶函数。
 *
 * 这些函数此前在 Material 与 MIUIX 两套界面里各写一份（已经出现命名漂移），
 * 收敛到 `ui/common` 后在此锁定行为：改配色或进位规则时先改测试，避免两套界面再次走偏。
 */
class FormatHelpersTest {

    @Test
    fun `文件大小按 1024 进位并保留一位小数`() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("512 B", formatByteSize(512))
        // 1023B 仍是 B，1024B 起进位到 KB（边界值最容易写错）
        assertEquals("1023 B", formatByteSize(1023))
        assertEquals("1 KB", formatByteSize(1024))
        // KB 档刻意不带小数（存储页里数字很多，少一位小数更清爽）；1.5KB 四舍五入为 2 KB
        assertEquals("2 KB", formatByteSize(1536))
        assertEquals("500 KB", formatByteSize(500L * 1024))
        assertEquals("1.0 MB", formatByteSize(1024L * 1024))
        assertEquals("2.5 MB", formatByteSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", formatByteSize(1024L * 1024 * 1024))
    }

    @Test
    fun `文件大小始终使用点号小数点`() {
        // 固定 Locale.US：某些区域设置会输出逗号小数点，与 MB/GB 单位混排时观感不一致
        val text = formatByteSize(1536L * 1024)
        assertTrue("应使用点号小数点：$text", text.contains("."))
    }

    @Test
    fun `无数据或零分钟一律用主题空色`() {
        val empty = Color(0xFF123456)
        assertEquals(empty, heatmapCellColor(0, hasData = false, weekend = false, emptyColor = empty))
        assertEquals(empty, heatmapCellColor(0, hasData = true, weekend = false, emptyColor = empty))
        assertEquals(empty, heatmapCellColor(30, hasData = false, weekend = true, emptyColor = empty))
    }

    @Test
    fun `周末用蓝色系_工作日用绿色系_且强度随分钟递增`() {
        val weekdayLow = heatmapCellColor(5, true, weekend = false, emptyColor = Color.Black)
        val weekdayHigh = heatmapCellColor(60, true, weekend = false, emptyColor = Color.Black)
        val weekendHigh = heatmapCellColor(60, true, weekend = true, emptyColor = Color.Black)

        assertEquals(weekdayColor(10), weekdayLow)
        assertEquals(weekdayColor(Int.MAX_VALUE), weekdayHigh)
        assertEquals(weekendColor(Int.MAX_VALUE), weekendHigh)
        // 同分钟数下周末与工作日必须不同色（否则图例两行就失去意义）
        assertNotEquals(weekdayHigh, weekendHigh)
        // 三档强度互不相同
        assertNotEquals(weekdayColor(10), weekdayColor(30))
        assertNotEquals(weekdayColor(30), weekdayColor(Int.MAX_VALUE))
    }
}
