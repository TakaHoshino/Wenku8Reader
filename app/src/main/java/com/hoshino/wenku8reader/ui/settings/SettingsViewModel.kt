package com.hoshino.wenku8reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.data.UpdateCenter
import com.hoshino.wenku8reader.data.Wenku8Client
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.AppStorageManager
import com.hoshino.wenku8reader.data.local.DefaultAccount
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.data.local.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 清理动作的结果：ViewModel 不持有 Context，因此只回传"发生了什么 + 释放了多少字节"，
 * 文案由 UI 层用字符串资源渲染（与 `UiText` 的既有约定一致）。
 */
data class CacheActionResult(
    val kind: Kind,
    val freedBytes: Long = 0L,
    val affectedBooks: Int = 0,
) {
    enum class Kind {
        /** 清理网页离线缓存 */
        HTML,

        /** 清理图片缓存 */
        IMAGES,

        /** 清理已下载的更新安装包 */
        UPDATE_PACKAGES,

        /** 清理 WebView 缓存与临时文件 */
        OTHER,

        /** 清理全部缓存 */
        ALL,

        /** 清理过期阅读记录 */
        READING_HISTORY,
    }

    /** 是否确实有内容被清理（用于决定提示"已释放 X"还是"没有可清理的内容"）。 */
    val freedSomething: Boolean get() = freedBytes > 0L || affectedBooks > 0
}

