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
        // 放在 container 之后：静态引用一旦非空，container 必然已可用
        instance = this
        silentLogin()
    }

    /**
     * 进程结束时释放网络资源（Cronet 引擎）。
     *
     * 注意：`onTerminate` 在真机上**不会被调用**（只有在模拟器等调试环境才会），
     * 所以这只是"把释放动作接在该接的地方"——真机上 Cronet 随进程回收，无需额外处理。
     * 这样 [com.hoshino.wenku8reader.data.Wenku8Client.close] 不再是无人调用的死代码。
     */
    override fun onTerminate() {
        container.client.close()
        super.onTerminate()
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

    companion object {
        /**
         * 应用级静态引用。
         *
         * 唯一用途：[MainActivity.attachBaseContext] 里做应用内语言切换——那个时点
         * `activity.application` 尚未赋值，只能经这里取 [container] 里的设置。
         * 设置值由 `ReaderSettings` 在 [onCreate] 中同步载入，因此读到的不是默认值。
         */
        @Volatile
        internal var instance: Wenku8Application? = null
            private set
    }
}
