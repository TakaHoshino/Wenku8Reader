package com.hoshino.wenku8reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.Wenku8Hosts

/**
 * 书架条目（书目快照 + 入架信息）。
 *
 * 字段**逐字段对齐**旧 `LocalLibraryStore` 写入的 `library.json`：
 * id/title/author/cover/status/lastUpdate/wordCount/desc/gid/tags/shelf/time。
 * 迁移代码（`LegacyLibraryMigration`）直接读旧 JSON 的同名键，命名一旦漂移就会静默丢字段。
 *
 * 与旧实现一致，这里**只存书目快照**；阅读进度（上次章节 / 已读章节 / 总章节数）
 * 由 [ReadingProgressEntity] 承担，两者按 bookId 关联。
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val author: String,
    val status: String,
    val lastUpdate: String,
    val wordCount: String,
    val description: String,
    val coverUrl: String?,
    val groupId: Int?,
    val tags: List<String>,
    val shelf: String,
    val addedAt: Long,
)

/** 书目快照 → [BookInfo]（详情页 / 阅读器 / 目录页统一使用的领域模型）。 */
internal fun BookEntity.toBookInfo(): BookInfo = BookInfo(
    id = id,
    title = title,
    author = author,
    status = status,
    lastUpdate = lastUpdate,
    wordCount = wordCount,
    description = description,
    // 旧书架里可能存着站点给的 http:// 封面地址（应用已默认禁止明文流量），
    // 与旧的 LocalLibraryStore.fromJson 一致，读取时就地升级协议，
    // 避免老用户已入架的书封面一直加载失败。
    coverUrl = Wenku8Hosts.normalizeImageUrl(coverUrl.orEmpty()).ifEmpty { null },
    groupId = groupId,
    tags = tags,
)

/**
 * [BookInfo] → 书目快照（入架时使用）。
 *
 * [shelfValue] 是**编码后的归属**（JSON 数组字符串，见 `ShelfOps.encodeShelves`），
 * 不是单个书架名——一本书可以同时属于多个书架。
 */
internal fun BookInfo.toEntity(shelfValue: String, addedAt: Long): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    status = status,
    lastUpdate = lastUpdate,
    wordCount = wordCount,
    description = description,
    // 同样就地升级协议后再落库，避免把 http:// 写回存储
    coverUrl = Wenku8Hosts.normalizeImageUrl(coverUrl.orEmpty()).ifEmpty { null },
    groupId = groupId,
    tags = tags,
    shelf = shelfValue,
    addedAt = addedAt,
)
