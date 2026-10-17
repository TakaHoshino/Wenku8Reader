package com.hoshino.wenku8reader.data.local

/**
 * 账户登录（实验性）的**纯逻辑**：开关状态机与 Wenku8 书架的展示条件。
 *
 * 与 `ShelfOps` 同样的理由——这些判断决定"点开关会不会弹窗""书架切换条里该不该多一项"，
 * 埋进 UI 就只能靠人工点按验证；抽成纯函数后由 `AccountOpsTest` 钉住。
 */

/** 用户点了账户开关之后应该发生什么。 */
internal sealed interface AccountSwitchAction {
    /** 直接打开账户开关。 */
    data object EnableAccount : AccountSwitchAction

    /** 先弹「该功能依赖多书架管理，是否同时开启前置功能？」。 */
    data object AskShelfPrerequisite : AccountSwitchAction

    /** 关闭账户开关。 */
    data object DisableAccount : AccountSwitchAction
}

/**
 * 账户开关的状态机：
 * - 已开启 → 点它表示关闭；
 * - 未开启、多书架已开 → 直接开启；
 * - 未开启、多书架没开 → 先问前置依赖（由用户决定要不要两个一起开）。
 */
internal fun accountSwitchAction(
    multiShelfEnabled: Boolean,
    accountLoginEnabled: Boolean,
): AccountSwitchAction = when {
    accountLoginEnabled -> AccountSwitchAction.DisableAccount
    multiShelfEnabled -> AccountSwitchAction.EnableAccount
    else -> AccountSwitchAction.AskShelfPrerequisite
}

/**
 * 联动：多书架被关闭时，账户开关必须跟随关闭（**只改开关，不动登录态数据**）。
 *
 * 为什么选"联动关闭"而不是"变成禁用态"：账户功能的承载体（Wenku8 书架、账户二级页入口）
 * 都挂在多书架 UI 上，留一个"开着却什么都看不到"的开关只会让人困惑。
 */
internal fun accountEnabledAfterShelfChange(
    accountLoginEnabled: Boolean,
    multiShelfEnabled: Boolean,
): Boolean = accountLoginEnabled && multiShelfEnabled

/**
 * Wenku8 书架（虚拟书架）是否出现在书架切换条里。
 *
 * 三个条件缺一不可：账户开关开着、多书架开着、**当前是用户账户登录**——
 * 内置账户登录后同样有会话（`hasSession()` 为真），但那是应用自带的共享账号，
 * 它的站方书架不算"用户的"书架，所以不能视为已登录。
 */
internal fun wenku8ShelfVisible(
    accountLoginEnabled: Boolean,
    multiShelfEnabled: Boolean,
    userAccountLoggedIn: Boolean,
): Boolean = accountLoginEnabled && multiShelfEnabled && userAccountLoggedIn

/** Wenku8 虚拟书架在切换条里的名字（不落库、不可重命名/删除）。 */
internal const val WENKU8_SHELF = "Wenku8书架"
