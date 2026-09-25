package com.hoshino.wenku8reader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hoshino.wenku8reader.data.Wenku8Hosts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    val hapticsEnabled: Boolean = true,        // 控件点击振动反馈（全局 Indication 注入）
    val hapticsStrength: Int = 50,             // 点击振动强度 0-100（映射到 VibrationEffect 幅度 1-255）
    val checkUpdatesOnStartup: Boolean = true, // 启动时自动检查更新
    val updateChannel: String = "stable",      // 更新通道："stable"（正式版）| "beta"（测试版）
    val updateSource: String = "github",       // 更新源："github" | "gh_proxy"（镜像）
    val appLanguage: String = "system",        // 界面语言："system" | "zh-CN" | "zh-TW"
    val cacheMaxMb: Int = 30,                  // 磁盘缓存上限（MB），默认 30
    // 动效风格：true = M3 Expressive 动效（推荐默认），false = 标准动效（线性、克制）
    val expressiveMotion: Boolean = true,
    // 实验性 UI 风格："material3"（默认，M3 Expressive）| "miuix"（MIUIX / HyperOS 风格）
    val uiStyle: String = "material3",
    // 实验性（仅 MIUIX 风格生效）：悬浮底栏（HyperOS 的胶囊式底栏）与其液态玻璃背景
    val floatingBottomBar: Boolean = true,
    val bottomBarGlass: Boolean = true,
    val autoPadding: Boolean = true,           // 自动边距（跟随安全区）
    val topPadding: Int = 24,
    val bottomPadding: Int = 16,
    val leftPadding: Int = 20,
    val rightPadding: Int = 20,
) {
    companion object {
        /** 默认主站镜像（单一来源：[Wenku8Hosts.DEFAULT_BASE]；用户可在设置页「网络」切换）。 */
        const val DEFAULT_MIRROR = Wenku8Hosts.DEFAULT_BASE

        /** 旧版本默认值，用于迁移：未手动改过主域的用户自动切到新默认值。 */
        const val LEGACY_DEFAULT_MIRROR = "https://www.wenku8.net"
    }
}

/**
 * 设置项在 DataStore 里的键名。
 *
 * **刻意与旧 `settings` SharedPreferences 的键名完全一致**：这样"从 SharedPreferences 迁移"
 * 就是一次同名搬运，迁移代码可以直接逐字段对照审查，不需要任何键名映射表。
 * 改动这里的名字等于让老用户的该设置失效，务必同步 [ReaderSettingsState] 默认值与迁移代码。
 */
