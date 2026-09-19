package com.hoshino.wenku8reader.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
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
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** 磁盘缓存上限（MB，设置页可调）；每次写入前同步，动态生效 */
    private val cacheMaxMbProvider: () -> Int = { 30 },
) {

    private val appContext = context.applicationContext
    private val cookieStore = CookieStore(appContext)
    private val htmlCache = HtmlDiskCache(appContext, cacheMaxMbProvider().toLong() * 1024 * 1024)
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .cookieJar(cookieStore)
        .build()

    /**
     * Chromium network stack; its TLS fingerprint usually bypasses Cloudflare blocks.
     * 用 [Lazy] 持有，便于 [close] 判断引擎是否真的创建过（避免 close 反而触发创建）。
     */
    private val cronetEngineLazy: Lazy<CronetEngine?> = lazy {
        runCatching { CronetEngine.Builder(appContext).build() }.getOrNull()
    }
    private val cronetEngine: CronetEngine? get() = cronetEngineLazy.value

    /**
     * Cronet 回调执行器。复用 IO 调度器而不是自建单线程池：
     * 自建线程池的生命周期与应用等长却从不 `shutdown()`（线程泄漏），
     * 复用调度器后无需再管理其释放。
     */
    private val cronetExecutor: Executor = Dispatchers.IO.asExecutor()

    /**
     * 统一限流组件。原先 `lastRequest` / `lastSearch` / `rate` 三个字段与
     * `pace()` / `adjustRate()` / 搜索内联节流散落在类中，读代码要跳半个文件
     * 才能拼出"一次请求究竟等了几次"。收敛为一个所有者后策略清晰；
     * 分层本身保留——全局请求间隔、自适应速率、搜索硬间隔语义不同，不能压成一个延时。
     */
    private val pacer = RatePacer(PACE_BASE_INTERVAL_MS, SEARCH_MIN_INTERVAL_MS)

    /**
     * 登录互斥锁：见 [ensureLoggedIn]。同一时刻只允许一个登录流程在跑，
     * 排队者进入后先复查登录态，不重复发请求。
     */
    private val loginMutex = Mutex()

    /**
     * 隐藏 WebView 互斥锁：Cloudflare 兜底同一时刻只允许一个 WebView 在跑。
     *
     * 多个 WebView 同时解挑战会在主线程同时跑 JS，既拖慢首帧、抬高内存，
     * 也更容易被风控判定为异常；串行化后第二个请求通常直接复用刚拿到的
     * cf_clearance 走直连快路径，实际并不慢。
     */
    private val webViewMutex = Mutex()

    var username: String? = null
        private set

    companion object {
        /** 默认主站镜像（单一来源：[Wenku8Hosts]）。 */
        private const val DEFAULT_BASE = Wenku8Hosts.DEFAULT_BASE
        private const val DL = "https://dl.wenku8.com"

        /** 可用镜像；供设置页等直接消费，避免多处各自硬编码。 */
        val MIRRORS: List<String> get() = Wenku8Hosts.MIRRORS

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

        /** 登录页特征串：命中说明拿到的是登录页而非目标内容（不缓存、视为无效）。 */
        private const val LOGIN_PATH = "login.php"

        /** 站点限流错误页特征："两次搜索的间隔时间不得少于 5 秒"。 */
        private const val THROTTLE_MARK = "两次搜索的间隔时间"

        // ---- 搜索节流/重试（有界，避免无界递归）----
        private const val SEARCH_MIN_INTERVAL_MS = 5000L
        private const val SEARCH_MAX_ATTEMPTS = 3
        private const val SEARCH_RETRY_DELAY_MS = 5000L

        /** 全局请求间隔基数（毫秒），实际间隔 = 该值 × 自适应速率。 */
        private const val PACE_BASE_INTERVAL_MS = 600L

        /** 详情页/单书命中链接（`/book/{id}.htm`）。 */
        private val BOOK_URL = Regex("/book/(\\d+)\\.htm")

        /** Cronet 请求等待上限（秒）；超时后 cancel 并返回 null。 */
        private const val CRONET_TIMEOUT_SECONDS = 8L

        /** 单个隐藏 WebView 解挑战的等待上限（毫秒）。 */
        private const val WEBVIEW_TIMEOUT_MS = 15_000L

        /**
         * 等待 WebView 互斥锁的上限（毫秒）：比单个 WebView 的超时更长，
         * 保证"排队 + 执行"整体有界，排队的请求不会无限期挂着。
         */
        private const val WEBVIEW_LOCK_TIMEOUT_MS = 25_000L

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

    /**
     * 用户选定的主镜像优先，其余镜像按固定顺序兜底。
     *
     * 结果按主镜像缓存：`uaFor`/`tryDirect`/`fetchWithBypass` 每次请求都会读取该属性，
     * 原实现是 `get` 属性里 `buildList + filter` 重新构造，属纯无谓分配。
     * 仅在设置页切换主镜像（[primaryMirrorProvider] 返回值变化）时重建。
     */
    @Volatile private var cachedPrimary: String? = null
    @Volatile private var cachedMirrors: List<String> = emptyList()

    private val mirrors: List<String>
        get() {
            val primary = primaryMirrorProvider().ifBlank { DEFAULT_BASE }
            if (primary != cachedPrimary) {
                cachedMirrors = buildList {
                    add(primary)
                    MIRRORS.filter { it != primary }.forEach { add(it) }
                }
                cachedPrimary = primary
            }
            return cachedMirrors
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
    // 均带 LRU 容量上限：只有 TTL 时，用户长读一本书会让 chapterCache 把
    // 全部已读章节正文（每章数十 KB）长期留在内存里。
    // ------------------------------------------------------------------ //
    private val infoCache = TimedCache(2 * 60 * 60 * 1000L, maxEntries = 128)
    private val tocCache = TimedCache(2 * 60 * 60 * 1000L, maxEntries = 32)
    private val chapterCache = TimedCache(30 * 60 * 1000L, maxEntries = 64)

    /**
     * 释放网络资源（应用退出或数据源被替换时调用）。
     * 仅在 Cronet 引擎确实创建过时才 shutdown，避免"释放"反而触发初始化。
     */
    fun close() {
        if (cronetEngineLazy.isInitialized()) {
            runCatching { cronetEngineLazy.value?.shutdown() }
        }
    }

    /** App API 串行限流（官方 App 行为：同时间仅一个请求）。 */
    private val appApiSemaphore = Semaphore(1)

    // ------------------------------------------------------------------ //
    // retry（节流状态统一在 [pacer]）
    // ------------------------------------------------------------------ //
    private suspend fun execute(req: Request, retries: Int = 3): Response {
        var attempt = 0
        while (true) {
            pacer.pace()
            val resp = withContext(Dispatchers.IO) { okHttp.newCall(req).execute() }
            if (resp.code in RATE_CODES) {
                pacer.adjust(ok = false)
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
            pacer.adjust(ok = true)
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
     * [category] 用于按内容类型分组管理（home/book/chapter/tag）。
     */
    private suspend fun getHtmlCached(
        url: String,
        ttlMs: Long,
        category: String = "other",
        retries: Int = 3,
        ua: String = UA,
    ): String {
        htmlCache.get(url, ttlMs, category)?.let { return it }
        val html = getHtml(url, retries, ua)
        if (html.isNotBlank() && !isChallenge(html) && !html.contains(LOGIN_PATH)) {
            // 写入前同步用户配置的缓存上限（动态生效）
            htmlCache.setMaxBytes(cacheMaxMbProvider().toLong() * 1024 * 1024)
            htmlCache.put(url, html, category)
        }
        return html
    }

    // ---- 缓存管理（设置页入口）----

    /** 各类型磁盘缓存大小（字节）。 */
    fun cacheStats(): Map<String, Long> = htmlCache.sizeByCategory()

    /** 网页离线缓存（`filesDir/html_cache`）总大小（字节）。 */
    fun htmlCacheSize(): Long = htmlCache.totalSize()

    /**
     * 立即按当前设置应用缓存上限。
     *
     * 原先上限只在**写入时**读取（见 [getHtmlCached]），用户把上限从 500MB 调到 30MB 后，
     * 超出的部分要等到下一次写入才会被裁掉——设置页看起来"没生效"。调整上限时显式调用本方法。
     */
    fun applyCacheLimit() {
        htmlCache.setMaxBytes(cacheMaxMbProvider().toLong() * 1024 * 1024)
    }

    /** 清理磁盘缓存（[category] = null 清全部）；清全部时同时清空内存缓存。 */
    fun clearCache(category: String? = null) {
        htmlCache.clear(category)
        if (category == null) {
            infoCache.clear()
            tocCache.clear()
            chapterCache.clear()
        }
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

    /**
     * 确保已登录（供 tags/bookcase 等需登录接口调用）。
     *
     * 加锁的原因：启动时静默登录、探索页 `tags()`、书架页 `bookcase()` 可能**同时**
     * 发现"未登录"并各自发起一次登录，产生重复请求、重复写 Cookie，还会让站点多算几次
     * 风控样本。锁内必须**再次检查**登录态——先到的协程可能已经把登录做完了，
     * 排队者直接复用结果即可。
     */
    suspend fun ensureLoggedIn(): Boolean {
        if (isLoggedIn()) return true
        return loginMutex.withLock {
            if (isLoggedIn()) return@withLock true
            val creds = defaultCredentials() ?: return@withLock false
            loginInternal(creds.first, creds.second)
        }
    }

    /** 显式登录（切换镜像后重登等）；与 [ensureLoggedIn] 共用同一把锁，避免并发重复登录。 */
    suspend fun login(user: String, pass: String): Boolean =
        loginMutex.withLock { loginInternal(user, pass) }

    private suspend fun loginInternal(user: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
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

    // 说明：这里**没有** logout()。本应用全程使用内置共享账号、不提供退出入口，
    // 原先的 logout() 在全仓已无任何调用点（清除会话统一走 clearCookies()），
    // 故一并移除，避免留下"可以退出登录"的误导性 API。

    // ------------------------------------------------------------------ //
    // read operations
    // ------------------------------------------------------------------ //
    suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val type = if (byAuthor) "author" else "articlename"
            // 有界重试：站点在两次搜索间隔 < 5s 时返回错误页。
            // 原实现递归调用自身且无次数上限——站点持续返回错误页时会无界深递归，
            // 长时间挂起且协程取消无法从递归中恢复。这里改为定长循环。
            repeat(SEARCH_MAX_ATTEMPTS) { attempt ->
                pacer.paceSearch()
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
                val throttled = html.contains(THROTTLE_MARK) || html.contains("出现错误")
                if (!throttled) {
                    return@withContext parseSearchResult(finalUrl, html)
                }
                if (attempt < SEARCH_MAX_ATTEMPTS - 1) delay(SEARCH_RETRY_DELAY_MS)
            }
            throw IOException("搜索被站点限流，请稍后重试")
        }

    /** 搜索响应解析：既可能是精确命中（302 直跳详情页），也可能是结果列表页。 */
    private fun parseSearchResult(finalUrl: String, html: String): List<SearchResult> {
        val m = BOOK_URL.find(finalUrl)
        if (m != null) {
            val id = m.groupValues[1].toIntOrNull() ?: return emptyList()
            val info = Parsers.parseBookInfo(html, id)
            // 单结果重定向：从详情页带上封面 URL（与列表页一致展示封面）
            return listOf(SearchResult(id, info.title, info.coverUrl))
        }
        return Parsers.parseSearchResults(html)
    }

    suspend fun bookInfo(id: Int): BookInfo = withContext(Dispatchers.IO) {
        infoCache.get("info_$id") ?: run {
            // 网页优先（含磁盘缓存 + cf_clearance 快路径）；失败/空则走官方 App API（免 CF）
            val web = runCatchingNotCancelling {
                Parsers.parseBookInfo(getHtmlCached("$base/book/$id.htm", TTL_BOOK, "book"), id)
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
            val web = runCatchingNotCancelling {
                Parsers.parseChapterIndex(
                    getHtmlCached("$base/novel/$groupId/$bookId/index.htm", TTL_BOOK, "book")
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
                val web = runCatchingNotCancelling {
                    Parsers.parseChapter(
                        getHtmlCached("$base/novel/$gid/$bookId/$cid.htm", TTL_CHAPTER, "chapter")
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
            category = "home",
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

    /** Books under a tag. 需登录；快路径 → WebView → Cronet → OkHttp mirrors。分页：page>=2 追加 &page=N。 */
    suspend fun tagBooks(tag: String, page: Int = 1): List<HomeBook> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val query = URLEncoder.encode(tag, "GBK")
        val pageParam = if (page > 1) "&page=$page" else ""
        tryDirect(
            urlFor = { h -> "$h/modules/article/tags.php?t=$query&v=1$pageParam" },
            parse = { html -> Parsers.parseBookList(html).takeIf { it.isNotEmpty() } },
            ttlMs = TTL_TAG_BOOKS,
            category = "tag",
        ) ?: fetchWithBypass(
            urlFor = { h -> "$h/modules/article/tags.php?t=$query&v=1$pageParam" },
            parse = { html -> Parsers.parseBookList(html).takeIf { it.isNotEmpty() } },
        ) ?: emptyList()
    }

    private fun isChallenge(html: String): Boolean =
        html.contains("challenge-platform") || html.contains("cf-challenge") ||
            html.contains("cf_chl") || html.contains("cf-chl")

    /**
     * `runCatching` 的同义封装：**不吞协程取消**。
     *
     * 标准 `runCatching` 捕获 `Throwable`，会把 `CancellationException` 一并吃掉：
     * 页面被取消时会被误判成"直连失败"，随后继续走 WebView/Cronet 绕过栈，
     * 协程既无法及时取消、还会多打一串无谓请求。
     */
    private inline fun <T> runCatchingNotCancelling(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

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
        category: String = "other",
    ): T? {
        for (h in mirrors) {
            val ua = uaFor(urlFor(h))
            val parsed = runCatchingNotCancelling {
                parse(getHtmlCached(urlFor(h), ttlMs, category, retries = 1, ua = ua))
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    /**
     * Cloudflare 三级绕过抓取（与 tags/tagBooks 同栈）：
     * WebView（真浏览器跑 CF JS 挑战，解出后持久化 cf_clearance）→ Cronet（TLS 指纹）
     * → OkHttp 随机 Android UA，逐镜像尝试。[parse] 返回 null 表示该响应无有效内容。
     * 返回 null 表示全部失败。
     *
     * 注：原先还有一个 `steps: MutableList<String>` 诊断参数，用于收集各层失败原因，
     * 但从未有任何调用方传入（整条诊断链路是半成品），已移除以免误导。
     */
    private suspend fun <T> fetchWithBypass(
        urlFor: (String) -> String,
        parse: (String) -> T?,
    ): T? {
        for (h in mirrors) {
            val html = webViewGet(urlFor(h)) ?: continue
            parse(html)?.let { return it }
        }
        val engine = cronetEngine
        if (engine != null) {
            for (h in mirrors) {
                val html = cronetGet(engine, urlFor(h)) ?: continue
                parse(html)?.let { return it }
            }
        }
        for (h in mirrors) {
            // 若该主机已有 cf_clearance（WebView 解出后持久化），复用其绑定 UA 直接通过
            val ua = uaFor(urlFor(h))
            val parsed = runCatchingNotCancelling {
                parse(getHtml(urlFor(h), retries = 1, ua = ua))
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    /**
     * GET via Cronet, carrying the app's session cookies; returns null on failure/timeout.
     *
     * 用挂起等待替代 `CountDownLatch.await(8s)`：原先每个 Cronet 请求都要独占一个
     * IO 线程空等最多 8 秒（并发几个请求就白白占住几个线程），而且协程被取消时
     * 线程仍会等到超时才释放。改成 `suspendCancellableCoroutine` 后：
     * 线程在等待期间被释放、取消能立刻传播到 `UrlRequest.cancel()`。
     */
    private suspend fun cronetGet(engine: CronetEngine, url: String): String? =
        withTimeoutOrNull(CRONET_TIMEOUT_SECONDS * 1000) {
            val readBuffer = ByteBuffer.allocateDirect(64 * 1024)
            val body = ByteArrayOutputStream()
            val httpUrl = url.toHttpUrl()
            val cookieHeader = cookieStore.loadForRequest(httpUrl)
                .joinToString("; ") { "${it.name}=${it.value}" }

            suspendCancellableCoroutine { cont ->
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
                    if (cont.isActive) cont.resume(String(body.toByteArray(), GB18030))
                }

                override fun onFailed(
                    request: UrlRequest,
                    info: UrlResponseInfo?,
                    error: CronetException,
                ) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                    if (cont.isActive) cont.resume(null)
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
            // 取消（页面离开 / 超时）时立刻取消底层请求，回调再触发 onCanceled 也不影响已取消的协程
            cont.invokeOnCancellation { runCatching { request.cancel() } }
            request.start()
            }
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
    private suspend fun webViewGet(url: String): String? =
        // 串行化：同一时刻只有一个隐藏 WebView 在解挑战（见 webViewMutex 说明）。
        // 外层超时覆盖"排队 + 执行"，避免排在后面的请求无限期等待。
        withTimeoutOrNull(WEBVIEW_LOCK_TIMEOUT_MS) {
            webViewMutex.withLock {
                withTimeoutOrNull(WEBVIEW_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val webView = runCatching { WebView(appContext) }.getOrNull()
                            if (webView == null) {
                                if (cont.isActive) cont.resume(null)
                                return@suspendCancellableCoroutine
                            }
                            // 协程被取消（离开页面 / 超时 / ViewModel 销毁）时销毁 WebView：
                            // 原先只有"成功/失败"两条路径会 destroy，取消路径会留下一个
                            // 仍在跑 JS 的隐藏 WebView（内存 + 回调 + 风控样本全都留着）。
                            cont.invokeOnCancellation {
                                webView.post { destroyWebView(webView) }
                            }
                            var usedUa = randomAndroidUa()
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
                                                        destroyWebView(webView)
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
                                        destroyWebView(webView)
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }
                                webView.loadUrl(url)
                            }.onFailure {
                                destroyWebView(webView)
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    }
                }
            }
        }

    /**
     * 隐藏 WebView 的统一销毁路径。
     *
     * 直接 `destroy()` 会留下未停止的加载与回调引用；按官方建议先停加载、清历史，
     * 再摘掉 WebViewClient，最后销毁。每一步都 runCatching——任一步失败都不应
     * 阻止后续清理（否则就成了"清理过程中崩溃"）。
     */
    private fun destroyWebView(webView: WebView) {
        runCatching { webView.stopLoading() }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.clearHistory() }
        runCatching { webView.removeAllViews() }
        // 换成空实现而不是 null（SDK 里该属性是非空类型），目的是断开原匿名回调
        // 对协程 continuation 的引用，避免销毁后仍被回调持有。
        runCatching { webView.webViewClient = WebViewClient() }
        runCatching { webView.destroy() }
    }

    /** type: "txt"(GBK) | "utf8" | "big5" */
    suspend fun downloadFullTxt(id: Int, type: String): ByteArray =
        getBytes("$DL/down.php?type=$type&node=1&id=$id")

    /**
     * 统一限流组件：本项目**全部**请求节流状态的唯一所有者。
     *
     * 三层语义各自独立、不可合并成一个延时：
     * 1. [pace] 全局请求间隔（间隔 = 基数 × 自适应速率），保护站点也保护自己；
     * 2. [adjust] 自适应速率（成功回落 / 失败放大，1.0~8.0），遇 429 自动降速；
     * 3. [paceSearch] 搜索硬间隔（站点硬性要求两次搜索 ≥5s，短于该值直接返回错误页）。
     * 此外 App API 另有 `Semaphore(1)` 串行约束（官方 App 行为），留在调用侧。
     */
    private class RatePacer(
        private val baseIntervalMs: Long,
        private val searchIntervalMs: Long,
    ) {
        private val lock = Any()
        private var lastRequest = 0L
        private var lastSearch = 0L
        private var rate = 1.0

        /** 请求结果反馈：成功逐步回落（×0.85），失败立即放大（×2），钳制在 1.0~8.0。 */
        fun adjust(ok: Boolean) {
            synchronized(lock) {
                rate = if (ok) max(1.0, rate * 0.85) else min(8.0, rate * 2)
            }
        }

        /** 全局请求间隔：必要时挂起补足等待。 */
        suspend fun pace() {
            val sleep = synchronized(lock) {
                val now = System.currentTimeMillis()
                val wait = lastRequest + (baseIntervalMs * rate).toLong() - now
                lastRequest = now
                wait
            }
            if (sleep > 0) delay(sleep)
        }

        /** 搜索硬间隔：站点要求两次搜索间隔 ≥ [searchIntervalMs]。 */
        suspend fun paceSearch() {
            val sleep = synchronized(lock) {
                val now = System.currentTimeMillis()
                val wait = lastSearch + searchIntervalMs - now
                lastSearch = now
                wait
            }
            if (sleep > 0) delay(sleep)
        }
    }
}

/**
 * 简单 TTL + LRU 内存缓存（参考 LightNovelReader 的 Cache）。
 * 只缓存成功结果；超时后下次访问自动重取。
 *
 * 容量上限说明：原实现只有 TTL、没有容量上限——用户长读一本书时
 * `chapterCache`（30 分钟 TTL）会把全部已读章节正文（每章数十 KB）
 * 一直累积在内存里。这里补上 LRU 上限（按访问顺序淘汰最久未用），
 * 把内存占用钳制在可预期范围内。
 */
private class TimedCache(
    private val ttlMs: Long,
    private val maxEntries: Int,
) {
    /** accessOrder = true：读取即刷新 LRU 顺序，供 [removeEldestEntry] 淘汰最久未用项。 */
    private val map = object : LinkedHashMap<String, Pair<Long, Any>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Long, Any>>,
        ): Boolean = size > maxEntries
    }

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
