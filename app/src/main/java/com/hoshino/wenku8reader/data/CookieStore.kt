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

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().remove("data").apply()
    }
}
