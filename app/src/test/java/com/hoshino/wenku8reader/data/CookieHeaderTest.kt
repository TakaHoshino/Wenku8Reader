package com.hoshino.wenku8reader.data

import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Test

class CookieHeaderTest {
    @Test
    fun `Cookie 头采用参考项目固定顺序`() {
        val cookies = listOf(
            cookie("cf_clearance", "cf"),
            cookie("jieqiVisitInfo", "visit"),
            cookie("PHPSESSID", "session"),
            cookie("jieqiUserInfo", "user"),
        )

        assertEquals(
            "PHPSESSID=session; jieqiUserInfo=user; jieqiVisitInfo=visit; cf_clearance=cf",
            buildCookieHeader(cookies),
        )
    }

    @Test
    fun `同名同路径 Cookie 只保留最后一个`() {
        assertEquals(
            "PHPSESSID=new",
            buildCookieHeader(listOf(cookie("PHPSESSID", "old"), cookie("PHPSESSID", "new"))),
        )
    }

    private fun cookie(name: String, value: String): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain("www.wenku8.net")
            .path("/")
            .build()
}
