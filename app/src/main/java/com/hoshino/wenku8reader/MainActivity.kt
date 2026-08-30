package com.hoshino.wenku8reader

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshino.wenku8reader.ui.MainScaffold
import com.hoshino.wenku8reader.data.local.isDarkTheme
import com.hoshino.wenku8reader.ui.components.HapticScope
import com.hoshino.wenku8reader.ui.reader.VolumeKeyTurn
import com.hoshino.wenku8reader.ui.theme.Wenku8ReaderTheme

class MainActivity : ComponentActivity() {

    /**
     * 应用内语言切换：在 Activity 附着前按设置覆盖资源语言环境。
     * 切换语言后由设置页触发 `recreate()`，此方法即生效。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLocale(newBase))
    }

    private fun applyAppLocale(context: Context): Context {
        val language = (application as? Wenku8Application)
            ?.container?.readerSettings?.flow?.value?.appLanguage
            ?: "system"
        val locale = when (language) {
            "zh-TW" -> java.util.Locale.TRADITIONAL_CHINESE
            "zh-CN" -> java.util.Locale.SIMPLIFIED_CHINESE
            else -> return context // 跟随系统：不覆盖
        }
        return context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration)
                .apply { setLocale(locale) },
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (VolumeKeyTurn.enabled) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        VolumeKeyTurn.onVolumeUp?.invoke()
                        return true
                    }
                KeyEvent.KEYCODE_VOLUME_DOWN ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        VolumeKeyTurn.onVolumeDown?.invoke()
                        return true
                    }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()
        setContent {
            val app = application as Wenku8Application
            val settings by app.container.readerSettings.flow.collectAsStateWithLifecycle()
            Wenku8ReaderTheme(
                darkTheme = settings.isDarkTheme(isSystemInDarkTheme()),
                dynamicColor = settings.dynamicColor,
                seedColor = Color(settings.seedColor),
                amoled = settings.amoled,
            ) {
                // 全局点击振动（设置开关 + 强度控制）
                HapticScope(
                    enabled = settings.hapticsEnabled,
                    strength = settings.hapticsStrength,
                ) {
                    MainScaffold()
                }
            }
        }
    }

    /**
     * 高刷新率适配：请求系统以当前窗口支持的最高刷新率运行（同分辨率下选最高，
     * 避免切换分辨率）。API 30+ 用 [android.view.WindowManager.LayoutParams.preferredDisplayModeId]，
     * API 26-29 用 deprecated 的 [android.view.WindowManager.LayoutParams.preferredRefreshRate]。
     * 仅当设备刷新率高于当前模式时生效；60Hz 设备无副作用。
     */
    private fun requestHighRefreshRate() {
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: display.supportedModes.maxByOrNull { it.refreshRate }
            ?: return
        if (best.refreshRate <= current.refreshRate) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val attrs = window.attributes
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
        } else {
            @Suppress("DEPRECATION")
            window.attributes.preferredRefreshRate = best.refreshRate
        }
    }
}
