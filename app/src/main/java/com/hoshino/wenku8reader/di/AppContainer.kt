package com.hoshino.wenku8reader.di

import android.content.Context
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.data.UpdateChecker
import com.hoshino.wenku8reader.data.Wenku8Client
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.DefaultAccount
import com.hoshino.wenku8reader.data.local.LocalLibraryStore
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReadingStatsStore
import com.hoshino.wenku8reader.data.repository.Wenku8Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    private val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val readerSettings: ReaderSettings = ReaderSettings(context)

    /** 主镜像随设置可切换（见 ReaderSettings.primaryMirror）；注入内置账号供静默登录。 */
    val client: Wenku8Client = Wenku8Client(
        context,
        { readerSettings.flow.value.primaryMirror },
        { DefaultAccount.USERNAME to DefaultAccount.PASSWORD },
        { readerSettings.flow.value.cacheMaxMb },
    )

    val repository: Wenku8Repository = Wenku8Repository(client)

    val preferences: AppPreferences = AppPreferences(context)

    val localLibrary: LocalLibraryStore = LocalLibraryStore(context)

    /** 阅读时长聚合存储（按书+日期，热力图数据源）。 */
    val readingStats: ReadingStatsStore = ReadingStatsStore(context)

    /** 更新检查与安装（GitHub Releases 源）。 */
    val updateChecker: UpdateChecker = UpdateChecker()
    val updateCenter: UpdateCenter =
        UpdateCenter(context, updateChecker, preferences, readerSettings, applicationScope)

    val downloadEngine: DownloadEngine = DownloadEngine(context, repository, applicationScope)
}
