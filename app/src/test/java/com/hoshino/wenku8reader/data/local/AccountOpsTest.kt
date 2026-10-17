package com.hoshino.wenku8reader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账户登录（实验性）纯逻辑单测：开关状态机、多书架联动、Wenku8 书架展示条件。
 *
 * 这些判断错了不会崩，只会让人困惑：点了开关没反应、多书架关掉后账户还开着却没有任何入口、
 * 或者用内置共享账号登录后也把站方书架当成用户自己的书架显示出来。
 */
class AccountOpsTest {

    // ------------------------------------------------------------------ //
    // 开关状态机
    // ------------------------------------------------------------------ //

    @Test
    fun `多书架未开时点账户开关_先问前置依赖`() {
        assertEquals(
            AccountSwitchAction.AskShelfPrerequisite,
            accountSwitchAction(multiShelfEnabled = false, accountLoginEnabled = false),
        )
    }

    @Test
    fun `多书架已开时点账户开关_直接开启`() {
        assertEquals(
            AccountSwitchAction.EnableAccount,
            accountSwitchAction(multiShelfEnabled = true, accountLoginEnabled = false),
        )
    }

    @Test
    fun `账户已开启时点开关_表示关闭_不再问依赖`() {
        assertEquals(
            AccountSwitchAction.DisableAccount,
            accountSwitchAction(multiShelfEnabled = true, accountLoginEnabled = true),
        )
        // 即使此时多书架是关的（理论上不该出现），点开关仍是"关闭"而不是再弹一次依赖框
        assertEquals(
            AccountSwitchAction.DisableAccount,
            accountSwitchAction(multiShelfEnabled = false, accountLoginEnabled = true),
        )
    }

    // ------------------------------------------------------------------ //
    // 多书架联动
    // ------------------------------------------------------------------ //

    @Test
    fun `关闭多书架时账户开关跟随关闭`() {
        assertFalse(accountEnabledAfterShelfChange(accountLoginEnabled = true, multiShelfEnabled = false))
    }

    @Test
    fun `多书架开着时账户开关保持原样`() {
        assertTrue(accountEnabledAfterShelfChange(accountLoginEnabled = true, multiShelfEnabled = true))
        assertFalse(accountEnabledAfterShelfChange(accountLoginEnabled = false, multiShelfEnabled = true))
    }

    // ------------------------------------------------------------------ //
    // Wenku8 书架展示条件
    // ------------------------------------------------------------------ //

    @Test
    fun `三个条件齐备才显示 Wenku8 书架`() {
        assertTrue(
            wenku8ShelfVisible(
                accountLoginEnabled = true,
                multiShelfEnabled = true,
                userAccountLoggedIn = true,
            ),
        )
    }

    @Test
    fun `缺账户开关或多书架都不显示`() {
        assertFalse(
            wenku8ShelfVisible(
                accountLoginEnabled = false,
                multiShelfEnabled = true,
                userAccountLoggedIn = true,
            ),
        )
        assertFalse(
            wenku8ShelfVisible(
                accountLoginEnabled = true,
                multiShelfEnabled = false,
                userAccountLoggedIn = true,
            ),
        )
    }

    @Test
    fun `只有内置账户会话时不算用户登录_不显示`() {
        // 最容易搞错的一条：内置账户登录后 hasSession() 也为真，但站方书架不是"用户的"书架
        assertFalse(
            wenku8ShelfVisible(
                accountLoginEnabled = true,
                multiShelfEnabled = true,
                userAccountLoggedIn = false,
            ),
        )
    }
}
