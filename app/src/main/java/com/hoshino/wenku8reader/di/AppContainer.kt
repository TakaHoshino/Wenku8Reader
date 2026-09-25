package com.hoshino.wenku8reader.di

import android.content.Context
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.data.UpdateChecker
import com.hoshino.wenku8reader.data.Wenku8Client
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.AccountStore
import com.hoshino.wenku8reader.data.local.AppStorageManager
import com.hoshino.wenku8reader.data.local.DefaultAccount
import com.hoshino.wenku8reader.data.local.LibraryStore
import com.hoshino.wenku8reader.data.local.LocalDataMigration
import com.hoshino.wenku8reader.data.local.ReadingProgressStore
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReadingStatsStore
import com.hoshino.wenku8reader.data.local.ShelfStore
import com.hoshino.wenku8reader.data.local.db.AppDatabase
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container owned by the Application. Holds the app-scoped
 * singletons and wires the dependency graph without a DI framework.
 */
class AppContainer(context: Context) {

    /**
     * 应用级协程作用域（唯一）。
     *
     * 此前 `DownloadEngine` / `UpdateCenter` / `UpdateChecker` 各自裸建
     * `CoroutineScope(SupervisorJob() + …)` 且永不取消，作用域散落、生命周期不可控。
     * 这里集中持有一个，注入给需要的组件；需要主线程的（更新弹窗状态与安装器）
     * 用 `Main.immediate`，与原实现行为一致。
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 应用级**后台**任务作用域：与 [applicationScope] 共享同一个 Job，只是调度器换成 IO。
     *
     * 共享 Job 是关键——两个作用域同生共死，将来要统一取消后台任务时只需取消
     * [applicationScope]；若各自 `SupervisorJob()`，取消一个不会影响另一个。
     */
    private val ioScope: CoroutineScope =
        CoroutineScope(applicationScope.coroutineContext + Dispatchers.IO)

    /**
     * 启动应用级后台任务的唯一入口（静默登录、启动清理等）。
     *
     * 为什么不让调用方自己 `CoroutineScope(...).launch`：`Wenku8Application` 原先就是
     * 这么写的，于是静默登录既不属于应用作用域、也没有任何地方能取消它。
     */
    fun launchIo(block: suspend CoroutineScope.() -> Unit): Job = ioScope.launch(block = block)

    /** 设置存储需要应用级作用域来串行落盘（见 ReaderSettings 的说明）。 */
    val readerSettings: ReaderSettings = ReaderSettings(context, applicationScope)

    /**
     * 账户登录（实验性）的状态：只存用户名（激活账户 + 预填用的上一次用户名），不存密码。
     * 声明在 [client] 之前——客户端回落内置账号时要靠它清掉"激活账户"标记。
     */
    val accountStore: AccountStore = AccountStore(context)

    /** 主镜像随设置可切换（见 ReaderSettings.primaryMirror）；注入内置账号供静默登录。 */
    val client: Wenku8Client = Wenku8Client(
        context,
        { readerSettings.flow.value.primaryMirror },
        { DefaultAccount.USERNAME to DefaultAccount.PASSWORD },
        { readerSettings.flow.value.cacheMaxMb },
        // 回落到内置账号 ⇒ 用户账户的"激活标记"随之失效。
        // 必须这么做：内置账号登录后同样有会话，"有没有会话"区分不出账户归属。
        onUserSessionLost = {
            if (accountStore.read().activeUsername != null) accountStore.setActive(null)
        },
    )

    val repository: Wenku8Repository = Wenku8Repository(client)

    val preferences: AppPreferences = AppPreferences(context)

    /**
     * 存储占用统计与清理（缓存目录 + 网页离线缓存 + SharedPreferences）。
     * 需要 [client] 拿网页离线缓存的真实大小与清理入口，故在其之后初始化。
     */
    val storage: AppStorageManager = AppStorageManager(context, client)

    /** 本地数据库（书架 + 阅读进度）。 */
    private val database: AppDatabase = AppDatabase.build(context)

    /**
     * 旧 SharedPreferences → Room 的一次性搬迁门。
     *
     * 两个 store 的每次读写都会先过它，因此**不存在"页面先读到空数据"的窗口**；
     * [init] 里还会主动预热一次，把搬迁开销挪到启动阶段而不是第一次打开书架时。
     */
    private val localDataMigration: LocalDataMigration = LocalDataMigration(context, database.libraryDao())

    val libraryStore: LibraryStore = LibraryStore(database.libraryDao(), localDataMigration)

    val readingProgressStore: ReadingProgressStore =
        ReadingProgressStore(database.libraryDao(), localDataMigration)

    /**
     * 多书架：书架清单（只含用户自建的书架）的持久化。
     *
     * 与书籍归属分处两地是有意的——归属是 `BookEntity.shelf` 列（跟着书一起迁移），
     * 清单是独立的小集合，放 DataStore 可以完全避开 Room 升版与迁移。
     */
    val shelfStore: ShelfStore = ShelfStore(context)

    /** 阅读时长聚合存储（按书+日期，热力图数据源）。 */
    val readingStats: ReadingStatsStore = ReadingStatsStore(context)

    /** 更新检查与安装（GitHub Releases 源）。 */
    val updateChecker: UpdateChecker = UpdateChecker()
    val updateCenter: UpdateCenter =
        UpdateCenter(context, updateChecker, preferences, readerSettings, applicationScope)

    val downloadEngine: DownloadEngine = DownloadEngine(context, repository, applicationScope)

    init {
        /**
         * 预热一次性数据搬迁：让首次打开书架/阅读器时不必等待磁盘迁移，
         * 同时把结果留在日志里（失败会带上"已导入多少条"，便于排查）。
         */
        launchIo {
            runCatching { localDataMigration.ensure() }
        }
        /**
         * 启动时回收陈旧缓存产物（更新安装包 / 残留临时文件）。
         *
         * 放在 AppContainer 里而不是 Application：写操作需要应用级作用域，
         * 用这里的 [applicationScope] 才不会又冒出一个无人取消的裸作用域。
         */
        launchIo {
            runCatching { storage.pruneStaleArtifacts() }
        }
    }
}
