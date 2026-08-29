package com.hoshino.wenku8reader.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable snapshot of all reader/app customization settings.
 * ARGB colors are stored as [Long] (0xAARRGGBB).
 */
data class ReaderSettingsState(
    val darkMode: String = "system",          // "system" | "light" | "dark"
    val dynamicColor: Boolean = true,          // Android 12+ dynamic color
    val seedColor: Long = 0xFF3F5BA9L,        // manual theme seed (ARGB)
    val amoled: Boolean = false,              // 深色下使用纯黑背景（OLED 省电）
    val primaryMirror: String = DEFAULT_MIRROR, // 主站镜像（默认 wenku8.cc，可在设置切换）
    val backgroundMode: String = "color",      // "color" | "image"
    // 阅读器配色按主题模式分离：浅色模式默认纯白背景 + 纯黑字体
    val readerBackgroundLight: Long = 0xFFFFFFFFL,
    val readerTextColorLight: Long = 0xFF000000L,
    // 深色模式默认纯黑背景 + 纯白字体
    val readerBackgroundDark: Long = 0xFF000000L,
    val readerTextColorDark: Long = 0xFFFFFFFFL,
    val backgroundImagePath: String? = null,
    val fontFamily: String = "default",        // "default" | "sans" | "serif" | "mono"
    val fontSize: Int = 18,
    val fontWeight: Int = 400,
    val lineSpacing: Float = 1.8f,
    val traditionalChinese: Boolean = false,
    val scrollMode: Boolean = false,           // true=滚动翻页, false=侧滑翻页(默认)
    val volumeKeyTurnPage: Boolean = true,
    val autoNextChapter: Boolean = false,
    val pageTurnDirection: Boolean = true,     // true=向左翻(默认), false=向右翻
    val autoTurnInterval: Int = 10,            // seconds
    val clickTurnPage: Boolean = true,         // 侧滑翻页时点按左右翻页
    val autoPadding: Boolean = true,           // 自动边距（跟随安全区）
    val topPadding: Int = 24,
    val bottomPadding: Int = 16,
    val leftPadding: Int = 20,
    val rightPadding: Int = 20,
) {
    companion object {
        /** 默认主站镜像（用户可在设置页「网络」中切换）。 */
        const val DEFAULT_MIRROR = "https://www.wenku8.cc"

        /** 旧版本默认值，用于迁移：未手动改过主域的用户自动切到新默认值。 */
        const val LEGACY_DEFAULT_MIRROR = "https://www.wenku8.net"
    }
}

/**
 * App-wide customization store backed by SharedPreferences. Holds the single
 * [StateFlow] that drives both the global theme (MainActivity) and the reader.
 */
class ReaderSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<ReaderSettingsState> = _flow.asStateFlow()

    private fun load(): ReaderSettingsState = ReaderSettingsState(
        darkMode = prefs.getString("dark_mode", "system") ?: "system",
        dynamicColor = prefs.getBoolean("dynamic_color", true),
        seedColor = prefs.getLong("seed_color", 0xFF3F5BA9L),
        amoled = prefs.getBoolean("amoled", false),
        // 默认 wenku8.cc；旧默认 wenku8.net（用户未手动改过）自动迁移到新默认
        primaryMirror = prefs.getString("primary_mirror", null)
            ?.takeUnless { it == ReaderSettingsState.LEGACY_DEFAULT_MIRROR }
            ?: ReaderSettingsState.DEFAULT_MIRROR,        backgroundMode = prefs.getString("bg_mode", "color") ?: "color",
        // 旧版本只有单一 reader_bg / reader_text_color：迁移为浅色模式配色
        readerBackgroundLight = prefs.getLong(
            "reader_bg_light",
            prefs.getLong("reader_bg", 0xFFFFFFFFL),
        ),
        readerTextColorLight = prefs.getLong(
            "reader_text_light",
            prefs.getLong("reader_text_color", 0xFF000000L),
        ),
        backgroundImagePath = prefs.getString("bg_image", null),
        readerBackgroundDark = prefs.getLong("reader_bg_dark", 0xFF000000L),
        readerTextColorDark = prefs.getLong("reader_text_dark", 0xFFFFFFFFL),
        fontFamily = prefs.getString("font_family", "default") ?: "default",
        fontSize = prefs.getInt("font_size", 18),
        fontWeight = prefs.getInt("font_weight", 400),
        lineSpacing = prefs.getFloat("line_spacing", 1.8f),
        traditionalChinese = prefs.getBoolean("traditional", false),
        scrollMode = prefs.getBoolean("scroll_mode", false),
        volumeKeyTurnPage = prefs.getBoolean("volume_turn", true),
        autoNextChapter = prefs.getBoolean("auto_next", false),
        pageTurnDirection = prefs.getBoolean("turn_direction", true),
        autoTurnInterval = prefs.getInt("auto_interval", 10),
        clickTurnPage = prefs.getBoolean("click_turn", true),
        autoPadding = prefs.getBoolean("auto_padding", true),
        topPadding = prefs.getInt("pad_top", 24),
        bottomPadding = prefs.getInt("pad_bottom", 16),
        leftPadding = prefs.getInt("pad_left", 20),
        rightPadding = prefs.getInt("pad_right", 20),
    )

    private fun emit(transform: (ReaderSettingsState) -> ReaderSettingsState) {
        val next = transform(_flow.value)
        _flow.value = next
        prefs.edit()
            .putString("dark_mode", next.darkMode)
            .putBoolean("dynamic_color", next.dynamicColor)
            .putLong("seed_color", next.seedColor)
            .putBoolean("amoled", next.amoled)
            .putString("primary_mirror", next.primaryMirror)
            .putString("bg_mode", next.backgroundMode)
            .putLong("reader_bg_light", next.readerBackgroundLight)
            .putLong("reader_text_light", next.readerTextColorLight)
            .putLong("reader_bg_dark", next.readerBackgroundDark)
            .putLong("reader_text_dark", next.readerTextColorDark)
            .putString("bg_image", next.backgroundImagePath)
            .putString("font_family", next.fontFamily)
            .putInt("font_size", next.fontSize)
            .putInt("font_weight", next.fontWeight)
            .putFloat("line_spacing", next.lineSpacing)
            .putBoolean("traditional", next.traditionalChinese)
            .putBoolean("scroll_mode", next.scrollMode)
            .putBoolean("volume_turn", next.volumeKeyTurnPage)
            .putBoolean("auto_next", next.autoNextChapter)
            .putBoolean("turn_direction", next.pageTurnDirection)
            .putInt("auto_interval", next.autoTurnInterval)
            .putBoolean("click_turn", next.clickTurnPage)
            .putBoolean("auto_padding", next.autoPadding)
            .putInt("pad_top", next.topPadding)
            .putInt("pad_bottom", next.bottomPadding)
            .putInt("pad_left", next.leftPadding)
            .putInt("pad_right", next.rightPadding)
            .apply()
    }

    fun setDarkMode(mode: String) = emit { it.copy(darkMode = mode) }
    fun setDynamicColor(enabled: Boolean) = emit { it.copy(dynamicColor = enabled) }
    fun setSeedColor(color: Long) = emit { it.copy(seedColor = color) }
    fun setAmoled(enabled: Boolean) = emit { it.copy(amoled = enabled) }
    fun setPrimaryMirror(url: String) = emit { it.copy(primaryMirror = url) }
    fun setReaderBackgroundLight(color: Long) =
        emit { it.copy(readerBackgroundLight = color, backgroundMode = "color") }
    fun setReaderTextColorLight(color: Long) = emit { it.copy(readerTextColorLight = color) }
    fun setReaderBackgroundDark(color: Long) =
        emit { it.copy(readerBackgroundDark = color, backgroundMode = "color") }
    fun setReaderTextColorDark(color: Long) = emit { it.copy(readerTextColorDark = color) }
    fun setBackgroundImage(path: String?) =
        emit { it.copy(backgroundImagePath = path, backgroundMode = if (path != null) "image" else "color") }
    fun setFontFamily(key: String) = emit { it.copy(fontFamily = key) }
    fun setFontSize(size: Int) = emit { it.copy(fontSize = size) }
    fun setFontWeight(weight: Int) = emit { it.copy(fontWeight = weight) }
    fun setLineSpacing(spacing: Float) = emit { it.copy(lineSpacing = spacing) }
    fun setTraditionalChinese(enabled: Boolean) = emit { it.copy(traditionalChinese = enabled) }
    fun setScrollMode(enabled: Boolean) = emit { it.copy(scrollMode = enabled) }
    fun setVolumeKeyTurnPage(enabled: Boolean) = emit { it.copy(volumeKeyTurnPage = enabled) }
    fun setAutoNextChapter(enabled: Boolean) = emit { it.copy(autoNextChapter = enabled) }
    fun setPageTurnDirection(leftToRight: Boolean) = emit { it.copy(pageTurnDirection = leftToRight) }
    fun setAutoTurnInterval(seconds: Int) = emit { it.copy(autoTurnInterval = seconds) }
    fun setClickTurnPage(enabled: Boolean) = emit { it.copy(clickTurnPage = enabled) }
    fun setAutoPadding(enabled: Boolean) = emit { it.copy(autoPadding = enabled) }
    fun setTopPadding(v: Int) = emit { it.copy(topPadding = v) }
    fun setBottomPadding(v: Int) = emit { it.copy(bottomPadding = v) }
    fun setLeftPadding(v: Int) = emit { it.copy(leftPadding = v) }
    fun setRightPadding(v: Int) = emit { it.copy(rightPadding = v) }
}

/**
 * 解析当前是否为深色主题（含「跟随系统」）。MainActivity 主题、阅读器配色、
 * 自定义页预览共用此逻辑，避免三处重复。
 */
fun ReaderSettingsState.isDarkTheme(systemDark: Boolean): Boolean = when (darkMode) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}
