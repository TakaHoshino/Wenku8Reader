package com.hoshino.wenku8reader.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.hoshino.wenku8reader.data.local.HtmlDiskCache

/**
 * Minimal wenku8.net (jieqi CMS) API client.
 * All methods are suspend and safe to call from any dispatcher.
 *
 * Networking details:
 * - The site declares `charset=gbk` but actually emits GB18030 (a strict superset),
 *   so all pages are decoded with GB18030 to avoid mojibake on `•・〜` etc.
 * - Requests carry browser-like navigation headers (Sec-Fetch-*, Accept, etc.) to
 *   reduce the chance of triggering Cloudflare/anti-bot challenges.
 * - A global adaptive throttle plus exponential back-off handles 429/5xx.
 */
class Wenku8Client(
    context: Context,
    /** 用户选定的主站镜像（设置页可切换）；缺省用 wenku8.net */
    private val primaryMirrorProvider: () -> String = { DEFAULT_BASE },
    /** 内置账号凭据（供需登录接口 ensureLoggedIn 静默登录用） */
    private val defaultCredentials: () -> Pair<String, String>? = { null },
) {

    private val appContext = context.applicationContext
    private val cookieStore = CookieStore(appContext)
    private val htmlCache = HtmlDiskCache(appContext)
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .cookieJar(cookieStore)
        .build()

    /** Chromium network stack; its TLS fingerprint usually bypasses Cloudflare blocks. */
    private val cronetEngine: CronetEngine? by lazy {
        runCatching { CronetEngine.Builder(appContext).build() }.getOrNull()
    }
    private val cronetExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var lastRequest = 0L
    @Volatile private var lastSearch = 0L
    @Volatile private var rate = 1.0
    private val lock = Any()

    var username: String? = null
        private set

    companion object {
        private const val DEFAULT_BASE = "https://www.wenku8.cc"
        private const val DL = "https://dl.wenku8.com"

        // ---- 官方 App API（无网页 CF 验证，参考 LightNovelReader）----
        private const val APP_API_OFFICIAL = "http://app.wenku8.com/android.php"
        private const val APP_API_RELAY = "https://wenku8-relay.mewx.org"
        private const val APP_VER = "1.24-pico-mochi"
        private const val APP_UA =
            "Dalvik/2.1.0 (Linux; U; Android 15; 23114RD76B Build/AQ3A.240912.001)"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        private val GB18030: Charset = Charset.forName("GB18030")
        private val RATE_CODES = setOf(403, 429, 500, 502, 503, 504)

        // ---- 磁盘缓存 TTL（见 HtmlDiskCache）----
        const val TTL_HOME = 60L * 60 * 1000                 // 首页 1 小时
        const val TTL_BOOK = 7L * 24 * 60 * 60 * 1000        // 详情/目录 7 天
        const val TTL_CHAPTER = 30L * 24 * 60 * 60 * 1000    // 章节正文 30 天
        const val TTL_TAG_BOOKS = 24L * 60 * 60 * 1000       // 标签书单 1 天

        /**
         * 内置分类清单（标准 wenku8 标签，参考 LightNovelReader 的 tagList）。
         * 直接作为「标签」页的分类来源：秒回、无需登录/网络；
         * 每个分类下的书籍仍按需在线抓取（tagBooks）。
         */
        private val BUILT_IN_TAGS = listOf(
            "校园", "青春", "恋爱", "治愈", "群像", "竞技", "音乐", "美食", "旅行", "欢乐向",
            "经营", "职场", "斗智", "脑洞", "宅文化", "穿越", "奇幻", "魔法", "异能", "战斗",
            "科幻", "机战", "战争", "冒险", "龙傲天", "悬疑", "犯罪", "复仇", "黑暗", "猎奇",
            "惊悚", "间谍", "末日", "游戏", "大逃杀", "青梅竹马", "妹妹", "女儿", "JK", "JC",
            "大小姐", "性转", "伪娘", "人外", "后宫", "百合", "耽美", "NTR", "女性视角",
        )

        /** Mirror hosts; used to fall back when a host returns a Cloudflare block. */
        private val MIRRORS = listOf(
            "https://www.wenku8.cc",
            "https://www.wenku8.net",
            "https://www.wenku8.com",
        )

        /** 随机 Android Chrome UA（参考 LightNovelReader：随机 Build ID 与子版本）。 */
        private fun randomAndroidUa(): String {
            val os = listOf("8.1.0", "9", "10", "11", "12", "13", "14", "15")
            val device = listOf(
                "Pixel 7; Build/TQ3A.230805.001",
                "Pixel 6; Build/TQ3A.230805.001",
                "SM-G991B; Build/SP1A.210812.016",
                "SM-G998B; Build/SP1A.210812.016",
                "SM-S9010; Build/TD1A.220804.031",
                "Redmi K40; Build/RKQ1.200826.002",
                "Xiaomi 12; Build/SKQ1.211006.001",
                "OPPO Reno6; Build/RP1A.200720.011",
                "vivo X60; Build/RP1A.200720.012",
            )
            // Chrome 主版本 100-140，子版本/构建/补丁号随机（增大 UA 熵）
            val chrome = 100 + Random.nextInt(41)
            val minor = Random.nextInt(0, 4000)
            val build = Random.nextInt(0, 200)
            val patch = Random.nextInt(0, 150)
            return "Mozilla/5.0 (Linux; Android ${os.random()}; ${device.random()}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome.0.$minor.$build Mobile Safari/537.$patch"
        }
    }

    // ------------------------------------------------------------------ //
    // 主镜像选择（设置页可切换）+ cf_clearance UA 绑定
    // ------------------------------------------------------------------ //

    /** 用户选定的主镜像优先，其余镜像按固定顺序兜底。 */
    private val mirrors: List<String>
        get() = buildList {
            val primary = primaryMirrorProvider().ifBlank { DEFAULT_BASE }
            add(primary)
            MIRRORS.filter { it != primary }.forEach { add(it) }
        }

    private val base: String get() = mirrors.first()

    /**
     * WebView 解出 Cloudflare 挑战时使用的 UA（按主机记录）。
     * cf_clearance 令牌与该 UA 绑定，后续 OkHttp/Cronet 复用令牌时必须使用同一 UA。
     */
    private val challengeUa = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun uaFor(url: String): String =
        runCatching { url.toHttpUrl().host }.getOrNull()?.let { challengeUa[it] }
            ?: randomAndroidUa()

    /** 切换主镜像/清空会话时调用：清空全部 Cookie 与 UA 绑定。 */
    fun clearCookies() {
        challengeUa.clear()
        cookieStore.clear()
    }

    // ------------------------------------------------------------------ //
    // 内存缓存（参考 LightNovelReader 的 2h Cache）
    // ------------------------------------------------------------------ //
    private val infoCache = TimedCache(2 * 60 * 60 * 1000L)
    private val tocCache = TimedCache(2 * 60 * 60 * 1000L)
    private val chapterCache = TimedCache(30 * 60 * 1000L)

    /** App API 串行限流（官方 App 行为：同时间仅一个请求）。 */
    private val appApiSemaphore = Semaphore(1)

    // ------------------------------------------------------------------ //
    // pacing / retry
    // ------------------------------------------------------------------ //
    private fun adjustRate(ok: Boolean) {
        synchronized(lock) {
            rate = if (ok) max(1.0, rate * 0.85) else min(8.0, rate * 2)
        }
    }

    private suspend fun pace(ms: Long) {
        var sleep = 0L
        synchronized(lock) {
            val wait = lastRequest + (ms * rate).toLong() - System.currentTimeMillis()
            if (wait > 0) sleep = wait
            lastRequest = System.currentTimeMillis()
        }
        if (sleep > 0) delay(sleep)
    }

    private suspend fun execute(req: Request, retries: Int = 3): Response {
        var attempt = 0
        while (true) {
            pace(600)
            val resp = withContext(Dispatchers.IO) { okHttp.newCall(req).execute() }
            if (resp.code in RATE_CODES) {
                adjustRate(false)
                if (attempt < retries) {
                    resp.close()
                    val backoff = min(1500L * (1L shl attempt), 30000L)
                    delay(backoff)
                    attempt++
                    continue
                }
                resp.close()
                val msg = if (resp.code == 429) {
                    "站点限流(HTTP 429)，请稍后重试"
                } else {
                    "请求失败(HTTP ${resp.code})，请稍后重试"
                }
                throw IOException(msg)
            }
            adjustRate(true)
            return resp
        }
    }

    private suspend fun readBytes(resp: Response): ByteArray =
        withContext(Dispatchers.IO) { resp.use { it.body?.bytes() ?: ByteArray(0) } }

    // ------------------------------------------------------------------ //
    // low-level requests
    // ------------------------------------------------------------------ //
    private fun Request.Builder.browserHeaders(
        referer: String?,
        ua: String = UA,
    ): Request.Builder {
        header("User-Agent", ua)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        header("Cache-Control", "max-age=0")
        header("Upgrade-Insecure-Requests", "1")
        header("Sec-Fetch-Dest", "document")
        header("Sec-Fetch-Mode", "navigate")
        header("Sec-Fetch-Site", "none")
        header("Sec-Fetch-User", "?1")
        referer?.let { header("Referer", it) }
        return this
    }

    private suspend fun getBytes(url: String, retries: Int = 3, ua: String = UA): ByteArray {
        val req = Request.Builder()
            .url(url)
            .browserHeaders(refererFor(url), ua)
            .build()
        return readBytes(execute(req, retries))
    }

    /** Referer matching the request's own host (mirror-safe). */
    private fun refererFor(url: String): String? = runCatching {
        val h = url.toHttpUrl()
        "${h.scheme}://${h.host}/"
    }.getOrNull()

    /** Fetches a page and decodes it as GB18030 on the IO dispatcher. */
    private suspend fun getHtml(url: String, retries: Int = 3, ua: String = UA): String =
        withContext(Dispatchers.IO) {
            val bytes = getBytes(url, retries, ua)
            String(bytes, GB18030)
        }

    /**
     * 带本地磁盘缓存的抓取：命中且未过期直接返回（免二次加载）。
     * 仅缓存「非 CF 挑战页/非登录页」的有效内容，避免缓存到垃圾页。
     */
    private suspend fun getHtmlCached(
        url: String,
        ttlMs: Long,
        retries: Int = 3,
        ua: String = UA,
    ): String {
        htmlCache.get(url, ttlMs)?.let { return it }
        val html = getHtml(url, retries, ua)
        if (html.isNotBlank() && !isChallenge(html) && !html.contains("login.php")) {
            htmlCache.put(url, html)
        }
        return html
    }

    private fun gbkForm(pairs: List<Pair<String, String>>): RequestBody {
        val sb = StringBuilder()
        pairs.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append('&')
            sb.append(URLEncoder.encode(k, "GBK"))
                .append('=')
                .append(URLEncoder.encode(v, "GBK"))
        }
        return sb.toString().toByteArray(GB18030)
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    private suspend fun postForm(url: String, pairs: List<Pair<String, String>>): Response {
        val req = Request.Builder()
            .url(url)
            .browserHeaders("$base/login.php")
            .post(gbkForm(pairs))
            .build()
        return execute(req, retries = 2)
    }

    // ------------------------------------------------------------------ //
    // account
    // ------------------------------------------------------------------ //

    /** 是否持有有效会话：以 jieqiUserInfo 会话 Cookie 为准（旧版 frmlogin 标记已随页面改版失效）。 */
    private fun hasSession(): Boolean =
        cookieStore.loadForRequest(base.toHttpUrl()).any {
            it.name == "jieqiUserInfo" && it.value.isNotBlank()
        }

    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) { hasSession() }

    /** 确保已登录（供 tags/bookcase 等需登录接口调用）。 */
    suspend fun ensureLoggedIn(): Boolean {
        if (isLoggedIn()) return true
        val creds = defaultCredentials() ?: return false
        return login(creds.first, creds.second)
    }

    suspend fun login(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val resp = postForm(
            "$base/login.php?do=submit" +
                "&jumpurl=${URLEncoder.encode("$base/index.php", "UTF-8")}",
            listOf(
                "username" to user,
                "password" to pass,
                "usecookie" to "315360000",
                "action" to "login",
                "submit" to "\u00A0\u00A0\u767B\u00A0\u00A0\u5F55\u00A0",
            )
        )
        readBytes(resp)
        // 成功与否以是否拿到 jieqiUserInfo 会话 Cookie 为准
        val ok = hasSession()
        if (ok) {
            username = user
            cookieStore.persist()
        }
        ok
    }

    suspend fun logout() {
        runCatching { getBytes("$base/logout.php") }
        cookieStore.clear()
        username = null
    }

    // ------------------------------------------------------------------ //
    // read operations
    // ------------------------------------------------------------------ //
    suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> = withContext(Dispatchers.IO) {
        var sleep = 0L
        synchronized(lock) {
            val wait = lastSearch + 5000 - System.currentTimeMillis()
            if (wait > 0) sleep = wait
            lastSearch = System.currentTimeMillis()
        }
        if (sleep > 0) delay(sleep)

        val type = if (byAuthor) "author" else "articlename"
        val resp = postForm(
            "$base/so.php",
            listOf(
                "searchtype" to type,
                "searchkey" to keyword,
                "charset" to "gbk",
                "Submit" to "\u8F7B\u5C0F\u8BF4\u641C\u7D22",
            )
        )
        val finalUrl = resp.request.url.toString()
        val html = String(readBytes(resp), GB18030)

        // search-too-frequent error page -> wait and retry once
        if (html.contains("两次搜索的间隔时间") || html.contains("出现错误")) {
            delay(5000)
            return@withContext search(keyword, byAuthor)
        }

        val m = Regex("/book/(\\d+)\\.htm").find(finalUrl)
        if (m != null) {
            val id = m.groupValues[1].toIntOrNull() ?: return@withContext emptyList()
            val info = Parsers.parseBookInfo(html, id)
            return@withContext listOf(SearchResult(id, info.title))
        }
        Parsers.parseSearchResults(html)
    }

    suspend fun bookInfo(id: Int): BookInfo = withContext(Dispatchers.IO) {
        infoCache.get("info_$id") ?: run {
            // 网页优先（含磁盘缓存 + cf_clearance 快路径）；失败/空则走官方 App API（免 CF）
            val web = runCatching {
                Parsers.parseBookInfo(getHtmlCached("$base/book/$id.htm", TTL_BOOK), id)
            }.getOrNull()
            val info = web?.takeIf { it.title.isNotBlank() }
                ?: appApiBookInfo(id)
                ?: throw IOException("书籍信息获取失败")
            infoCache.put("info_$id", info)
            info
        }
    }

    suspend fun chapters(bookId: Int, groupId: Int): List<Volume> = withContext(Dispatchers.IO) {
        tocCache.get("toc_$bookId") ?: run {
            val web = runCatching {
                Parsers.parseChapterIndex(
                    getHtmlCached("$base/novel/$groupId/$bookId/index.htm", TTL_BOOK)
                )
            }.getOrNull()
            val volumes = web?.takeIf { it.isNotEmpty() }
                ?: appApiVolumes(bookId)
                ?: throw IOException("章节目录加载失败")
            tocCache.put("toc_$bookId", volumes)
            volumes
        }
    }

    suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent =
        withContext(Dispatchers.IO) {
            chapterCache.get("chap_${bookId}_$cid") ?: run {
                val web = runCatching {
                    Parsers.parseChapter(
                        getHtmlCached("$base/novel/$gid/$bookId/$cid.htm", TTL_CHAPTER)
                    )
                }.getOrNull()
                val chapter = web?.takeIf { it.text.isNotBlank() || it.images.isNotEmpty() }
                    ?: appApiChapter(bookId, cid)
                    ?: throw IOException("章节加载失败")
                chapterCache.put("chap_${bookId}_$cid", chapter)
                chapter
            }
        }

    // ------------------------------------------------------------------ //
    // 官方 App API（免 CF，参考 LightNovelReader 的 Wenku8AppDataSource）
    // ------------------------------------------------------------------ //

    /** POST 官方 App API；官方地址失败后尝试社区中继。串行限流 + 随机延迟。 */
    private suspend fun appApiGet(request: String): String? = withContext(Dispatchers.IO) {
        appApiSemaphore.withPermit {
            for (host in listOf(APP_API_OFFICIAL, APP_API_RELAY)) {
                val text = runCatching {
                    var attempt = 0
                    while (true) {
                        val body = gbkForm(
                            listOf(
                                "request" to Base64.getEncoder()
                                    .encodeToString(request.toByteArray()),
                                "timetoken" to System.currentTimeMillis().toString(),
                                "appver" to APP_VER,
                            )
                        )
                        val req = Request.Builder()
                            .url(host)
                            .post(body)
                            .header("User-Agent", APP_UA)
                            .header("Accept", "*/*")
                            .build()
                        val resp = execute(req, retries = 1)
                        val text = readBytes(resp).toString(GB18030)
                        if (text.isNotBlank()) return@runCatching text
                        if (attempt >= 2) break
                        attempt++
                        delay(2500L * attempt)
                    }
                    ""
                }.getOrNull()
                if (!text.isNullOrBlank()) {
                    // App API 官方行为：请求间随机 1.5~2s 延迟，避免被限流
                    delay(Random.nextLong(1500, 2001))
                    return@withPermit text
                }
            }
            null
        }
    }

    private suspend fun appApiBookInfo(id: Int): BookInfo? {
        val meta = appApiGet("action=book&do=meta&aid=$id&t=0") ?: return null
        val info = Parsers.parseAppBookInfo(meta, id) ?: return null
        // 简介在 do=intro 接口：响应正文即简介纯文本
        val intro = appApiGet("action=book&do=intro&aid=$id&t=0")
        val description = intro?.let { html ->
            html.substringAfter("<body>", html).substringBefore("</body>")
                .replace(Regex("<[^>]+>"), "").trim()
        }?.takeIf { it.isNotBlank() } ?: ""
        return info.copy(description = description)
    }

    private suspend fun appApiVolumes(bookId: Int): List<Volume>? {
        val list = appApiGet("action=book&do=list&aid=$bookId&t=0") ?: return null
        return Parsers.parseAppVolumes(list)
    }

    private suspend fun appApiChapter(bookId: Int, cid: String): ChapterContent? {
        val text = appApiGet("action=book&do=text&aid=$bookId&cid=$cid&t=0") ?: return null
        return Parsers.parseAppChapter(text)
    }

    suspend fun bookcase(): List<BookcaseItem> = withContext(Dispatchers.IO) {
        Parsers.parseBookcase(getHtml("$base/modules/article/bookcase.php"))
    }

    /**
     * 首页栏目。快路径：默认 UA 直连（常规情况毫秒级返回）；
     * 若直连失败或返回疑似 CF 挑战页（解析为空），自动升级到
     * WebView → Cronet → OkHttp 随机 UA 的三级 Cloudflare 绕过栈。
     */
    suspend fun homepage(): List<HomeSection> = withContext(Dispatchers.IO) {
        // 快路径：cf_clearance cookie-first 逐镜像直连（磁盘缓存 1 小时，避免二次加载）
        tryDirect(
            urlFor = { h -> "$h/index.php" },
            parse = { html -> Parsers.parseHomepage(html).takeIf { it.isNotEmpty() } },
            ttlMs = TTL_HOME,
        )?.let { return@withContext it }

        val sections = fetchWithBypass(
            urlFor = { h -> "$h/index.php" },
            parse = { html -> Parsers.parseHomepage(html).takeIf { it.isNotEmpty() } },
        )
        sections ?: throw IOException("首页获取失败：直连与三级绕过均未命中")
    }

    /**
     * 分类清单：直接返回内置标准标签（秒回，无需登录/网络）。
     * 书籍仍按分类在线抓取，故此处先确保登录（供后续 tagBooks 使用）。
     */
    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        BUILT_IN_TAGS
    }

    /** Books under a tag (first page). 需登录；快路径 → WebView → Cronet → OkHttp mirrors。 */
    suspend fun tagBooks(tag: String): List<HomeBook> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val query = URLEncoder.encode(tag, "GBK")
        tryDirect(
            urlFor = { h -> "$h/modules/article/tags.php?t=$query&v=1" },
            parse = { html -> Parsers.parseBookList(html).takeIf { it.isNotEmpty() } },
            ttlMs = TTL_TAG_BOOKS,
        ) ?: fetchWithBypass(
            urlFor = { h -> "$h/modules/article/tags.php?t=$query&v=1" },
            parse = { html -> Parsers.parseBookList(html).takeIf { it.isNotEmpty() } },
        ) ?: emptyList()
    }

    /** Rough classification of an unexpected page body, for diagnostics. */
    private fun classify(html: String): String = when {
        isChallenge(html) -> "疑似CF挑战页"
        html.contains("frmlogin") || html.contains("login.php") || html.contains("用户登录") -> "疑似登录页"
        else -> "未知内容"
    }

    private fun isChallenge(html: String): Boolean =
        html.contains("challenge-platform") || html.contains("cf-challenge") ||
            html.contains("cf_chl") || html.contains("cf-chl")

    /**
     * 快路径（参考 LightNovelReader 的 cookie-first 思路）：直接用 OkHttp 携带
     * 持久化的 cf_clearance 与绑定 UA 直连各镜像；已有有效令牌时一次通过，无需跑 WebView。
     * [ttlMs] > 0 时启用磁盘缓存（见 getHtmlCached），避免二次加载。
     * 返回 null 表示全部镜像直连均无有效内容（此时才升级到三级绕过栈）。
     */
    private suspend fun <T> tryDirect(
        urlFor: (String) -> String,
        parse: (String) -> T?,
        ttlMs: Long = 0L,
    ): T? {
        for (h in mirrors) {
            val ua = uaFor(urlFor(h))
            val parsed = runCatching {
                parse(getHtmlCached(urlFor(h), ttlMs, retries = 1, ua = ua))
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    /**
     * Cloudflare 三级绕过抓取（与 tags/tagBooks 同栈）：
     * WebView（真浏览器跑 CF JS 挑战，解出后持久化 cf_clearance）→ Cronet（TLS 指纹）
     * → OkHttp 随机 Android UA，逐镜像尝试。[parse] 返回 null 表示该响应无有效内容。
     * [steps] 可选，收集各层诊断信息供错误提示。返回 null 表示全部失败。
     */
    private suspend fun <T> fetchWithBypass(
        urlFor: (String) -> String,
        parse: (String) -> T?,
        steps: MutableList<String>? = null,
    ): T? {
        for (h in mirrors) {
            val html = webViewGet(urlFor(h))
            if (html != null) {
                val parsed = parse(html)
                if (parsed != null) return parsed
                steps?.add("WebView $h 无有效内容(${classify(html)})")
            } else {
                steps?.add("WebView $h 失败/超时")
            }
        }
        val engine = cronetEngine
        if (engine != null) {
            for (h in mirrors) {
                val html = cronetGet(engine, urlFor(h))
                if (html != null) {
                    val parsed = parse(html)
                    if (parsed != null) return parsed
                    steps?.add("Cronet $h 无有效内容(${classify(html)})")
                } else {
                    steps?.add("Cronet $h 失败/超时")
                }
            }
        } else {
            steps?.add("Cronet 初始化失败")
        }
        for (h in mirrors) {
            // 若该主机已有 cf_clearance（WebView 解出后持久化），复用其绑定 UA 直接通过
            val ua = uaFor(urlFor(h))
            val parsed = runCatching {
                parse(getHtml(urlFor(h), retries = 1, ua = ua))
            }.getOrNull()
            if (parsed != null) return parsed
            steps?.add("OkHttp $h 无有效内容")
        }
        return null
    }

    /** GET via Cronet, carrying the app's session cookies; returns null on failure/timeout. */
    private suspend fun cronetGet(engine: CronetEngine, url: String): String? =
        withContext(Dispatchers.IO) {
            val latch = CountDownLatch(1)
        val result = AtomicReference<ByteArray?>(null)
        val readBuffer = ByteBuffer.allocateDirect(64 * 1024)
        val body = ByteArrayOutputStream()
        val httpUrl = url.toHttpUrl()
        val cookieHeader = cookieStore.loadForRequest(httpUrl)
            .joinToString("; ") { "${it.name}=${it.value}" }

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String?,
            ) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                runCatching {
                    info.allHeadersAsList
                        .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                        .mapNotNull { (_, v) ->
                            runCatching { Cookie.parse(httpUrl, v) }.getOrNull()
                        }
                        .takeIf { it.isNotEmpty() }
                        ?.let { cookieStore.saveFromResponse(httpUrl, it) }
                }
                request.read(readBuffer)
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer,
            ) {
                byteBuffer.flip()
                val arr = ByteArray(byteBuffer.remaining())
                byteBuffer.get(arr)
                body.write(arr)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                result.set(body.toByteArray())
                latch.countDown()
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: CronetException,
            ) {
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                latch.countDown()
            }
        }

        val builder = engine.newUrlRequestBuilder(url, callback, cronetExecutor)
            .setHttpMethod("GET")
            .addHeader("User-Agent", uaFor(url))
            .addHeader(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            )
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Cache-Control", "max-age=0")
            .addHeader("Upgrade-Insecure-Requests", "1")
            .addHeader("Sec-Fetch-Dest", "document")
            .addHeader("Sec-Fetch-Mode", "navigate")
            .addHeader("Sec-Fetch-Site", "none")
            .addHeader("Sec-Fetch-User", "?1")
        refererFor(url)?.let { builder.addHeader("Referer", it) }
        if (cookieHeader.isNotEmpty()) builder.addHeader("Cookie", cookieHeader)
        val request = builder.build()
        request.start()
        if (!latch.await(8, TimeUnit.SECONDS)) {
            request.cancel()
        }
        result.get()?.let { String(it, GB18030) }
    }

    /**
     * Loads the page in a hidden WebView so Cloudflare's JS challenge runs like in a
     * real browser, then reads back the rendered DOM. Carries the app's session cookies.
     *
     * 挑战通过后会做两件事（参考 LightNovelReader 的 cf_clearance 思路）：
     * 1. 记录本次使用的 UA（cf_clearance 与该 UA 绑定，后续复用需一致）；
     * 2. 把 WebView 写入的 Cookie（含 cf_clearance / __cf_bm）持久化到 CookieStore，
     *    之后 OkHttp/Cronet 直接带令牌请求，无需每次重跑 WebView。
     */
    private suspend fun webViewGet(url: String): String? = withTimeoutOrNull(15000) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val webView = runCatching { WebView(appContext) }.getOrNull()
                if (webView == null) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                val usedUa = randomAndroidUa()
                val host = runCatching { url.toHttpUrl().host }.getOrNull()
                runCatching {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.userAgentString = usedUa
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                    runCatching {
                        val httpUrl = url.toHttpUrl()
                        val cookieHeader = cookieStore.loadForRequest(httpUrl)
                            .joinToString("; ") { "${it.name}=${it.value}" }
                        if (cookieHeader.isNotEmpty()) {
                            @Suppress("DEPRECATION")
                            CookieManager.getInstance().setCookie(url, cookieHeader)
                        }
                    }
                    var attempts = 0
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(
                                "document.documentElement.outerHTML",
                                object : ValueCallback<String> {
                                    override fun onReceiveValue(html: String?) {
                                        val decoded = runCatching {
                                            JSONTokener(html).nextValue() as String
                                        }.getOrNull()
                                        attempts++
                                        // Skip challenge pages and wait for Cloudflare's auto-redirect.
                                        if (decoded != null && (!isChallenge(decoded) || attempts >= 3)) {
                                            // 挑战已通过：记录 UA 并持久化 cf_clearance 等 Cookie
                                            if (host != null) challengeUa[host] = usedUa
                                            val finishedUrl = url
                                            if (finishedUrl != null) {
                                                runCatching {
                                                    val wvCookies = CookieManager.getInstance()
                                                        .getCookie(finishedUrl)
                                                    if (!wvCookies.isNullOrBlank()) {
                                                        cookieStore.saveRaw(finishedUrl.toHttpUrl(), wvCookies)
                                                    }
                                                }
                                            }
                                            runCatching { webView.destroy() }
                                            if (cont.isActive) cont.resume(decoded)
                                        }
                                    }
                                }
                            )
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            runCatching { webView.destroy() }
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    webView.loadUrl(url)
                }.onFailure {
                    runCatching { webView.destroy() }
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /** type: "txt"(GBK) | "utf8" | "big5" */
    suspend fun downloadFullTxt(id: Int, type: String): ByteArray =
        getBytes("$DL/down.php?type=$type&node=1&id=$id")
}

/**
 * 简单 TTL 内存缓存（参考 LightNovelReader 的 Cache）。
 * 只缓存成功结果；超时后下次访问自动重取。
 */
private class TimedCache(private val ttlMs: Long) {
    private val map = ConcurrentHashMap<String, Pair<Long, Any>>()

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> get(key: String): T? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.first > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.second as T
    }

    @Synchronized
    fun put(key: String, value: Any) {
        map[key] = System.currentTimeMillis() to value
    }

    @Synchronized
    fun clear() = map.clear()
}