class SettingsViewModel(
    private val readerSettings: ReaderSettings,
    private val client: Wenku8Client,
    private val preferences: AppPreferences,
    private val storage: AppStorageManager,
    private val updateCenter: UpdateCenter,
) : ViewModel() {

    val ui: StateFlow<ReaderSettingsState> = readerSettings.flow

    // ---- 存储占用 ----

    private val _storageStats = MutableStateFlow(StorageBreakdown())
    val storageStats: StateFlow<StorageBreakdown> = _storageStats.asStateFlow()

    /** 清理动作结果（一次性提示）。 */
    private val _cacheResults = MutableSharedFlow<CacheActionResult>(extraBufferCapacity = 4)
    val cacheResults: SharedFlow<CacheActionResult> = _cacheResults.asSharedFlow()

    /** 网页离线缓存的分类明细（home/book/chapter/tag/...）。 */
    private val _cacheSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val cacheSizes: StateFlow<Map<String, Long>> = _cacheSizes.asStateFlow()

    init {
        refreshStorageStats()
    }

    /**
     * 刷新存储统计与网页缓存分类明细。
     *
     * 全部在 IO 线程：要遍历 cacheDir/dataDir（Coil 缓存与 WebView 数据目录可能有上万个文件），
     * 放在主线程会直接卡住设置页滚动。
     */
    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val breakdown = storage.stats()
            val categories = client.cacheStats()
            _storageStats.value = breakdown
            _cacheSizes.value = categories
        }
    }

    /** 刷新磁盘缓存分类大小（兼容旧调用点）。 */
    fun refreshCacheSizes() = refreshStorageStats()

    fun clearCache(category: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val before = client.htmlCacheSize()
            client.clearCache(category)
            val freed = (before - client.htmlCacheSize()).coerceAtLeast(0L)
            _cacheSizes.value = client.cacheStats()
            _storageStats.value = storage.stats()
            _cacheResults.emit(CacheActionResult(kind = CacheActionResult.Kind.HTML, freedBytes = freed))
        }
    }

    /** 清理图片缓存（Coil 磁盘 + 内存）。 */
    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = storage.clearImageCache()
            _storageStats.value = storage.stats()
            _cacheResults.emit(CacheActionResult(CacheActionResult.Kind.IMAGES, freed))
        }
    }

    /** 清理已下载的更新安装包。 */
    fun clearUpdatePackages() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = storage.clearUpdatePackages()
            _storageStats.value = storage.stats()
            _cacheResults.emit(CacheActionResult(CacheActionResult.Kind.UPDATE_PACKAGES, freed))
        }
    }

    /** 清理 WebView 缓存与临时文件。 */
    fun clearOtherCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = storage.clearWebViewCache() + storage.clearTempFiles()
            _storageStats.value = storage.stats()
            _cacheResults.emit(CacheActionResult(CacheActionResult.Kind.OTHER, freed))
        }
    }

    /**
     * 清理全部缓存：网页离线缓存 + 缓存目录（图片、更新包、WebView、临时文件）。
     *
     * 更新正在下载时跳过安装包目录（文件句柄仍指向它，删掉会让下载写出已删除的文件）。
     */
    fun clearAllCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            val preserveUpdate = updateCenter.state.value.downloading
            val htmlBefore = client.htmlCacheSize()
            val cacheFreed = storage.clearCacheDir(preserveUpdatePackage = preserveUpdate)
            client.clearCache(null)
            val htmlFreed = (htmlBefore - client.htmlCacheSize()).coerceAtLeast(0L)
            _cacheSizes.value = client.cacheStats()
            _storageStats.value = storage.stats()
            _cacheResults.emit(
                CacheActionResult(CacheActionResult.Kind.ALL, cacheFreed + htmlFreed),
            )
        }
    }

    /** 清理过期阅读记录（默认保留最近 30 天有阅读的书）。 */
    fun cleanupReadingHistory(keepDays: Int = AppPreferences.DEFAULT_KEEP_DAYS) {
        viewModelScope.launch(Dispatchers.IO) {
            val removed = preferences.cleanupStaleReadingData(keepDays)
            _storageStats.value = storage.stats()
            _cacheResults.emit(
                CacheActionResult(
                    kind = CacheActionResult.Kind.READING_HISTORY,
                    affectedBooks = removed,
                ),
            )
        }
    }

    fun setCacheMaxMb(mb: Int) {
        readerSettings.setCacheMaxMb(mb)
        // 立即按新上限裁剪（否则要等下一次写入才生效）
        viewModelScope.launch(Dispatchers.IO) {
            client.applyCacheLimit()
            _storageStats.value = storage.stats()
            _cacheSizes.value = client.cacheStats()
        }
    }

    fun setDarkMode(mode: String) = readerSettings.setDarkMode(mode)
    fun setDynamicColor(enabled: Boolean) = readerSettings.setDynamicColor(enabled)
    fun setSeedColor(color: Long) = readerSettings.setSeedColor(color)
    fun setAmoled(enabled: Boolean) = readerSettings.setAmoled(enabled)
    fun setExpressiveMotion(enabled: Boolean) = readerSettings.setExpressiveMotion(enabled)
    fun setHapticsEnabled(enabled: Boolean) = readerSettings.setHapticsEnabled(enabled)
    fun setHapticsStrength(value: Int) = readerSettings.setHapticsStrength(value)
    fun setCheckUpdatesOnStartup(enabled: Boolean) = readerSettings.setCheckUpdatesOnStartup(enabled)
    fun setUpdateChannel(channel: String) = readerSettings.setUpdateChannel(channel)
    fun setUpdateSource(source: String) = readerSettings.setUpdateSource(source)
    fun setAppLanguage(language: String) = readerSettings.setAppLanguage(language)

    /** 切换主站镜像：清空旧域 Cookie 与 cf_clearance，并用内置账号在新主域重新登录。 */
    fun setPrimaryMirror(url: String) {
        if (url == readerSettings.flow.value.primaryMirror) return
        readerSettings.setPrimaryMirror(url)
        viewModelScope.launch(Dispatchers.IO) {
            client.clearCookies()
            if (DefaultAccount.USERNAME.isNotBlank()) {
                runCatching { client.login(DefaultAccount.USERNAME, DefaultAccount.PASSWORD) }
            }
        }
    }

    fun setReaderBackgroundLight(color: Long) = readerSettings.setReaderBackgroundLight(color)
    fun setReaderTextColorLight(color: Long) = readerSettings.setReaderTextColorLight(color)
    fun setReaderBackgroundDark(color: Long) = readerSettings.setReaderBackgroundDark(color)
    fun setReaderTextColorDark(color: Long) = readerSettings.setReaderTextColorDark(color)
    fun setBackgroundImage(path: String?) = readerSettings.setBackgroundImage(path)
    fun setFontFamily(key: String) = readerSettings.setFontFamily(key)
    fun setFontSize(size: Int) = readerSettings.setFontSize(size)
    fun setFontWeight(weight: Int) = readerSettings.setFontWeight(weight)
    fun setLineSpacing(spacing: Float) = readerSettings.setLineSpacing(spacing)
    fun setTraditionalChinese(enabled: Boolean) = readerSettings.setTraditionalChinese(enabled)
}
