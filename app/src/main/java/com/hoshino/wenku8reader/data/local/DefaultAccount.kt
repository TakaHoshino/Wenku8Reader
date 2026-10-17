package com.hoshino.wenku8reader.data.local

/**
 * 内置共享账号——应用的**默认**账号，也是任何用户账户都不可用时的兜底。
 *
 * 口径（2026-09 更新）：应用**默认不提供任何登录入口**，所有请求以内置共享账号进行；
 * 但**实验性「账户登录」**开关打开后（见 [AccountStore] 与 `ui/account/`），用户可以登录
 * 自己的 wenku8 账户，此时用户账户覆盖内置账号。两者的边界：
 * - 首启动、以及任何"需要会话但当前没有会话"的时刻，由
 *   [com.hoshino.wenku8reader.data.Wenku8Client.ensureLoggedIn] 用本账号静默登录；
 * - 用户账户登录成功后由 [AccountStore] 记录"当前激活账户"；`ensureLoggedIn()` 复用已有会话，
 *   不会把它顶掉；用户会话失效时回落本账号并清掉该标记；
 * - 退出用户登录 = 清会话 + 清激活标记 ⇒ 下一次请求自动回落本账号，阅读无感续用。
 *
 * 凭据为**硬编码常量**（不是从 `技术性文档(只读勿动)/wenku8account.txt` 运行时读取——
 * 该文件仅作运维记录，应用不读取它；旧注释与实现不符，此处已更正）。
 * 具体值属于凭据：**不得出现在日志、文档、测试夹具与新代码注释里**。
 *
 * 安全边界：这是共享的**公共**账号，不是用户个人凭据。应用**不保存任何用户密码**
 * （见 [AppPreferences]：明文凭据存储接口已整体移除），登录态仅由
 * [com.hoshino.wenku8reader.data.CookieStore] 持久化的会话 Cookie 承担。
 */
object DefaultAccount {
    const val USERNAME: String = "w8racc"
    const val PASSWORD: String = "TdAFDyWzXRxEpCmFYPnS"
}
