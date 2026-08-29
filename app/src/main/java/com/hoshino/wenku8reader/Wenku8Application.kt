package com.hoshino.wenku8reader

import android.app.Application
import com.hoshino.wenku8reader.data.local.DefaultAccount
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
        if (DefaultAccount.USERNAME.isBlank()) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val client = container.client
            val ok = runCatching { client.isLoggedIn() }.getOrDefault(false)
            if (!ok) {
                runCatching {
                    client.login(DefaultAccount.USERNAME, DefaultAccount.PASSWORD)
                }
            }
        }
    }
}
