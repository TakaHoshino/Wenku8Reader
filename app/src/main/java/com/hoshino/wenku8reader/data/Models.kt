package com.hoshino.wenku8reader.data

import androidx.compose.runtime.Immutable

/** Search result from /modules/article/search.php */
@Immutable
data class SearchResult(val id: Int, val name: String, val coverUrl: String? = null)

/** Book detail parsed from /book/{id}.htm */
@Immutable
data class BookInfo(
    val id: Int,
    val title: String = "",
    val author: String = "",
    val category: String = "",
    val status: String = "",
    val lastUpdate: String = "",
    val wordCount: String = "",
    val description: String = "",
    val coverUrl: String? = null,
    val groupId: Int? = null,
    val tags: List<String> = emptyList(),
)

/** One chapter in the chapter index */
@Immutable
data class Chapter(val cid: String, val name: String)

/** One volume in the chapter index */
@Immutable
data class Volume(val name: String, val chapters: List<Chapter> = emptyList())

/** Parsed chapter body */
@Immutable
data class ChapterContent(
    val title: String,
    val text: String,
    val images: List<String> = emptyList(),
)

/**
 * 站方书架的一行。
 *
 * [aid] 是**书 id**（与 `/book/{id}.htm`、本地书架的 bookId 同一套编号）；
 * [bid] 是**书架记录 id**——站点用它来"移出书架"（`bookcase.php?delid=<bid>`）。
 * 两者不相等，混用会移错/移不掉，所以都要保留。
 */
@Immutable
data class BookcaseItem(
    val aid: Int,
    val name: String,
    val latestName: String? = null,
    val latestCid: String? = null,
    val bid: Int = 0,
)

/** A chapter flattened out of the volume tree, for the reader */
@Immutable
data class FlatChapter(val index: Int, val cid: String, val name: String)

/** A book entry on the homepage (with optional cover) */
@Immutable
data class HomeBook(val id: Int, val name: String, val coverUrl: String? = null)

/** A homepage section (block title + its books) */
@Immutable
data class HomeSection(val title: String, val books: List<HomeBook>)
