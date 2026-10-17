package com.hoshino.wenku8reader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * 书架清单的持久化：**只存用户自建的书架**（默认书架隐式存在，见 [DEFAULT_SHELF]）。
 *
 * ### 为什么是独立的 DataStore 文件
 *
 * - 不塞进现有的 `settings` DataStore：书架清单是**用户数据**，与设置的生命周期不同
 *   （清设置、设置迁移都不该连带动它）。
 * - 不放 Room：那需要 `AppDatabase` 升版 + 手写 `Migration`，而本工程没有
 *   Robolectric/instrumentation，迁移**无法自动化验证**；一旦漏注册 `addMigrations`
 *   就是存量用户首次访问数据库即崩。书架清单只是个短字符串数组，不值得这个代价。
 * - 不用 `stringSetPreferencesKey`：它是**无序**的，而书架顺序对用户可见。
 *
 * 存储形态：单个 key 存 JSON 数组字符串（保序）。写入是 DataStore 的事务性写入
 * （临时文件 + rename），不会出现"写一半进程被杀 → 清单损坏"。
 *
 * 本类只负责**存取**；增删改的判断全在 `ShelfOps.kt` 的纯函数里（有单测）。
 */
private val Context.shelfDataStore: DataStore<Preferences> by preferencesDataStore(name = "shelves")

class ShelfStore internal constructor(context: Context) {

    private val appContext = context.applicationContext

    private val dataStore: DataStore<Preferences> get() = appContext.shelfDataStore

    /**
     * 观察自建书架清单（保序）。
     *
     * 读取失败（磁盘异常 / 文件损坏）降级为空清单——即"只有默认书架"，
     * 而不是让书架页崩掉：此时书本身都还在（归属存在 Room 的 `books.shelf` 列），
     * 只是自建书架的名字暂时看不到，下次写入即恢复。
     */
    fun observe(): Flow<List<String>> = dataStore.data
        .map { decodeShelfList(it[KEY]) }
        .catch { emit(emptyList()) }

    /** 一次性读取（详情页弹层、管理页刷新等不需要持续观察的位置）。 */
    suspend fun read(): List<String> = observe().first()

    /**
     * 覆盖写入整份清单。
     *
     * 传进来的列表应已由 `ShelfOps` 的纯函数算好；这里再兜一层（去空、去默认同名、
     * 去重），保证任何调用路径都不会把坏数据落盘。
     */
    suspend fun replace(shelves: List<String>) {
        dataStore.edit { prefs -> prefs[KEY] = encodeShelfList(shelves) }
    }

    private companion object {
        /** 唯一的 key：整份清单的 JSON 数组。 */
        val KEY = stringPreferencesKey("list")
    }
}

/**
 * JSON → 清单；坏数据退化为空清单。
 *
 * 提到顶层是为了能被 JVM 单测直接钉住（见 `ShelfStoreCodecTest`）：这里的过滤规则
 * 一旦写错，表现是"用户新建的书架重启后不见了"，而不会抛任何异常。
 * 丢弃的三类历史脏数据都是 UI 无法正常操作的：空名、与默认同名的、超长的。
 */
internal fun decodeShelfList(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length())
            .map { i -> normalizeShelfName(arr.optString(i)) }
            .filter { it.isNotEmpty() && it != DEFAULT_SHELF && it.length <= SHELF_NAME_MAX_LENGTH }
            .distinct()
    }.getOrDefault(emptyList())
}

/** 清单 → JSON（保序，写盘前再兜一层过滤）。 */
internal fun encodeShelfList(shelves: List<String>): String {
    val arr = JSONArray()
    shelves
        .map { normalizeShelfName(it) }
        .filter { it.isNotEmpty() && it != DEFAULT_SHELF && it.length <= SHELF_NAME_MAX_LENGTH }
        .distinct()
        .forEach { arr.put(it) }
    return arr.toString()
}
