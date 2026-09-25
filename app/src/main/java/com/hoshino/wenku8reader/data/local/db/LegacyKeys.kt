package com.hoshino.wenku8reader.data.local.db

/**
 * 旧存储（SharedPreferences）的键名常量。
 *
 * 迁移代码既要*读*这些键，也要在导入校验通过后*删*它们，而 [com.hoshino.wenku8reader.data.local.AppPreferences]
 * 仍在写其中一部分（迁移与旧读取方会共存到第 4 步切换完成）。集中到一处是为了避免
 * 「写的键」和「迁移读的键」悄悄漂移——那种 bug 不会报错，只会安静地丢掉用户的书架或进度。
 */
internal const val LEGACY_PREFS_LIBRARY = "library"
internal const val LEGACY_PREFS_READING = "reading"

/** `library` 偏好里唯一的键：整份书架 JSON 数组。 */
internal const val LEGACY_KEY_LIBRARY_DATA = "data"

/** `progress_<bookId>`：继续阅读的章节 cid。 */
internal const val LEGACY_KEY_RESUME_CID = "progress_"

/** `progress_at_<bookId>`：最后阅读时间（毫秒）。 */
internal const val LEGACY_KEY_PROGRESS_AT = "progress_at_"

/** `progress_total_<bookId>`：总章节数。 */
internal const val LEGACY_KEY_PROGRESS_TOTAL = "progress_total_"

/** `finished_<bookId>`：JSON 数组形式的已读章节 cid 集合。 */
internal const val LEGACY_KEY_FINISHED = "finished_"

/**
 * 从偏好键（如 `progress_at_101`）取出书 id；不是阅读进度键则返回 null。
 *
 * `progress_at_` 与 `progress_` 是前缀包含关系，必须**先匹配更长的那个**，
 * 否则 `progress_at_101` 会被解析成 `progress_` + `at_101`（`toIntOrNull()` 恰好返回 null 而侥幸不炸，
 * 但 `progress_total_101` 同理，一旦将来出现 `progress_1_2` 这种键就会静默错位）。
 */
internal fun legacyBookIdOf(key: String): Int? = when {
    key.startsWith(LEGACY_KEY_PROGRESS_AT) ->
        key.removePrefix(LEGACY_KEY_PROGRESS_AT).toIntOrNull()

    key.startsWith(LEGACY_KEY_PROGRESS_TOTAL) ->
        key.removePrefix(LEGACY_KEY_PROGRESS_TOTAL).toIntOrNull()

    key.startsWith(LEGACY_KEY_FINISHED) ->
        key.removePrefix(LEGACY_KEY_FINISHED).toIntOrNull()

    key.startsWith(LEGACY_KEY_RESUME_CID) ->
        key.removePrefix(LEGACY_KEY_RESUME_CID).toIntOrNull()

    else -> null
}
