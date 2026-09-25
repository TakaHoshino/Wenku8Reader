package com.hoshino.wenku8reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单本书的阅读进度 / 已读标记。
 *
 * 对应旧 `AppPreferences` 里散落在 `reading` 偏好中的四类键：
 * - `progress_<id>`       → [resumeCid]（继续阅读位置）
 * - `progress_at_<id>`    → [lastReadAt]（最后阅读时间，清理过期阅读数据的唯一依据）
 * - `progress_total_<id>` → [totalChapters]（书架进度条分母）
 * - `finished_<id>`       → [finishedCids]（目录页"已读"标记）
 *
 * 这四个键在旧存储里是**各自独立**的，可能只存在其中一个（例如只有 `finished_` 而没有
 * `progress_`，或进度写于「最后阅读时间」功能上线之前因而没有时间戳）。因此除 [bookId] 外
 * 全部可空 / 可为零，迁移时不能假设它们同生同灭。
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Int,
    /** 继续阅读的章节 cid；旧存储里没有 `progress_<id>` 键时为 null。 */
    val resumeCid: String?,
    /**
     * 最后阅读时间（毫秒）；null 表示该记录写于「最后阅读时间」功能上线之前，
     * 无法判断新旧——清理过期数据时必须保留（见 `staleReadingBookIds` 的保守规则）。
     */
    val lastReadAt: Long?,
    /** 总章节数（书架进度条分母）；0 表示未记录。 */
    val totalChapters: Int,
    /** 已标记「已读」的章节 cid 集合。 */
    val finishedCids: List<String>,
)

/** 该行是否只是空壳（三个数据字段都没有内容），迁移时可跳过以免写垃圾行。 */
internal val ReadingProgressEntity.isEmpty: Boolean
    get() = resumeCid == null &&
        lastReadAt == null &&
        totalChapters == 0 &&
        finishedCids.isEmpty()
