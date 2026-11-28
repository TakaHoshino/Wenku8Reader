package com.hoshino.wenku8reader.data.local

import com.hoshino.wenku8reader.BuildConfig
import com.hoshino.wenku8reader.data.CookieStore
import com.hoshino.wenku8reader.data.Wenku8Hosts
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 将本地构建私密配置中的 Wenku8 身份 Cookie 种入各镜像。
 *
 * 生产/CI 构建默认没有这两个 BuildConfig 值，函数直接返回。Cookie 不写日志、
 * 不出现在源码里，也不依赖账号密码登录表单。
 */
internal object BuiltInSessionCookies {
    fun seed(cookieStore: CookieStore) {
        val userInfo = BuildConfig.WENKU8_JIEQI_USER_INFO
        val visitInfo = BuildConfig.WENKU8_JIEQI_VISIT_INFO
        if (userInfo.isBlank() || visitInfo.isBlank()) return

        val rawCookie = "jieqiUserInfo=$userInfo; jieqiVisitInfo=$visitInfo"
        Wenku8Hosts.MIRRORS.forEach { base ->
            runCatching {
                cookieStore.saveRaw("$base/".toHttpUrl(), rawCookie)
            }
        }
        cookieStore.persist()
    }
}
