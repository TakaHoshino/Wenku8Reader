package com.hoshino.wenku8reader.data.local

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 设置状态在 DataStore 里的编解码单测。
 *
 * 为什么值得单独测：`ReaderSettingsState` 有 40 个字段，`writeAll` / `toState` 是两份手写的
 * 长清单。少写一行的后果不是崩溃，而是**该设置重启后被静默重置**——正是这次迁移最需要
 * 避免的事故。往返测试能把它变成一条失败的断言。
 *
 * 注意：新增字段时请同步更新下面「全字段往返」用例里的取值，否则新字段的遗漏不会被发现。
 */
class ReaderSettingsCodecTest {

    @Test
    fun `空存储读出全套默认值`() {
        assertEquals(ReaderSettingsState(), emptyPreferences().toState())
    }

    @Test
    fun `多书架开关默认关闭`() {
        // 产品要求：实验性功能必须默认关闭，老用户升级后书架页与收藏行为不得有任何变化
        assertFalse(emptyPreferences().toState().multiShelfEnabled)
    }

    @Test
    fun `每个字段都能原样往返`() {
        // 每一项都取与默认值不同的值——只要有字段没被 writeAll 写进去（或写错键），
        // 读回来就会退化成默认值，下面的断言立刻失败。
        val state = ReaderSettingsState(
            darkMode = "dark",
            dynamicColor = false,
            seedColor = 0xFF123456L,
            amoled = true,
            primaryMirror = "https://example.org",
            backgroundMode = "image",
            readerBackgroundLight = 0xFFF0F0F0L,
            readerTextColorLight = 0xFF111111L,
            readerBackgroundDark = 0xFF0A0A0AL,
            readerTextColorDark = 0xFFEEEEEEL,
            backgroundImagePath = "/sdcard/bg.png",
            fontFamily = "serif",
            fontSize = 26,
            fontWeight = 700,
            lineSpacing = 2.4f,
            traditionalChinese = true,
            scrollMode = true,
            volumeKeyTurnPage = false,
            autoNextChapter = true,
            pageTurnDirection = false,
            autoTurnInterval = 42,
            clickTurnPage = false,
            hapticsEnabled = false,
            hapticsStrength = 88,
            checkUpdatesOnStartup = false,
            updateChannel = "beta",
            updateSource = "gh_proxy",
            appLanguage = "zh-TW",
            cacheMaxMb = 256,
            expressiveMotion = false,
            uiStyle = "miuix",
            floatingBottomBar = false,
            bottomBarGlass = false,
            multiShelfEnabled = true,
            autoPadding = false,
            topPadding = 1,
            bottomPadding = 2,
            leftPadding = 3,
            rightPadding = 4,
        )

        val restored = mutablePreferencesOf().apply { writeAll(state) }.toState()

        assertEquals(state, restored)
    }

    @Test
    fun `没有自定义背景图时不写入该键`() {
        val state = ReaderSettingsState(backgroundImagePath = null)
        val prefs = mutablePreferencesOf().apply { writeAll(state) }
        assertNull(prefs[stringPreferencesKey("bg_image")])
        assertEquals(state, prefs.toState())
    }

    @Test
    fun `越界数值读取时就地夹紧`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("haptics_strength") to 999,
            intPreferencesKey("cache_max_mb") to 1,
        )
        val state = prefs.toState()
        assertEquals(100, state.hapticsStrength)
        assertEquals(10, state.cacheMaxMb)
    }

    @Test
    fun `写入设置不会清掉迁移标记`() {
        // 迁移标记与设置项同住一个 Preferences；全量覆盖写入若把它抹掉，
        // 每次启动都会重跑一次旧数据迁移。
        val prefs = mutablePreferencesOf(booleanPreferencesKey("migrated_from_prefs") to true)

        prefs.writeAll(ReaderSettingsState(fontSize = 22))

        assertEquals(true, prefs[booleanPreferencesKey("migrated_from_prefs")])
    }
}
