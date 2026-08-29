package com.hoshino.wenku8reader.ui.navigation

import android.net.Uri

/** Type-safe navigation route names. */
object Routes {
    /** 主界面（分页式三个 Tab 的宿主路由） */
    const val MAIN = "main"
    const val SETTINGS_CUSTOM = "settings/custom"
    const val ABOUT = "about"
    const val DOWNLOADS = "downloads"
    const val DETAIL = "detail/{id}"
    const val READER = "reader/{id}"
    const val TAG = "tag/{tag}"

    fun detail(id: Int) = "detail/$id"
    fun reader(id: Int) = "reader/$id"
    fun tag(tag: String) = "tag/${Uri.encode(tag)}"
}
