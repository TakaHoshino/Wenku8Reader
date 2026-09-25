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

/**
 * 账户登录（实验性）的状态存储。
 *
 * 只存**用户名**，两个键各司其职：
 * - [StoredAccount.activeUsername]：当前**激活的登录账户**；`null` 表示在用内置共享账号。
 *   只在"用户账户登录成功"时写入，退出登录 / 回落到内置账号时清除。
 * - [StoredAccount.lastUsername]：最近一次登录用的用户名，**仅用于账户页预填**，登出后不清。
 *
 * ### 为什么不靠 Cookie 判断"是不是用户账户"
 *
 * 会话 Cookie（`jieqiUserInfo`）里确实带着账号信息，但那是 jieqi 自己的一套序列化格式
 * （含 `#x....` 转义），解析它既脆弱又会把账号信息带进解析路径；更重要的是**内置账号登录后
 * 同样有会话**，"有没有会话"根本区分不出内置与用户。所以用这个显式标记 +
 * `Wenku8Client.hasSession()` 一起判断，并且**回落到内置账号时一定会把标记清掉**，
 * 保证标记不会与实际会话脱节。
 *
 * ### 密码
 *
 * **任何情况下都不落盘**：登录请求只在内存里持有明文，成功与否取决于站点是否下发
 * `jieqiUserInfo` 会话 Cookie（10 年有效），之后靠 Cookie 维持登录态——重启应用也不需要密码。
 */
private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/** 账户存储里的两个用户名（都不含密码）。 */
data class StoredAccount(
    /** 当前激活的登录账户；null = 内置共享账号。 */
    val activeUsername: String? = null,
    /** 最近一次登录用的用户名（预填用）。 */
    val lastUsername: String? = null,
)

class AccountStore internal constructor(context: Context) {

    private val appContext = context.applicationContext

    private val dataStore: DataStore<Preferences> get() = appContext.accountDataStore

    /** 读取失败（磁盘异常/文件损坏）时退化为"内置账号"，不让设置页或书架页崩掉。 */
    fun observe(): Flow<StoredAccount> = dataStore.data
        .map { prefs ->
            StoredAccount(
                activeUsername = prefs[KEY_ACTIVE]?.takeIf { it.isNotBlank() },
                lastUsername = prefs[KEY_LAST]?.takeIf { it.isNotBlank() },
            )
        }
        .catch { emit(StoredAccount()) }

    suspend fun read(): StoredAccount = observe().first()

    /** 设置/清除"当前激活账户"（null = 内置账号）。 */
    suspend fun setActive(username: String?) {
        dataStore.edit { prefs ->
            if (username.isNullOrBlank()) {
                prefs.remove(KEY_ACTIVE)
            } else {
                prefs[KEY_ACTIVE] = username
            }
        }
    }

    /** 记录最近登录的用户名（预填用；退出登录不清除）。 */
    suspend fun setLast(username: String) {
        dataStore.edit { prefs -> prefs[KEY_LAST] = username }
    }

    private companion object {
        val KEY_ACTIVE = stringPreferencesKey("active_username")
        val KEY_LAST = stringPreferencesKey("last_username")
    }
}
