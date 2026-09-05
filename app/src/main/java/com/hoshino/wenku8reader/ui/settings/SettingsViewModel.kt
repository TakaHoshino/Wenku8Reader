package com.hoshino.wenku8reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoshino.wenku8reader.data.Wenku8Client
import com.hoshino.wenku8reader.data.local.DefaultAccount
import com.hoshino.wenku8reader.data.local.ReaderSettings
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val readerSettings: ReaderSettings,
    private val client: Wenku8Client,
) : ViewModel() {

    val ui: StateFlow<ReaderSettingsState> = readerSettings.flow

    // ---- 缓存管理 ----

    private val _cacheSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val cacheSizes: StateFlow<Map<String, Long>> = _cacheSizes.asStateFlow()

    /** 刷新磁盘缓存分类大小（IO 线程读取，避免主线程遍历文件）。 */
    fun refreshCacheSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheSizes.value = client.cacheStats()
        }
    }

    /** 清理指定类型缓存（null = 全部）；完成后刷新大小统计。 */
    fun clearCache(category: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            client.clearCache(category)
            _cacheSizes.value = client.cacheStats()
        }
    }

    fun setCacheMaxMb(mb: Int) = readerSettings.setCacheMaxMb(mb)

    fun setDarkMode(mode: String) = readerSettings.setDarkMode(mode)
    fun setDynamicColor(enabled: Boolean) = readerSettings.setDynamicColor(enabled)
    fun setSeedColor(color: Long) = readerSettings.setSeedColor(color)
    fun setAmoled(enabled: Boolean) = readerSettings.setAmoled(enabled)
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
