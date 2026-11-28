package com.hoshino.wenku8reader.data

import okhttp3.Cookie

/**
 * 按 LightNovelReader 的固定顺序组出单个 `Cookie` 请求头。
 *
 * 参考项目直接使用静态 Cookie Map；本项目复用相同的手工头格式，但值来自持久化
 * CookieStore，避免把已过期的 PHPSESSID / cf_clearance 写死在源码中。
 */
private val WENKU8_COOKIE_ORDER = listOf(
    "PHPSESSID",
    "jieqiUserInfo",
    "jieqiVisitInfo",
    "cf_clearance",
    "HMACCOUNT",
    "Hm_lvt_d72896ddbf8d27c750e3b365ea2fc902",
    "Hm_lpvt_d72896ddbf8d27c750e3b365ea2fc902",
)

internal fun buildCookieHeader(cookies: List<Cookie>): String =
    buildMap {
        cookies.forEach { cookie -> put(cookie.name to cookie.path, cookie) }
    }.values
        .sortedWith(
            compareBy<Cookie>(
                {
                    WENKU8_COOKIE_ORDER.indexOf(it.name)
                        .let { index -> if (index < 0) Int.MAX_VALUE else index }
                },
                { it.name },
                { it.path },
            )
        )
        .joinToString("; ") { "${it.name}=${it.value}" }
