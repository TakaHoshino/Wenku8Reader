package com.hoshino.wenku8reader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.Wenku8Client
import com.hoshino.wenku8reader.data.local.AccountStore
import com.hoshino.wenku8reader.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 账户页状态。
 *
 * [userLoggedIn] 的判断不是"有没有会话"——内置共享账号登录后同样有会话，
 * 必须同时满足"有激活的用户账户标记"与"会话仍在"（见 `AccountStore` 的说明）。
 */
data class AccountUiState(
    /** 当前激活的用户账户名；null = 未登录用户账户（用的是内置共享账号）。 */
    val activeUsername: String? = null,
    /** 最近一次登录用的用户名（预填）。 */
    val lastUsername: String = "",
    /** 是否持有会话（含内置账号的会话）。 */
    val sessionPresent: Boolean = false,
    val error: UiText? = null,
    val notice: UiText? = null,
) {
    val userLoggedIn: Boolean get() = activeUsername != null && sessionPresent
}

/**
 * 账户登录页（Material 与 MIUIX 共用）。
 *
 * 登录在应用内 WebView 的真实站点页面完成；本 ViewModel 只接收登录成功后回填的 Cookie，
 * 从不读取、保存或传递用户名密码。
 */
class AccountViewModel(
    private val client: Wenku8Client,
    private val accountStore: AccountStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    /** 重新读取账户状态（进入页面、从别处返回时调用）。 */
    fun refresh() {
        viewModelScope.launch {
            val stored = accountStore.read()
            val session = withContext(Dispatchers.IO) { client.isLoggedIn() }
            _ui.update {
                it.copy(
                    activeUsername = stored.activeUsername,
                    lastUsername = stored.lastUsername.orEmpty(),
                    sessionPresent = session,
                )
            }
        }
    }

    /** 打开网站登录页时使用当前主镜像的登录地址。 */
    fun websiteLoginUrl(): String = client.websiteLoginUrl()

    /**
     * 网站登录 Activity 已把 Cookie 写入共享 CookieStore。
     * 这里再次校验会话，并记录“当前使用的是用户账户”这一应用侧标记。
     */
    fun completeWebsiteLogin(username: String?) {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { client.isLoggedIn() }
            if (!session) {
                _ui.update {
                    it.copy(
                        error = UiText.StringResource(R.string.account_error_failed),
                    )
                }
                return@launch
            }
            val detected = username?.trim()?.takeIf { it.isNotEmpty() }
            val displayName = detected ?: "网站账户"
            if (detected != null) accountStore.setLast(detected)
            accountStore.setActive(displayName)
            _ui.update {
                it.copy(
                    activeUsername = displayName,
                    lastUsername = detected ?: it.lastUsername,
                    sessionPresent = true,
                    error = null,
                    notice = UiText.StringResource(R.string.account_login_ok, displayName),
                )
            }
        }
    }

    /**
     * 退出登录 = 清会话 + 清激活标记，**随后立刻回落内置共享账号**。
     *
     * 顺序（清标记 → 回落）与 `Wenku8Client.ensureLoggedIn` 里的一致：只要发生回落就必须
     * 先清标记，否则会出现"其实已经掉回内置、界面还显示某个用户已登录"。
     */
    fun logout() {
        viewModelScope.launch {
            client.clearCookies()
            accountStore.setActive(null)
            runCatching { client.ensureLoggedIn() }
            val session = withContext(Dispatchers.IO) { client.isLoggedIn() }
            _ui.update {
                it.copy(
                    activeUsername = null,
                    sessionPresent = session,
                    error = null,
                    notice = UiText.StringResource(R.string.account_logout_ok),
                )
            }
        }
    }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }
}
