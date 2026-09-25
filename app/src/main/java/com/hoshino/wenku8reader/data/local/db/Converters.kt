package com.hoshino.wenku8reader.data.local.db

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room 类型转换器。
 *
 * 只有 `List<String>` 需要转换（标签、已读章节 cid），沿用旧存储的 JSON 数组表示，
 * 迁移时可以直接把旧值搬过来而无需再解析一遍。
 *
 * 解析失败一律退化为空集合而不是抛异常：数据库里出现一条脏数据不应该让整个书架读不出来
 * （旧 `LocalLibraryStore` / `AppPreferences` 同样是 `runCatching` 容错）。
 */
class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToStringList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }
        }.getOrDefault(emptyList())
    }
}
