package com.hoshino.wenku8reader.ui.navigation

import android.net.Uri

/** Type-safe navigation route names. */
object Routes {
    /** 主界面（分页式三个 Tab 的宿主路由） */
    const val MAIN = "main"
    const val SETTINGS_CUSTOM = "settings/custom"
    const val ABOUT = "about"
    const val DOWNLOADS = "downloads"
    const val STATS = "stats"
    const val DETAIL = "detail/{id}"
    const val READER = "reader/{id}?cid={cid}"
    const val TAG = "tag/{tag}"
    const val AUTHOR = "author/{name}"
    const val TOC = "toc/{id}"

    fun detail(id: Int) = "detail/$id"
    fun reader(id: Int, cid: String? = null) =
        if (cid == null) "reader/$id" else "reader/$id?cid=${Uri.encode(cid)}"
    fun tag(tag: String) = "tag/${Uri.encode(tag)}"
    fun author(name: String) = "author/${Uri.encode(name)}"
    fun toc(id: Int) = "toc/$id"
}
