package com.hoshino.wenku8reader

import android.app.Application
import com.hoshino.wenku8reader.di.AppContainer
import kotlinx.coroutines.launch

class Wenku8Application : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        silentLogin()
    }

    /** Signs in with the built-in default account so content works without any login UI. */
    private fun silentLogin() {
        // 从容器统一启动：后台任务与下载/更新共用同一个应用级作用域（见 AppContainer.launchIo）
        container.launchIo {
            repeat(3) { attempt ->
                val ok = runCatching { container.client.ensureLoggedIn() }.getOrDefault(false)
                if (ok) return@launchIo
                kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }
    }
}
