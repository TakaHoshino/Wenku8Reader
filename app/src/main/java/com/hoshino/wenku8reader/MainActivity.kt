package com.hoshino.wenku8reader

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
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
import com.hoshino.wenku8reader.ui.theme.UiStyle

class MainActivity : ComponentActivity() {

    /**
     * 应用内语言切换：在 Activity 附着前按设置覆盖资源语言环境。
     * 切换语言后由设置页触发 `recreate()`，此方法即生效。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLocale(newBase))
    }

    private fun applyAppLocale(context: Context): Context {
        // 注意：attachBaseContext 阶段 application 尚未赋值（Activity.attach 先调 attachBaseContext
        // 再赋 mApplication），不能经 Application/container 读取设置；直接读 SharedPreferences。
        val language = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("app_language", "system")
            ?: "system"
        val locale = when (language) {
            "zh-TW" -> java.util.Locale.TRADITIONAL_CHINESE
            "zh-CN" -> java.util.Locale.SIMPLIFIED_CHINESE
            else -> return context // 跟随系统：不覆盖
        }
        // 资源字符串由 createConfigurationContext 覆盖，但进程级默认 Locale 是另一份状态：
        // 不设置的话 java.text.DateFormat / NumberFormat 等仍按系统语言格式化。
        // attachBaseContext 早于任何 UI 与格式化调用，此处设置安全；跟随系统时不覆盖默认值。
        java.util.Locale.setDefault(locale)
        return context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration)
                .apply { setLocale(locale) },
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (VolumeKeyTurn.enabled && event.action == KeyEvent.ACTION_DOWN) {
            // 仅在阅读器确实注册了回调时才吞掉音量键：回调为 null（音量键翻页已关闭但
            // enabled 尚未被协程复位）时回退给系统，否则音量调节会被短暂吞掉。
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val turn = VolumeKeyTurn.onVolumeUp
                    if (turn != null) {
                        turn()
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val turn = VolumeKeyTurn.onVolumeDown
                    if (turn != null) {
                        turn()
                        return true
                    }
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
                // M3 Expressive 动效（可在设置页「外观」切回标准动效）
                expressiveMotion = settings.expressiveMotion,
                // 实验性：Material 3 Expressive / MIUIX 风格（设置 → 实验性）
                uiStyle = UiStyle.fromKey(settings.uiStyle),
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
        val display = currentDisplay() ?: return
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

    /**
     * 取当前窗口所在的 [Display]。
     *
     * 为什么不用裸的 `display`：它是 Activity 从 Context 继承的旧入口。
     * API 30+ 改走「窗口关联的显示设备」——`WindowManager.currentWindowMetrics` 只提供
     * 窗口尺寸与 Insets、**不暴露 Display**，拿不到刷新率模式，因此这里用
     * `context.display`（API 30 起可用），并以 [DisplayManager] 的默认显示设备兜底
     * （多屏/未关联显示设备等极端情况仍能取到可用模式）。
     * API 26-29 无替代 API，只能继续用旧入口。
     */
    private fun currentDisplay(): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ctx: Context = this
            ctx.display?.let { return it }
            return (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        }
        @Suppress("DEPRECATION")
        val legacy = display
        return legacy
    }
}
