package com.hoshino.wenku8reader.ui.common

import androidx.annotation.StringRes
import com.hoshino.wenku8reader.R

/**
 * 磁盘缓存类别。
 *
 * `key` 必须与 `HtmlDiskCache` 的文件名前缀一致（`{category}_{md5}.html`，见 `HtmlDiskCache.put`）。
 * Material 版与 MIUIX 版的存储页此前各有一份同名枚举（内容相同），收敛到这里一份：
 * 新增/调整分类时不会漏改其中一个（漏改的表现是"清理按钮清不掉那类文件"）。
 */
internal enum class CacheCategory(val key: String, @param:StringRes val labelRes: Int) {
    HOME("home", R.string.settings_cache_home),
    BOOK("book", R.string.settings_cache_book),
    CHAPTER("chapter", R.string.settings_cache_chapter),
    TAG("tag", R.string.settings_cache_tag),
    OTHER("other", R.string.settings_cache_other),
    LEGACY("legacy", R.string.settings_cache_legacy),
}
