package com.hoshino.wenku8reader.ui.bookcase

import com.hoshino.wenku8reader.data.BookcaseItem
import com.hoshino.wenku8reader.data.local.WENKU8_SHELF

/**
 * 站方书架条目（`bookcase.php` 的一行）→ 书架卡片。
 *
 * 站点只给书名与"最新章"，没有封面/作者/字数，也没有本地进度：
 * 卡片上的作者位放最新章名，是这一栏能提供的最有用信息；其余字段留空由卡片自行省略。
 *
 * 从 `BookcaseViewModel` 提为顶层函数的原因与 [parseWordCount] 相同——"哪个字段进哪一格"
 * 改错了不会报错，只会让卡片显示得像另一本书，属于典型的静默缺陷，必须能用单测锁住。
 */
internal fun BookcaseItem.toSiteEntry(): BookcaseEntry = BookcaseEntry(
    bookId = aid,
    title = name,
    // 站点不提供作者；把"最新章"放在副标题位置
    author = latestName.orEmpty(),
    lastUpdate = latestName.orEmpty(),
    // 归属标记为虚拟书架：长按卡片时据此走"移出网站书架"而不是本地归属多选。
    // 注意它**不进** LibraryStore，只是让卡片知道自己属于哪一栏。
    shelves = setOf(WENKU8_SHELF),
)
