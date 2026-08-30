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

/**
 * Manual dependency container owned by the Application. Holds the app-scoped
 * singletons and wires the dependency graph without a DI framework.
 */
class AppContainer(context: Context) {

    val readerSettings: ReaderSettings = ReaderSettings(context)

    /** 主镜像随设置可切换（见 ReaderSettings.primaryMirror）；注入内置账号供静默登录。 */
    val client: Wenku8Client = Wenku8Client(
        context,
        { readerSettings.flow.value.primaryMirror },
        { DefaultAccount.USERNAME to DefaultAccount.PASSWORD },
    )

    val repository: Wenku8Repository = Wenku8Repository(client)

    val preferences: AppPreferences = AppPreferences(context)

    val localLibrary: LocalLibraryStore = LocalLibraryStore(context)

    /** 阅读时长聚合存储（按书+日期，热力图数据源）。 */
    val readingStats: ReadingStatsStore = ReadingStatsStore(context)

    /** 更新检查与安装（GitHub Releases 源）。 */
    val updateChecker: UpdateChecker = UpdateChecker()
    val updateCenter: UpdateCenter = UpdateCenter(context, updateChecker, preferences, readerSettings)

    val downloadEngine: DownloadEngine = DownloadEngine(context, client)
}
