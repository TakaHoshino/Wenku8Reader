package com.hoshino.wenku8reader.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Persists cookies to SharedPreferences (mirrors cookies.json in the Python tool). */
class CookieStore(context: Context) : CookieJar {

    private val prefs =
        context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    init {
        load()
    }

    private fun load() {
        val raw = prefs.getString("data", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cookie = Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .domain(o.getString("domain"))
                    .path(o.optString("path", "/"))
                    .build()
                val hostList = cookies.getOrPut(o.getString("host")) { mutableListOf() }
                hostList.removeAll { it.name == cookie.name && it.path == cookie.path }
                hostList.add(cookie)
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
        val hostList = cookies.getOrPut(url.host) { mutableListOf() }
        for (c in list) {
            // Replace an existing cookie with the same name+path so the newest
            // value wins (the server picks the first match of a duplicated name).
            hostList.removeAll { it.name == c.name && it.path == c.path }
            if (c.expiresAt > System.currentTimeMillis()) {
                hostList.add(c)
            }
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies[url.host]?.toList() ?: emptyList()

    @Synchronized
    fun persist() {
        val arr = JSONArray()
        for ((host, list) in cookies) {
            for (c in list) {
                arr.put(JSONObject()
                    .put("host", host)
                    .put("name", c.name)
                    .put("value", c.value)
                    .put("domain", c.domain ?: "")
                    .put("path", c.path))
            }
        }
        prefs.edit().putString("data", arr.toString()).apply()
    }

    /**
     * 保存 WebView 解出 Cloudflare 挑战后写入的原始 Cookie 头（"k=v; k2=v2"），
     * 典型如 `cf_clearance` / `__cf_bm`。持久化后 OkHttp / Cronet 请求可直接复用，
     * 无需每次都重跑 WebView 挑战。
     */
    @Synchronized
    fun saveRaw(url: HttpUrl, rawHeader: String) {
        if (rawHeader.isBlank()) return
        val hostList = cookies.getOrPut(url.host) { mutableListOf() }
        rawHeader.split(";").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    hostList.removeAll { it.name == name }
                    hostList.add(
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(url.host)
                            .path("/")
                            .build()
                    )
                }
            }
        }
        persist()
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().remove("data").apply()
    }
}
