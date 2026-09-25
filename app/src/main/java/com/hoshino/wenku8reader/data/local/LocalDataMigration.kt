package com.hoshino.wenku8reader.data.local

import android.content.Context
import com.hoshino.wenku8reader.data.local.db.LibraryDao
import com.hoshino.wenku8reader.data.local.db.MigrationResult
import com.hoshino.wenku8reader.data.local.db.SharedPrefsLegacySource
import com.hoshino.wenku8reader.data.local.db.migrateLegacyStores
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 「旧 SharedPreferences → Room」一次性搬迁的执行门。
 *
 * ### 为什么需要它
 *
 * 搬迁是**读改写**过程：导入完成前 Room 是空的，此时任何读取都会看到"没有书架 / 没有进度"。
 * 如果只在启动时后台跑一次迁移、而页面各读各的，就会出现两件坏事：
 * 1. 首帧书架为空（用户以为书丢了）；
 * 2. 阅读器读不到续读位置，从头开始。
 *
 * 所以所有读写入口都先 `ensure()`：首次调用真正执行迁移，之后的调用直接返回同一个结果。
 * 迁移本身是幂等的（见 `migrateLegacyStores`），失败时旧数据原样保留。
 *
 * [Mutex] + [result] 双检保证并发调用只会真正执行一次；失败结果也会被记住——否则每次
 * 读取都要重试一遍磁盘 I/O，而失败原因通常不会自己好（下次启动会重新尝试）。
 */
internal class LocalDataMigration(context: Context, private val dao: LibraryDao) {

    private val legacy = SharedPrefsLegacySource(context)
    private val mutex = Mutex()

    @Volatile
    private var result: MigrationResult? = null

    suspend fun ensure(): MigrationResult {
        result?.let { return it }
        return mutex.withLock {
            result ?: migrateLegacyStores(dao, legacy).also { result = it }
        }
    }
}
