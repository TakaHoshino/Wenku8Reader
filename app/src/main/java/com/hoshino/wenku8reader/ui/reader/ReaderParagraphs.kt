package com.hoshino.wenku8reader.ui.reader

/**
 * 把整章正文切成「逐段渲染」用的段落列表。
 *
 * 为什么需要：滚动模式此前把整章正文交给**一个** `Text`——章节越长，单次文本测量的
 * 代价越大，且整段文字构成一个不可分割的布局单元（改字号/选字都要整体重排）。切成段落
 * 后每段独立测量，滚动时还能只组合视口附近的段落（见 `ReaderScreen` 的 LazyColumn）。
 *
 * 切分口径（保持与原文本一致，不做多余的排版改写）：
 * - 只按换行符切段，**不 trim 行内容**——中文正文常用全角空格做段首缩进，trim 会把它吃掉；
 * - 纯空白行（正文里的空行/分隔空行）直接丢弃，避免渲染出空白项；
 * - 兼容 CRLF / CR（部分历史缓存的正文可能仍是 Windows 换行）。
 */
internal fun splitReaderParagraphs(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    return text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .filter { it.isNotBlank() }
}
