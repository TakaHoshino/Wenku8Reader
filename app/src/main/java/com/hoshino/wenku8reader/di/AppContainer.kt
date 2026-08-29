package com.hoshino.wenku8reader.di

import android.content.Context
import com.hoshino.wenku8reader.data.DownloadEngine
import com.hoshino.wenku8reader.data.Wenku8Client
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.LocalLibraryStore
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.repository.Wenku8Repository

/**
 * Manual dependency container owned by the Application. Holds the app-scoped
 * singletons and wires the dependency graph without a DI framework.
 */
class AppContainer(context: Context) {

    val client: Wenku8Client = Wenku8Client(context)

    val repository: Wenku8Repository = Wenku8Repository(client)

    val preferences: AppPreferences = AppPreferences(context)

    val readerSettings: ReaderSettings = ReaderSettings(context)

    val localLibrary: LocalLibraryStore = LocalLibraryStore(context)

    val downloadEngine: DownloadEngine = DownloadEngine(context, client)
}
