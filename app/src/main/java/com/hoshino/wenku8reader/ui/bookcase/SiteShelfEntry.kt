package com.hoshino.wenku8reader.ui.bookcase

import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.wenku8CoverUrl
import com.hoshino.wenku8reader.data.local.LibraryBook
import com.hoshino.wenku8reader.data.local.ReadingProgress
import com.hoshino.wenku8reader.data.local.WENKU8_SHELF

/**
 * 站方书架条目（`bookcase.php` 的一行）→ 书架卡片。
 *
 * 「Wenku8书架」与本地书架**同等地位**：卡片布局、阅读进度、排序都走同一套，所以这里要把
 * 站方给不出的字段补上，而不是让卡片显示成另一种样子。
 *
 * 数据来源（**零额外网络请求**）：
 * - 书名 / 最新章：站方给什么用什么；
 * - 封面：由书 id 按 [wenku8CoverUrl] 推出来（站点不返回封面字段，也不逐本拉 `bookInfo`）；
 * - 阅读进度：**套用本地进度**——`reading_progress` 按 `bookId` 存，站方没有进度概念，
 *   同一本书在哪个书架看到的都是同一份进度；
 * - 作者 / 字数 / 更新时间：本地书架上恰好也有这本书时顺带补全，让"按字数 / 按更新排序"
 *   在重叠的书籍上也有意义。**只读不写**，不会把站方书架写进本地归属。
 *
 * 从 `BookcaseViewModel` 提为顶层函数的原因与 [parseWordCount] 相同——"哪个字段进哪一格"
 * 改错了不会报错，只会让卡片显示得像另一本书，属于典型的静默缺陷，必须能用单测锁住。
 */
internal fun BookcaseItem.toSiteEntry(
    local: LibraryBook? = null,
    progress: ReadingProgress? = null,
): BookcaseEntry {
    val info = local?.book
    return BookcaseEntry(
        bookId = aid,
        title = name.ifBlank { info?.title.orEmpty() },
        // 站点不提供作者：本地已有这本书就用本地作者，否则把"最新章"放在这一格
        author = info?.author?.takeIf { it.isNotBlank() } ?: latestName.orEmpty(),
        coverUrl = info?.coverUrl ?: wenku8CoverUrl(aid),
        status = info?.status.orEmpty(),
        lastUpdate = info?.lastUpdate.orEmpty(),
        wordCount = info?.wordCount?.let(::parseWordCount) ?: 0,
        progressTotal = progress?.totalChapters ?: 0,
        readCount = progress?.finishedCids?.size ?: 0,
        // 归属标记为站方书架：长按卡片时据此走"移出网站书架"而不是本地归属多选。
        // 注意它**不进** LibraryStore，只是让卡片知道自己属于哪一栏。
        shelves = setOf(WENKU8_SHELF),
    )
}
