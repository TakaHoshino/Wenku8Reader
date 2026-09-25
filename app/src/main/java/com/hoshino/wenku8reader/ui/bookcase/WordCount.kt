package com.hoshino.wenku8reader.ui.bookcase

/**
 * 书架「字数」文本解析（如 `390K` / `1.2万` / `12M` / `千`）。
 *
 * 从 `BookcaseViewModel` 提为顶层函数：它是纯计算、却决定了书架按字数排序的结果——
 * 解析错了只会表现为"排序看着不对"，不会报错，属于典型的静默缺陷，必须能用单测锁住。
 * 正则提为常量，避免每本书都新建一次。
 */
private val WORD_COUNT = Regex("([0-9.]+)\\s*([KM千]|万)?")

internal fun parseWordCount(raw: String): Int {
    val s = raw.trim().uppercase().replace(",", "").replace("，", "")
    val m = WORD_COUNT.find(s) ?: return 0
    val num = m.groupValues[1].toDoubleOrNull() ?: return 0
    val mult = when (m.groupValues[2]) {
        "K", "千" -> 1000
        "M" -> 1_000_000
        "万" -> 10_000
        else -> 1
    }
    // 先按 Long 计算再钳制：异常数据（如 "99999M"）会让 Double→Int 截断甚至溢出，
    // 得到负数参与排序时会把这类书错排到极前/极后。
    return (num * mult).toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