private object Keys {
    val darkMode = stringPreferencesKey("dark_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val seedColor = longPreferencesKey("seed_color")
    val amoled = booleanPreferencesKey("amoled")
    val primaryMirror = stringPreferencesKey("primary_mirror")
    val backgroundMode = stringPreferencesKey("bg_mode")
    val readerBackgroundLight = longPreferencesKey("reader_bg_light")
    val readerTextColorLight = longPreferencesKey("reader_text_light")
    val readerBackgroundDark = longPreferencesKey("reader_bg_dark")
    val readerTextColorDark = longPreferencesKey("reader_text_dark")
    val backgroundImagePath = stringPreferencesKey("bg_image")
    val fontFamily = stringPreferencesKey("font_family")
    val fontSize = intPreferencesKey("font_size")
    val fontWeight = intPreferencesKey("font_weight")
    val lineSpacing = floatPreferencesKey("line_spacing")
    val traditionalChinese = booleanPreferencesKey("traditional")
    val scrollMode = booleanPreferencesKey("scroll_mode")
    val volumeKeyTurnPage = booleanPreferencesKey("volume_turn")
    val autoNextChapter = booleanPreferencesKey("auto_next")
    val pageTurnDirection = booleanPreferencesKey("turn_direction")
    val autoTurnInterval = intPreferencesKey("auto_interval")
    val clickTurnPage = booleanPreferencesKey("click_turn")
    val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
    val hapticsStrength = intPreferencesKey("haptics_strength")
    val checkUpdatesOnStartup = booleanPreferencesKey("check_updates_on_startup")
    val updateChannel = stringPreferencesKey("update_channel")
    val updateSource = stringPreferencesKey("update_source")
    val appLanguage = stringPreferencesKey("app_language")
    val cacheMaxMb = intPreferencesKey("cache_max_mb")
    val expressiveMotion = booleanPreferencesKey("expressive_motion")
    val uiStyle = stringPreferencesKey("ui_style")
    val floatingBottomBar = booleanPreferencesKey("floating_bottom_bar")
    val bottomBarGlass = booleanPreferencesKey("bottom_bar_glass")
    val autoPadding = booleanPreferencesKey("auto_padding")
    val topPadding = intPreferencesKey("pad_top")
    val bottomPadding = intPreferencesKey("pad_bottom")
    val leftPadding = intPreferencesKey("pad_left")
    val rightPadding = intPreferencesKey("pad_right")
    // 一次性迁移标记；置位后不再读旧 SharedPreferences。
    val migrated = booleanPreferencesKey("migrated_from_prefs")
}

/** 旧版设置所在的 SharedPreferences 文件（迁移来源；迁移后只作降级兜底保留在磁盘上）。 */
private const val LEGACY_PREFS = "settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App-wide customization store backed by **DataStore**.
 *
 * 对外仍然暴露一个同步可读的 [StateFlow]：主题、阅读器排版、安全区在首帧就要用。
 * 若改成"等 DataStore 第一次发射"，启动瞬间会先按默认值渲染——用户看到的是
 * **设置被重置**的闪烁（深色模式用户尤其明显）。
 *
 * ### 三个关键行为
 *
 * 1. **首值同步**：构造时阻塞读一次 DataStore（理由见 [loadOrSeed]），
 *    所以 `flow.value` 从第一帧起就是真实设置。
 * 2. **写内存 + 异步落盘**：`emit` 立即更新 `_flow`（UI 零延迟），再把整份状态交给
 *    [writes] 通道由后台协程落盘。用 conflated 通道而不是直接 `launch { edit }` ，
 *    是为了同时拿到**顺序**与**合并**：字号/行距 Slider 每帧都会改值，直接并发 edit
 *    可能后写先落、把新值覆盖回旧值；conflated 通道按提交顺序写且自动合并中间态。
 * 3. **一次性迁移**：首次运行把旧 `settings` SharedPreferences 的同名键搬进 DataStore，
 *    同一次事务里置位 [Keys.migrated]。旧文件留在磁盘上（降级或迁移出错可回退），
 *    但置位之后不再读取，避免长期维护两套会互相漂移的存储。
 */
class ReaderSettings(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext

    /** 迁移来源；只在 [legacyState] 里读一次。 */
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    private val dataStore: DataStore<Preferences> get() = appContext.settingsDataStore

    /** 待落盘状态：conflated 只保留最新一份，顺序仍由通道保证。 */
    private val writes = Channel<ReaderSettingsState>(Channel.CONFLATED)

    private val _flow = MutableStateFlow(
        // 兜底顺序说明了故障时的降级路径：DataStore 读不出来（磁盘异常 / 文件损坏）
        //   → 退回旧 SharedPreferences 里那份迁移前的值
        //   → 再不行才用全套默认值。
        // 关键是**绝不让构造抛异常**：ReaderSettings 在 Application.onCreate 里创建，
        // 抛出去就是启动即崩溃，用户连设置页都进不去。
        runBlocking { runCatching { loadOrSeed() }.getOrElse { legacyState() ?: ReaderSettingsState() } },
    )

    val flow: StateFlow<ReaderSettingsState> = _flow.asStateFlow()

    init {
        scope.launch {
            for (state in writes) {
                // 落盘失败只影响"下次启动是否记得这次改动"，不该让设置页崩溃；
                // 内存状态已经生效，用户本次使用不受影响。
                runCatching { dataStore.edit { it.writeAll(state) } }
            }
        }
    }

    /**
     * 读出当前设置；若尚未迁移，则先把旧 SharedPreferences 的值搬进 DataStore。
     *
     * 这里用 [runBlocking] 是有意为之：它发生在 `Application.onCreate`、任何 UI 出现之前，
     * 成本与旧实现（在同一个位置同步解析 `settings.xml`）同级，换来的是首帧设置正确。
     * 迁移与读取在同一个阻塞区间内完成，因此**不存在**「用户先改了一项、迁移又把它覆盖掉」
     * 的竞态窗口。
     */
    private suspend fun loadOrSeed(): ReaderSettingsState {
        if (dataStore.data.first()[Keys.migrated] != true) {
            val legacy = legacyState()
            dataStore.edit { prefs ->
                legacy?.let { prefs.writeAll(it) }
                prefs[Keys.migrated] = true
            }
        }
        return dataStore.data.first().toState()
    }

    /**
     * 只写入**发生变化**的状态，值没变就完全不落盘。
     *
     * 原实现每次调用都把 30+ 个 key 全量重写一遍，而 Slider 拖动时每帧都会触发一次；
     * 现在"没变不写"，拖动期间的连续变化再由 [writes] 通道合并成一次落盘。
     */
    private fun emit(transform: (ReaderSettingsState) -> ReaderSettingsState) {
        val prev = _flow.value
        val next = transform(prev)
        if (next == prev) return
        _flow.value = next
        writes.trySend(next)
    }

    /**
     * 读旧 SharedPreferences 的整份设置；文件为空（全新安装）时返回 null。
     *
     * 这里保留了两处**旧版本默认值迁移**，必须与旧实现逐字一致，否则老用户会看到
     * 主站被改回旧网域、阅读配色被重置。
     */
    private fun legacyState(): ReaderSettingsState? {
        if (legacyPrefs.all.isEmpty()) return null
        return ReaderSettingsState(
            darkMode = legacyPrefs.getString("dark_mode", "system") ?: "system",
            dynamicColor = legacyPrefs.getBoolean("dynamic_color", true),
            seedColor = legacyPrefs.getLong("seed_color", 0xFF3F5BA9L),
            amoled = legacyPrefs.getBoolean("amoled", false),
            // 默认 wenku8.cc；旧默认 wenku8.net（用户未手动改过）自动迁移到新默认
            primaryMirror = legacyPrefs.getString("primary_mirror", null)
                ?.takeUnless { it == ReaderSettingsState.LEGACY_DEFAULT_MIRROR }
                ?: ReaderSettingsState.DEFAULT_MIRROR,
            backgroundMode = legacyPrefs.getString("bg_mode", "color") ?: "color",
            // 旧版本只有单一 reader_bg / reader_text_color：迁移为浅色模式配色
            readerBackgroundLight = legacyPrefs.getLong(
                "reader_bg_light",
                legacyPrefs.getLong("reader_bg", 0xFFFFFFFFL),
            ),
            readerTextColorLight = legacyPrefs.getLong(
                "reader_text_light",
                legacyPrefs.getLong("reader_text_color", 0xFF000000L),
            ),
            backgroundImagePath = legacyPrefs.getString("bg_image", null),
            readerBackgroundDark = legacyPrefs.getLong("reader_bg_dark", 0xFF000000L),
            readerTextColorDark = legacyPrefs.getLong("reader_text_dark", 0xFFFFFFFFL),
            fontFamily = legacyPrefs.getString("font_family", "default") ?: "default",
            fontSize = legacyPrefs.getInt("font_size", 18),
            fontWeight = legacyPrefs.getInt("font_weight", 400),
            lineSpacing = legacyPrefs.getFloat("line_spacing", 1.8f),
            traditionalChinese = legacyPrefs.getBoolean("traditional", false),
            scrollMode = legacyPrefs.getBoolean("scroll_mode", false),
            volumeKeyTurnPage = legacyPrefs.getBoolean("volume_turn", true),
            autoNextChapter = legacyPrefs.getBoolean("auto_next", false),
            pageTurnDirection = legacyPrefs.getBoolean("turn_direction", true),
            autoTurnInterval = legacyPrefs.getInt("auto_interval", 10),
            clickTurnPage = legacyPrefs.getBoolean("click_turn", true),
            hapticsEnabled = legacyPrefs.getBoolean("haptics_enabled", true),
            hapticsStrength = legacyPrefs.getInt("haptics_strength", 50).coerceIn(0, 100),
            checkUpdatesOnStartup = legacyPrefs.getBoolean("check_updates_on_startup", true),
            updateChannel = legacyPrefs.getString("update_channel", "stable") ?: "stable",
            updateSource = legacyPrefs.getString("update_source", "github") ?: "github",
            appLanguage = legacyPrefs.getString("app_language", "system") ?: "system",
            cacheMaxMb = legacyPrefs.getInt("cache_max_mb", 30).coerceIn(10, 500),
            expressiveMotion = legacyPrefs.getBoolean("expressive_motion", true),
            uiStyle = legacyPrefs.getString("ui_style", "material3") ?: "material3",
            floatingBottomBar = legacyPrefs.getBoolean("floating_bottom_bar", true),
            bottomBarGlass = legacyPrefs.getBoolean("bottom_bar_glass", true),
            autoPadding = legacyPrefs.getBoolean("auto_padding", true),
            topPadding = legacyPrefs.getInt("pad_top", 24),
            bottomPadding = legacyPrefs.getInt("pad_bottom", 16),
            leftPadding = legacyPrefs.getInt("pad_left", 20),
            rightPadding = legacyPrefs.getInt("pad_right", 20),
        )
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
    fun setHapticsEnabled(enabled: Boolean) = emit { it.copy(hapticsEnabled = enabled) }
    fun setHapticsStrength(value: Int) =
        emit { it.copy(hapticsStrength = value.coerceIn(0, 100)) }
    fun setCheckUpdatesOnStartup(enabled: Boolean) = emit { it.copy(checkUpdatesOnStartup = enabled) }
    fun setUpdateChannel(channel: String) = emit { it.copy(updateChannel = channel) }
    fun setUpdateSource(source: String) = emit { it.copy(updateSource = source) }
    fun setAppLanguage(language: String) = emit { it.copy(appLanguage = language) }
    fun setCacheMaxMb(mb: Int) = emit { it.copy(cacheMaxMb = mb.coerceIn(10, 500)) }
    fun setExpressiveMotion(enabled: Boolean) = emit { it.copy(expressiveMotion = enabled) }
    fun setUiStyle(style: String) = emit { it.copy(uiStyle = style) }
    fun setFloatingBottomBar(enabled: Boolean) = emit { it.copy(floatingBottomBar = enabled) }
    fun setBottomBarGlass(enabled: Boolean) = emit { it.copy(bottomBarGlass = enabled) }
    fun setAutoPadding(enabled: Boolean) = emit { it.copy(autoPadding = enabled) }
    fun setTopPadding(v: Int) = emit { it.copy(topPadding = v) }
    fun setBottomPadding(v: Int) = emit { it.copy(bottomPadding = v) }
    fun setLeftPadding(v: Int) = emit { it.copy(leftPadding = v) }
    fun setRightPadding(v: Int) = emit { it.copy(rightPadding = v) }
}

/**
 * DataStore 内容 → 设置快照（缺键即默认值）。
 *
 * `internal` 是为了让 `ReaderSettingsCodecTest` 能钉住"每个字段都能原样往返"——
 * 漏写一个字段的后果是该设置**重启后静默复位**，编译期与运行期都不会报错。
 */
internal fun Preferences.toState(): ReaderSettingsState = ReaderSettingsState(
    darkMode = this[Keys.darkMode] ?: "system",
    dynamicColor = this[Keys.dynamicColor] ?: true,
    seedColor = this[Keys.seedColor] ?: 0xFF3F5BA9L,
    amoled = this[Keys.amoled] ?: false,
    primaryMirror = this[Keys.primaryMirror] ?: ReaderSettingsState.DEFAULT_MIRROR,
    backgroundMode = this[Keys.backgroundMode] ?: "color",
    readerBackgroundLight = this[Keys.readerBackgroundLight] ?: 0xFFFFFFFFL,
    readerTextColorLight = this[Keys.readerTextColorLight] ?: 0xFF000000L,
    readerBackgroundDark = this[Keys.readerBackgroundDark] ?: 0xFF000000L,
    readerTextColorDark = this[Keys.readerTextColorDark] ?: 0xFFFFFFFFL,
    backgroundImagePath = this[Keys.backgroundImagePath],
    fontFamily = this[Keys.fontFamily] ?: "default",
    fontSize = this[Keys.fontSize] ?: 18,
    fontWeight = this[Keys.fontWeight] ?: 400,
    lineSpacing = this[Keys.lineSpacing] ?: 1.8f,
    traditionalChinese = this[Keys.traditionalChinese] ?: false,
    scrollMode = this[Keys.scrollMode] ?: false,
    volumeKeyTurnPage = this[Keys.volumeKeyTurnPage] ?: true,
    autoNextChapter = this[Keys.autoNextChapter] ?: false,
    pageTurnDirection = this[Keys.pageTurnDirection] ?: true,
    autoTurnInterval = this[Keys.autoTurnInterval] ?: 10,
    clickTurnPage = this[Keys.clickTurnPage] ?: true,
    hapticsEnabled = this[Keys.hapticsEnabled] ?: true,
    // 越界值就地夹紧：非法数据（手工改过文件、旧版本写坏）不该让强度滑块跑飞
    hapticsStrength = (this[Keys.hapticsStrength] ?: 50).coerceIn(0, 100),
    checkUpdatesOnStartup = this[Keys.checkUpdatesOnStartup] ?: true,
    updateChannel = this[Keys.updateChannel] ?: "stable",
    updateSource = this[Keys.updateSource] ?: "github",
    appLanguage = this[Keys.appLanguage] ?: "system",
    cacheMaxMb = (this[Keys.cacheMaxMb] ?: 30).coerceIn(10, 500),
    expressiveMotion = this[Keys.expressiveMotion] ?: true,
    uiStyle = this[Keys.uiStyle] ?: "material3",
    floatingBottomBar = this[Keys.floatingBottomBar] ?: true,
    bottomBarGlass = this[Keys.bottomBarGlass] ?: true,
    autoPadding = this[Keys.autoPadding] ?: true,
    topPadding = this[Keys.topPadding] ?: 24,
    bottomPadding = this[Keys.bottomPadding] ?: 16,
    leftPadding = this[Keys.leftPadding] ?: 20,
    rightPadding = this[Keys.rightPadding] ?: 20,
)

/**
 * 设置快照 → DataStore（全量覆盖；DataStore 每次写入本来就重写整份文件）。
 *
 * 不碰 [Keys.migrated]：迁移标记必须留在原处，否则每次启动都会重跑一遍迁移。
 */
internal fun MutablePreferences.writeAll(state: ReaderSettingsState) {
    this[Keys.darkMode] = state.darkMode
    this[Keys.dynamicColor] = state.dynamicColor
    this[Keys.seedColor] = state.seedColor
    this[Keys.amoled] = state.amoled
    this[Keys.primaryMirror] = state.primaryMirror
    this[Keys.backgroundMode] = state.backgroundMode
    this[Keys.readerBackgroundLight] = state.readerBackgroundLight
    this[Keys.readerTextColorLight] = state.readerTextColorLight
    this[Keys.readerBackgroundDark] = state.readerBackgroundDark
    this[Keys.readerTextColorDark] = state.readerTextColorDark
    // null = 没有自定义背景图：DataStore 里删键，而不是存一个空串
    val image = state.backgroundImagePath
    if (image != null) {
        this[Keys.backgroundImagePath] = image
    } else {
        remove(Keys.backgroundImagePath)
    }
    this[Keys.fontFamily] = state.fontFamily
    this[Keys.fontSize] = state.fontSize
    this[Keys.fontWeight] = state.fontWeight
    this[Keys.lineSpacing] = state.lineSpacing
    this[Keys.traditionalChinese] = state.traditionalChinese
    this[Keys.scrollMode] = state.scrollMode
    this[Keys.volumeKeyTurnPage] = state.volumeKeyTurnPage
    this[Keys.autoNextChapter] = state.autoNextChapter
    this[Keys.pageTurnDirection] = state.pageTurnDirection
    this[Keys.autoTurnInterval] = state.autoTurnInterval
    this[Keys.clickTurnPage] = state.clickTurnPage
    this[Keys.hapticsEnabled] = state.hapticsEnabled
    this[Keys.hapticsStrength] = state.hapticsStrength
    this[Keys.checkUpdatesOnStartup] = state.checkUpdatesOnStartup
    this[Keys.updateChannel] = state.updateChannel
    this[Keys.updateSource] = state.updateSource
    this[Keys.appLanguage] = state.appLanguage
    this[Keys.cacheMaxMb] = state.cacheMaxMb
    this[Keys.expressiveMotion] = state.expressiveMotion
    this[Keys.uiStyle] = state.uiStyle
    this[Keys.floatingBottomBar] = state.floatingBottomBar
    this[Keys.bottomBarGlass] = state.bottomBarGlass
    this[Keys.autoPadding] = state.autoPadding
    this[Keys.topPadding] = state.topPadding
    this[Keys.bottomPadding] = state.bottomPadding
    this[Keys.leftPadding] = state.leftPadding
    this[Keys.rightPadding] = state.rightPadding
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
