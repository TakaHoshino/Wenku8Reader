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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

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
class Wenku8Client(context: Context) {

    private val appContext = context.applicationContext
    private val cookieStore = CookieStore(appContext)
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
        private const val BASE = "https://www.wenku8.net"
        private const val DL = "https://dl.wenku8.com"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        private val GB18030: Charset = Charset.forName("GB18030")
        private val RATE_CODES = setOf(403, 429, 500, 502, 503, 504)

        /** Mirror hosts; used to fall back when a host returns a Cloudflare block. */
        private val MIRRORS = listOf(
            "https://www.wenku8.net",
            "https://www.wenku8.cc",
            "https://www.wenku8.com",
        )

        /** Random Android Chrome UA for low-trust requests (mirrors the reference probe). */
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
            val chrome = 100 + Random.nextInt(41)
            return "Mozilla/5.0 (Linux; Android ${os.random()}; ${device.random()}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome.0.0.0 Mobile Safari/537.36"
        }
    }

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
            .browserHeaders("$BASE/login.php")
            .post(gbkForm(pairs))
            .build()
        return execute(req, retries = 2)
    }

    // ------------------------------------------------------------------ //
    // account
    // ------------------------------------------------------------------ //
    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        !getHtml("$BASE/index.php").contains("frmlogin")
    }

    suspend fun login(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val resp = postForm(
            "$BASE/login.php?do=submit" +
                "&jumpurl=http%3A%2F%2Fwww.wenku8.net%2Findex.php",
            listOf(
                "username" to user,
                "password" to pass,
                "usecookie" to "315360000",
                "action" to "login",
                "submit" to "\u00A0\u00A0\u767B\u00A0\u00A0\u5F55\u00A0",
            )
        )
        val bytes = readBytes(resp)
        val html = String(bytes, GB18030)
        val ok = !html.contains("frmlogin")
        if (ok) {
            username = user
            cookieStore.persist()
        }
        ok
    }

    suspend fun logout() {
        runCatching { getBytes("$BASE/logout.php") }
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
            "$BASE/so.php",
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
        Parsers.parseBookInfo(getHtml("$BASE/book/$id.htm"), id)
    }

    suspend fun chapters(bookId: Int, groupId: Int): List<Volume> = withContext(Dispatchers.IO) {
        Parsers.parseChapterIndex(getHtml("$BASE/novel/$groupId/$bookId/index.htm"))
    }

    suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent =
        withContext(Dispatchers.IO) {
            Parsers.parseChapter(getHtml("$BASE/novel/$gid/$bookId/$cid.htm"))
        }

    suspend fun bookcase(): List<BookcaseItem> = withContext(Dispatchers.IO) {
        Parsers.parseBookcase(getHtml("$BASE/modules/article/bookcase.php"))
    }

    /**
     * 首页栏目。快路径：默认 UA 直连（常规情况毫秒级返回）；
     * 若直连失败或返回疑似 CF 挑战页（解析为空），自动升级到
     * WebView → Cronet → OkHttp 随机 UA 的三级 Cloudflare 绕过栈。
     */
    suspend fun homepage(): List<HomeSection> = withContext(Dispatchers.IO) {
        val direct = runCatching { Parsers.parseHomepage(getHtml("$BASE/index.php")) }
            .getOrDefault(emptyList())
        if (direct.isNotEmpty()) return@withContext direct

        val sections = fetchWithBypass(
            urlFor = { h -> "$h/index.php" },
            parse = { html -> Parsers.parseHomepage(html).takeIf { it.isNotEmpty() } },
        )
        sections ?: throw IOException("首页获取失败：直连与三级绕过均未命中")
    }

    /** All tag names. WebView (real browser engine) first, then Cronet, then OkHttp mirrors. */
    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<String>()
        val tags = fetchWithBypass(
            urlFor = { h -> "$h/modules/article/tags.php" },
            parse = { html -> Parsers.parseTags(html).takeIf { it.isNotEmpty() } },
            steps = steps,
        )
        tags ?: throw IOException(steps.joinToString("；").ifEmpty { "未能获取分类" })
    }

    /** Books under a tag (first page). WebView, then Cronet, then OkHttp mirrors. */
    suspend fun tagBooks(tag: String): List<HomeBook> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(tag, "GBK")
        fetchWithBypass(
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
     * Cloudflare 三级绕过抓取（与 tags/tagBooks 同栈）：
     * WebView（真浏览器跑 CF JS 挑战）→ Cronet（TLS 指纹）→ OkHttp 随机 Android UA，
     * 逐镜像尝试。[parse] 返回 null 表示该响应无有效内容，进入下一层/下一镜像。
     * [steps] 可选，收集各层诊断信息供错误提示。返回 null 表示全部失败。
     */
    private suspend fun <T> fetchWithBypass(
        urlFor: (String) -> String,
        parse: (String) -> T?,
        steps: MutableList<String>? = null,
    ): T? {
        for (h in MIRRORS) {
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
            for (h in MIRRORS) {
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
        for (h in MIRRORS) {
            val parsed = runCatching {
                parse(getHtml(urlFor(h), retries = 1, ua = randomAndroidUa()))
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
            .addHeader("User-Agent", randomAndroidUa())
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
     */
    private suspend fun webViewGet(url: String): String? = withTimeoutOrNull(15000) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val webView = runCatching { WebView(appContext) }.getOrNull()
                if (webView == null) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                runCatching {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.userAgentString = randomAndroidUa()
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
