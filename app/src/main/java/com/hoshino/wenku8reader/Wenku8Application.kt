package com.hoshino.wenku8reader

import android.app.Application
import com.hoshino.wenku8reader.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repeat(3) { attempt ->
                val ok = runCatching { container.client.ensureLoggedIn() }.getOrDefault(false)
                if (ok) return@launch
                kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }
    }
}
