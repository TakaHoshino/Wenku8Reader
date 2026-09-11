package com.hoshino.wenku8reader.data.local

/**
 * 内置共享账号——本应用**唯一且全程使用**的账号。
 *
 * 产品约束：应用不提供登录、退出或切换账号的入口，所有请求始终以内置共享账号进行。
 * 因此：
 * - 首启动由 [com.hoshino.wenku8reader.data.Wenku8Client.ensureLoggedIn] 静默登录；
 * - 切换主镜像时重新用该账号登录（见 `SettingsViewModel.setPrimaryMirror`）；
 * - 设置页的"账号"分组只作说明性展示，不反映任何可由用户更改的状态。
 *
 * 凭据为**硬编码常量**（不是从 `技术性文档(只读勿动)/wenku8account.txt` 运行时读取——
 * 该文件仅作运维记录，应用不读取它；旧注释与实现不符，此处已更正）。
 *
 * 安全边界：这是共享的**公共**账号，不是用户个人凭据。应用不保存任何用户密码
 * （见 [AppPreferences]：明文凭据存储接口已整体移除），登录态仅由
 * [com.hoshino.wenku8reader.data.CookieStore] 持久化的会话 Cookie 承担。
 */
object DefaultAccount {
    const val USERNAME: String = "w8racc"
    const val PASSWORD: String = "TdAFDyWzXRxEpCmFYPnS"
}
