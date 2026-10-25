package com.hoshino.wenku8reader.data

import android.content.Context
import android.os.Build
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.hoshino.wenku8reader.data.log.AppLogger
import com.hoshino.wenku8reader.data.log.LogSanitizer
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
    /** 共享 Cookie 存储（与统一 Cronet 网络栈共用）。 */
    private val cookieStore: CookieStore,
    /** 应用唯一的 HTTP 网络栈；所有请求均经 CronetHttpClient。 */
    private val cronetHttp: CronetHttpClient,
    /** 用户选定的主站镜像（设置页可切换）；缺省用 wenku8.net */
    private val primaryMirrorProvider: () -> String = { DEFAULT_BASE },
    /** 内置账号凭据（供需登录接口 ensureLoggedIn 静默登录用） */
    private val defaultCredentials: () -> Pair<String, String>? = { null },
    /** 磁盘缓存上限（MB，设置页可调）；每次写入前同步，动态生效 */
    private val cacheMaxMbProvider: () -> Int = { 30 },
    /**
     * 回落到内置共享账号时的回调（用于清掉"当前激活账户"标记）。
     *
     * 内置账号登录后同样持有会话，所以"有没有会话"区分不出内置与用户；这个回调保证
     * **只要发生回落，激活标记就一定被清掉**，界面不会出现"其实已经掉回内置、却还显示
     * 某个用户已登录"。用回调而不是直接依赖 `AccountStore`，是为了让客户端不反向依赖
     * 具体存储（与镜像、缓存上限的注入方式一致）。
     */
    private val onUserSessionLost: suspend () -> Unit = {},
    /**
     * 应用日志（重点记录网络访问）。
     *
     * 允许为 null：单测 / 工具脚本构造客户端时不需要日志；生产装配见 `AppContainer`。
     * 打点只写"方法 + 脱敏地址 + 状态码 + 耗时 + 错误类型"，**不写**请求体、Cookie 与正文。
     */
    private val logger: AppLogger? = null,
) {

    private val appContext = context.applicationContext
    private val htmlCache = HtmlDiskCache(appContext, cacheMaxMbProvider().toLong() * 1024 * 1024)

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

    companion object {
        /** 日志标签：网络请求 / Cloudflare 绕过 / 登录态 / 站方书架。 */
        private const val TAG_NET = "NET"
        private const val TAG_BYPASS = "BYPASS"
        private const val TAG_AUTH = "AUTH"
        private const val TAG_SHELF = "SHELF"
        private const val TAG_SEARCH = "SEARCH"

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
        /**
         * 稳定的设备 UA：**整个进程只算一次**。
         *
         * 不要改成"每次请求随机"：真实设备不会换 UA，而 `cf_clearance` 与 UA 绑定，
         * 随机值既像自动化脚本、又会让挑战令牌复用失效（见 [UserAgent] 的说明）。
         */
        private val DEVICE_UA: String by lazy {
            UserAgent.build(Build.VERSION.RELEASE.orEmpty(), Build.MODEL.orEmpty())
        }
        private val GB18030: Charset = Charset.forName("GB18030")

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
            // 兜底校验：设置里可能残留已下线的镜像（例如 2026-10 下线的 www.wenku8.com）。
            // 不校验的话，老用户的"主站"会一直指向一个证书不匹配的域名。
            val primary = primaryMirrorProvider()
                .ifBlank { DEFAULT_BASE }
                .takeIf { Wenku8Hosts.isValidMirror(it) }
                ?: DEFAULT_BASE
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
     * cf_clearance 令牌与该 UA 绑定，后续 Cronet 复用令牌时必须使用同一 UA。
     */
    private val challengeUa = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun uaFor(url: String): String =
        runCatching { url.toHttpUrl().host }.getOrNull()?.let { challengeUa[it] }
            ?: DEVICE_UA

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
     * 同键并发去重：缓存只挡"已完成"的重复访问，挡不住"同时发起"的重复请求
     * （详情页 + 目录页 + 阅读器 + 下载器可能同时要同一本书的数据）。
     * 见 [SingleFlight] 的说明；章节正文按 `bookId+cid` 分键，不同章节仍可并行。
     */
    private val singleFlight = SingleFlight()

    fun close() {
        cronetHttp.close()
    }

    /** App API 串行限流（官方 App 行为：同时间仅一个请求）。 */
    private val appApiSemaphore = Semaphore(1)

    // ------------------------------------------------------------------ //
    // retry（节流状态统一在 [pacer]）
    // ------------------------------------------------------------------ //
    private suspend fun executeCronet(
        url: String,
        method: String = "GET",
        headers: Map<String, String>,
        body: ByteArray? = null,
        retries: Int = 3,
        timeoutMs: Long = 20_000L,
    ): CronetHttpClient.Response {
        var attempt = 0
        val startedAt = System.currentTimeMillis()
        val target = "$method ${LogSanitizer.sanitizeUrl(url)}"
        while (true) {
            pacer.pace()
            val resp = cronetHttp.execute(
                CronetHttpClient.Request(
                    url = url,
                    method = method,
                    headers = headers,
                    body = body,
                    useCookies = true,
                    timeoutMs = timeoutMs,
                    redirectTransform = Wenku8Hosts::secureMainSiteUrl,
                )
            )
            if (resp == null) {
                logger?.warn(
                    TAG_NET,
                    "$target -> 网络失败/超时（${elapsedMs(startedAt)} ms, " +
                        "attempt ${attempt + 1}）",
                )
                throw IOException("网络请求失败，请检查网络连接")
            }
            // 403 = Cloudflare 硬拦截：重试没有意义，直接失败交给三级绕过栈
            //（2026-10-18 设备日志：一天 433 次 403，每次白跑 3 次尝试；随后 Cronet 一次就 200）
            if (resp.statusCode == HttpRetryPolicy.BLOCKED) {
                pacer.adjust(ok = false)
                logger?.error(
                    TAG_NET,
                    "$target -> HTTP 403（Cloudflare 拦截，不重试，${elapsedMs(startedAt)} ms）",
                )
                throw IOException("请求被站点拦截(HTTP 403)")
            }
            if (HttpRetryPolicy.shouldRetry(resp.statusCode)) {
                pacer.adjust(ok = false)
                if (attempt < retries) {
                    val backoff = min(1500L * (1L shl attempt), 30000L)
                    logger?.warn(
                        TAG_NET,
                        "$target -> HTTP ${resp.statusCode}，${backoff} ms 后重试" +
                            "（第 ${attempt + 1} 次）",
                    )
                    delay(backoff)
                    attempt++
                    continue
                }
                logger?.error(
                    TAG_NET,
                    "$target -> HTTP ${resp.statusCode}，重试 ${attempt + 1} 次后放弃" +
                        "（${elapsedMs(startedAt)} ms）",
                )
                val msg = if (resp.statusCode == 429) {
                    "站点限流(HTTP 429)，请稍后重试"
                } else {
                    "请求失败(HTTP ${resp.statusCode})，请稍后重试"
                }
                throw IOException(msg)
            }
            pacer.adjust(ok = true)
            logger?.info(
                TAG_NET,
                "$target -> ${resp.statusCode}（${elapsedMs(startedAt)} ms, " +
                    "attempt ${attempt + 1}）",
            )
            return resp
        }
    }

    private fun elapsedMs(startedAt: Long): Long = System.currentTimeMillis() - startedAt

    // ------------------------------------------------------------------ //
    // low-level requests
    // ------------------------------------------------------------------ //
    private fun browserHeaders(
        referer: String?,
        ua: String = DEVICE_UA,
        sameOriginForm: Boolean = false,
    ): Map<String, String> = buildMap {
        put("User-Agent", ua)
        put(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        )
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        put("Cache-Control", "max-age=0")
        put("Upgrade-Insecure-Requests", "1")
        put("Sec-Fetch-Dest", "document")
        put("Sec-Fetch-Mode", "navigate")
        put("Sec-Fetch-Site", if (sameOriginForm) "same-origin" else "none")
        put("Sec-Fetch-User", "?1")
        referer?.let { put("Referer", it) }
    }

    private suspend fun getBytes(
        url: String,
        retries: Int = 3,
        ua: String = DEVICE_UA,
        referer: String? = refererFor(url),
    ): ByteArray = executeCronet(
        url = url,
        headers = browserHeaders(referer, ua),
        retries = retries,
    ).body

    /** Referer matching the request's own host (mirror-safe). */
    private fun refererFor(url: String): String? = runCatching {
        val h = url.toHttpUrl()
        "${h.scheme}://${h.host}/"
    }.getOrNull()

    /** Fetches a page and decodes it as GB18030 on the IO dispatcher. */
    private suspend fun getHtml(url: String, retries: Int = 3, ua: String = DEVICE_UA): String =
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
        ua: String = DEVICE_UA,
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

    /** GBK 编码的表单体（Cronet POST 使用原始字节）。 */
    private fun gbkFormBytes(pairs: List<Pair<String, String>>): ByteArray =
        pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "GBK")}=${URLEncoder.encode(v, "GBK")}"
        }.toByteArray(GB18030)

    private suspend fun postForm(
        url: String,
        pairs: List<Pair<String, String>>,
    ): CronetHttpClient.Response = executeCronet(
        url = url,
        method = "POST",
        headers = browserHeaders("$base/login.php", sameOriginForm = true) + mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "Origin" to base,
        ),
        body = gbkFormBytes(pairs),
        retries = 2,
    )

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
            // 走到这里说明没有可用会话：若之前是用户账户登录，此刻它已经失效 →
            // 先清掉激活标记，再用内置账号登录（顺序不能反）
            onUserSessionLost()
            val creds = defaultCredentials() ?: return@withLock false
            logger?.warn(TAG_AUTH, "会话缺失，回落到内置账号登录")
            loginInternal(creds.first, creds.second)
        }
    }

    /** 显式登录（切换镜像后重登等）；与 [ensureLoggedIn] 共用同一把锁，避免并发重复登录。 */
    suspend fun login(user: String, pass: String): Boolean =
        loginMutex.withLock { loginInternal(user, pass) }

    private suspend fun loginInternal(user: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
            val fields = listOf(
                "username" to user,
                "password" to pass,
                "usecookie" to "315360000",
                "action" to "login",
                "submit" to "\u00A0\u00A0\u767B\u00A0\u00A0\u5F55\u00A0",
            )
            val loginUrl = "$base/login.php?do=submit" +
                "&jumpurl=${URLEncoder.encode("$base/index.php", "UTF-8")}"

            // 普通 HTTP 栈的 TLS 指纹很容易被 Cloudflare 403；Cronet 是已经验证过的
            // 通道。登录响应通常是 302，会话 Cookie 可能在重定向响应而非最终页上，
            // cronetRequestRaw() 会逐跳保存 Set-Cookie，再按 HTTP 语义继续 GET。
            val viaCronet = runCatchingNotCancelling {
                cronetPost(loginUrl, gbkFormBytes(fields))
            }.getOrNull()
            if (viaCronet != null && hasSession()) {
                cookieStore.persist()
                logger?.info(TAG_AUTH, "登录成功（Cronet）")
                return@withContext true
            }

            // 只记成败，不记用户名（账户名属于用户隐私）
            logger?.info(TAG_AUTH, "登录失败")
            false
        }

    // 关于「退出登录」：客户端这里只提供 [clearCookies]（清会话），**不提供 logout()**。
    // 因为退出登录还牵扯"当前激活账户"这一层状态（见 AccountStore），而客户端并不持有它；
    // 由 ui/account 的 ViewModel 编排「clearCookies() + AccountStore.setActive(null)」两步，
    // 之后 ensureLoggedIn() 会自动用内置共享账号静默登录，阅读与探索无感续用。
    // 另：内置共享账号仍是**默认路径**，用户账户（由实验性开关控制）只是可选的覆盖。

    // ------------------------------------------------------------------ //
    // read operations
    // ------------------------------------------------------------------ //
    suspend fun search(keyword: String, byAuthor: Boolean): List<SearchResult> =
        withContext(Dispatchers.IO) {
            // 站点要求登录后才能搜索（见 wenku8-api.md §3.2）；已有会话时这一步是零请求
            ensureLoggedIn()
            val type = if (byAuthor) "author" else "articlename"

            // 1) Cronet POST /so.php：站点只接受 POST 搜索，而直连 POST 会被 Cloudflare 403，
            //    Cronet 的 TLS 指纹则能过。精确命中会 302 到 /book/{id}.htm，按最终 URL 解析。
            val fields = listOf(
                "searchtype" to type,
                "searchkey" to keyword,
                "charset" to "gbk",
                "Submit" to "\u8F7B\u5C0F\u8BF4\u641C\u7D22",
            )
            pacer.paceSearch()
            val viaCronet = runCatchingNotCancelling {
                cronetPost("$base/so.php", gbkFormBytes(fields))
            }.getOrNull()
            if (viaCronet != null) {
                val (finalUrl, html) = viaCronet
                val loginPage = html.contains("frmlogin") || html.contains(LOGIN_PATH)
                if (!loginPage && !html.contains(THROTTLE_MARK) && !html.contains("出现错误")) {
                    val results = parseSearchResult(finalUrl, html)
                    logger?.info(TAG_SEARCH, "Cronet POST 命中：${results.size} 条（${html.length} chars）")
                    return@withContext results
                }
                logger?.warn(TAG_SEARCH, "Cronet POST 返回登录页/限流页（${html.length} chars）")
            }

            // 2) GET 结果页（POST 的 302 目标）走三级绕过栈
            // 直连被 Cloudflare 拦截时 POST /so.php 根本发不出去；但它的 302 目标
            // /modules/article/search.php 是 **GET**，可以走三级绕过栈（Cronet 优先）。
            // 这也是 2026-10 设备日志里"搜索用不了"（so.php 403）的修复点。
            val encoded = runCatching { URLEncoder.encode(keyword, "GBK") }
                .getOrDefault(keyword)
            pacer.paceSearch()
            val viaBypass = runCatchingNotCancelling {
                fetchWithBypass(
                    urlFor = { h ->
                        "$h/modules/article/search.php?searchtype=$type&searchkey=$encoded"
                    },
                    parse = { html ->
                        when {
                            // 限流页 → 返回 null，交给下面的 POST 路径按原逻辑重试
                            html.contains(THROTTLE_MARK) || html.contains("出现错误") -> null
                            // 结果页（含 0 条结果）→ 正常返回；登录页 / CF 挑战页 → null
                            html.contains("id=\"centerm\"") || html.contains("<caption") ->
                                Parsers.parseSearchResults(html)
                            else -> null
                        }
                    },
                )
            }.getOrNull()
            if (viaBypass != null) {
                logger?.info(TAG_SEARCH, "GET search.php 命中：${viaBypass.size} 条")
                return@withContext viaBypass
            }

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
                val finalUrl = resp.url
                val html = String(resp.body, GB18030)
                val throttled = html.contains(THROTTLE_MARK) || html.contains("出现错误")
                if (!throttled) {
                    val results = parseSearchResult(finalUrl, html)
                    logger?.info(TAG_SEARCH, "直连 POST 命中：${results.size} 条（${html.length} chars）")
                    return@withContext results
                }
                if (attempt < SEARCH_MAX_ATTEMPTS - 1) delay(SEARCH_RETRY_DELAY_MS)
            }
            logger?.error(TAG_SEARCH, "搜索失败：Cronet POST / GET / 直连 POST 均未命中")
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
        // 同键并发只放行一个：排队者拿到锁时缓存已写好，不会再发一次请求
        singleFlight.run("info_$id") {
            infoCache.get("info_$id") ?: run {
                // 网页优先（含磁盘缓存 + cf_clearance 快路径）；失败/空则走官方 App API（免 CF）
                val web = runCatchingNotCancelling {
                    Parsers.parseBookInfo(
                        getHtmlCached("$base/book/$id.htm", TTL_BOOK, "book"),
                        id,
                    )
                }.getOrNull()
                val info = web?.takeIf { it.title.isNotBlank() }
                    ?: fetchBookInfoWithBypass(id)
                    ?: appApiBookInfo(id)
                    ?: throw IOException("书籍信息获取失败")
                infoCache.put("info_$id", info)
                info
            }
        }
    }

    suspend fun chapters(bookId: Int, groupId: Int): List<Volume> = withContext(Dispatchers.IO) {
        singleFlight.run("toc_$bookId") {
            tocCache.get("toc_$bookId") ?: run {
                val web = runCatchingNotCancelling {
                    Parsers.parseChapterIndex(
                        getHtmlCached("$base/novel/$groupId/$bookId/index.htm", TTL_BOOK, "book")
                    )
                }.getOrNull()
                val volumes = web?.takeIf { it.isNotEmpty() }
                    ?: fetchVolumesWithBypass(bookId, groupId)
                    ?: appApiVolumes(bookId)
                    ?: throw IOException("章节目录加载失败")
                tocCache.put("toc_$bookId", volumes)
                volumes
            }
        }
    }

    suspend fun chapterContent(gid: Int, bookId: Int, cid: String): ChapterContent =
        withContext(Dispatchers.IO) {
            // 按 bookId+cid 分键：同一章的并发请求合并，不同章节照旧并行
            singleFlight.run("chap_${bookId}_$cid") {
                chapterCache.get("chap_${bookId}_$cid") ?: run {
                    val web = runCatchingNotCancelling {
                        Parsers.parseChapter(
                            getHtmlCached("$base/novel/$gid/$bookId/$cid.htm", TTL_CHAPTER, "chapter")
                        )
                    }.getOrNull()
                    val chapter = web?.takeIf { it.text.isNotBlank() || it.images.isNotEmpty() }
                        ?: fetchChapterWithBypass(gid, bookId, cid)
                        ?: appApiChapter(bookId, cid)
                        ?: throw IOException("章节加载失败")
                    chapterCache.put("chap_${bookId}_$cid", chapter)
                    chapter
                }
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
                        val body = gbkFormBytes(
                            listOf(
                                "request" to Base64.getEncoder()
                                    .encodeToString(request.toByteArray()),
                                "timetoken" to System.currentTimeMillis().toString(),
                                "appver" to APP_VER,
                            )
                        )
                        val resp = executeCronet(
                            url = host,
                            method = "POST",
                            headers = mapOf(
                                "User-Agent" to APP_UA,
                                "Accept" to "*/*",
                                "Content-Type" to "application/x-www-form-urlencoded",
                            ),
                            body = body,
                            retries = 1,
                        )
                        val text = resp.body.toString(GB18030)
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
        val info = AppParsers.parseAppBookInfo(meta, id) ?: return null
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
        return AppParsers.parseAppVolumes(list)
    }

    private suspend fun appApiChapter(bookId: Int, cid: String): ChapterContent? {
        val text = appApiGet("action=book&do=text&aid=$bookId&cid=$cid&t=0") ?: return null
        return AppParsers.parseAppChapter(text)
    }

    /**
     * 站方书架（账号的默认分组）。
     *
     * 与 `tags()` / `tagBooks()` 一样**必须先确保登录**：本方法以前漏了这一步，未登录时
     * 站点会 302 到登录页，解析出来是个空书架——一个静默的错误结果。
     */
    suspend fun bookcase(): List<BookcaseItem> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val html = fetchSiteBookcasePage(
            urlFor = { h -> "$h/modules/article/bookcase.php" },
        ) ?: throw IOException("Wenku8 书架加载失败，请稍后重试")

        when (Parsers.classifyBookcasePage(html)) {
            Parsers.BookcasePageKind.LOGIN_REQUIRED -> markBookcaseSessionExpired()
            Parsers.BookcasePageKind.OTHER -> {
                logger?.warn(
                    TAG_SHELF,
                    "书架页面未命中旧模板，尝试新版解析（len=${html.length}, " +
                        "checkform=${html.contains("checkform")}, " +
                        "readbookcase=${html.contains("readbookcase.php")}, " +
                        "login=${html.contains(LOGIN_PATH)}, " +
                        "title=${bookcasePageTitle(html)}）",
                )
            }
            Parsers.BookcasePageKind.BOOKSHELF -> Unit
        }
        Parsers.parseBookcase(html).also {
            logger?.info(TAG_SHELF, "站方书架 -> ${it.size} 本")
        }
    }

    /** 用户会话在服务端已失效（请求被重定向到登录页）。 */
    class SessionExpiredException : IllegalStateException("登录状态已失效，请重新登录")

    /**
     * 把一本书加入站方书架（`addbookcase.php?bid=<书 id>`）。
     *
     * 站点无论成功与否都返回 200（成功后会跳回书架页），所以这里**不解析返回值**——
     * 调用方应在之后刷新一次站方书架来确认真的是否加上了（`Wenku8Shelf.add` 就是这么做的）。
     */
    suspend fun addToSiteBookcase(bookId: Int) {
        withContext(Dispatchers.IO) {
            ensureLoggedIn()
            val html = fetchSiteBookcasePage(
                urlFor = { h -> "$h/modules/article/addbookcase.php?bid=$bookId" },
            ) ?: throw IOException("加入 Wenku8 书架失败，请稍后重试")
            if (Parsers.classifyBookcasePage(html) == Parsers.BookcasePageKind.LOGIN_REQUIRED) {
                markBookcaseSessionExpired()
            }
            logger?.info(TAG_SHELF, "加入站方书架 book=$bookId")
        }
    }

    /**
     * 把一本书从站方书架移出（`bookcase.php?delid=<书架记录 id>`）。
     *
     * 注意参数是 [bookcaseId]（站点行里的 `bid`），**不是书 id**——两者不相等，
     * 传错会移不掉或移错书。同样不解析返回值，由调用方刷新确认。
     */
    suspend fun removeFromSiteBookcase(bookcaseId: Int) {
        withContext(Dispatchers.IO) {
            ensureLoggedIn()
            val html = fetchSiteBookcasePage(
                urlFor = { h -> "$h/modules/article/bookcase.php?delid=$bookcaseId" },
            ) ?: throw IOException("移出 Wenku8 书架失败，请稍后重试")
            if (Parsers.classifyBookcasePage(html) == Parsers.BookcasePageKind.LOGIN_REQUIRED) {
                markBookcaseSessionExpired()
            }
            logger?.info(TAG_SHELF, "移出站方书架 delid=$bookcaseId")
        }
    }

    /**
     * 站方账号数据只属于登录时的那个镜像（Cookie 不跨主机共享），因此不能用
     * [fetchWithBypass] 默认的全镜像轮换：切到另一个镜像只会得到登录页。
     * Cronet 的 TLS 指纹仍能通过当前镜像的 Cloudflare。
     */
    private suspend fun fetchSiteBookcasePage(
        urlFor: (String) -> String,
    ): String? = fetchWithBypass(
        urlFor = urlFor,
        candidateHosts = listOf(base),
        parse = { html ->
            if (isBlockedPage(html)) {
                null
            } else {
                when (Parsers.classifyBookcasePage(html)) {
                    Parsers.BookcasePageKind.BOOKSHELF,
                    Parsers.BookcasePageKind.LOGIN_REQUIRED,
                    Parsers.BookcasePageKind.OTHER -> html
                }
            }
        },
    )

    /** Cronet 即使拿到 HTTP 403 也会返回正文；必须按错误页特征过滤，不能把错误页当成功。 */
    private fun isBlockedPage(html: String): Boolean =
        isChallenge(html) ||
            html.contains("403 Forbidden", ignoreCase = true) ||
            html.contains("Access denied", ignoreCase = true)

    private fun bookcasePageTitle(html: String): String =
        Regex(
            "<title[^>]*>(.*?)</title>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(80)
            .orEmpty()

    private suspend fun markBookcaseSessionExpired(): Nothing {
        logger?.warn(TAG_SHELF, "站方书架会话失效（被重定向到登录页）")
        onUserSessionLost()
        throw SessionExpiredException()
    }

    /**
     * 首页栏目。快路径：默认 UA 直连（常规情况毫秒级返回）；
     * 若直连失败或返回疑似 CF 挑战页（解析为空），自动升级到
     * Cronet → WebView 的两级 Cloudflare 绕过栈（统一使用稳定的设备 UA）。
     */
    suspend fun homepage(): List<HomeSection> = withContext(Dispatchers.IO) {
        // 未登录时站点会把首页 302 到 login.php，并给出明文 HTTP Location。
        // 先等待静默登录（已有会话时零网络请求），首页才能拿到真正的栏目内容。
        ensureLoggedIn()
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

    /** Books under a tag. 需登录；快路径 → Cronet → WebView。分页：page>=2 追加 &page=N。 */
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
            html.contains("cf_chl") || html.contains("cf-chl") ||
            html.contains("__cf_chl") ||
            html.contains("Just a moment", ignoreCase = true) ||
            html.contains("Enable JavaScript and cookies", ignoreCase = true) ||
            html.contains("We're verifying your browser", ignoreCase = true) ||
            html.contains("Security Checkpoint", ignoreCase = true)

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
     * 快路径（参考 LightNovelReader 的 cookie-first 思路）：直接用 Cronet 携带
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
     * Cloudflare 两级绕过抓取（与 tags/tagBooks 同栈）：
     * **Cronet（TLS 指纹）→ WebView（真浏览器跑 CF JS 挑战，解出后持久化 cf_clearance）**
     * 逐镜像尝试。[parse] 返回 null 表示该响应无有效内容。
     * 返回 null 表示全部失败。
     *
     * ⚠️ **Cronet 放最前是有意的**（2026-10 调整）：WebView 请求是串行的
     * （`webViewMutex`），探索页一次要加载几十个标签预览；实测 50 个标签并发时，
     * 绝大多数请求都卡在 WebView 锁上超时（15s+），而 Cronet 每次约 0.5s 就拿到 200。
     * WebView 退居第二层，只在 Cronet 失败（例如确实需要跑 JS 挑战）时使用。
     */
    private suspend fun <T> fetchWithBypass(
        urlFor: (String) -> String,
        parse: (String) -> T?,
        candidateHosts: List<String> = mirrors,
    ): T? {
        for (h in candidateHosts) {
            val html = cronetGet(urlFor(h)) ?: continue
            parse(html)?.let {
                logger?.info(TAG_BYPASS, "BYPASS 命中 cronet: ${LogSanitizer.sanitizeUrl(urlFor(h))}")
                return it
            }
        }
        for (h in candidateHosts) {
            val html = webViewGet(urlFor(h)) ?: continue
            parse(html)?.let {
                logger?.info(TAG_BYPASS, "BYPASS 命中 webview: ${LogSanitizer.sanitizeUrl(urlFor(h))}")
                return it
            }
        }
        logger?.error(
            TAG_BYPASS,
            "BYPASS 全部失败（cronet / webview × ${candidateHosts.size} 个镜像）",
        )
        return null
    }

    /**
     * 详情 / 目录 / 正文的绕过栈兜底。
     *
     * 原先这三类只有"直连 → 官方 App API"两条路；2026-10 起 App API 已失效，
     * 而某些网络下直连会被 Cloudflare 全线 403（设备日志里 `/book/…` 每次重试 4 次仍 403），
     * 于是详情页直接报"书籍信息获取失败"。这三类同样是 GET + HTML，完全可以复用
     * [fetchWithBypass]（Cronet 优先），比 App API 更可靠。
     */
    private suspend fun fetchBookInfoWithBypass(id: Int): BookInfo? =
        fetchWithBypass(
            urlFor = { h -> "$h/book/$id.htm" },
            parse = { html -> Parsers.parseBookInfo(html, id).takeIf { it.title.isNotBlank() } },
        )

    private suspend fun fetchVolumesWithBypass(bookId: Int, groupId: Int): List<Volume>? =
        fetchWithBypass(
            urlFor = { h -> "$h/novel/$groupId/$bookId/index.htm" },
            parse = { html -> Parsers.parseChapterIndex(html).takeIf { it.isNotEmpty() } },
        )

    private suspend fun fetchChapterWithBypass(gid: Int, bookId: Int, cid: String): ChapterContent? =
        fetchWithBypass(
            urlFor = { h -> "$h/novel/$gid/$bookId/$cid.htm" },
            parse = { html ->
                Parsers.parseChapter(html)
                    .takeIf { it.text.isNotBlank() || it.images.isNotEmpty() }
            },
        )

    /**
     * GET via Cronet, carrying the app's session cookies; returns null on failure/timeout.
     *
     * 用挂起等待替代 `CountDownLatch.await(8s)`：原先每个 Cronet 请求都要独占一个
     * IO 线程空等最多 8 秒（并发几个请求就白白占住几个线程），而且协程被取消时
     * 线程仍会等到超时才释放。改成 `suspendCancellableCoroutine` 后：
     * 线程在等待期间被释放、取消能立刻传播到 `UrlRequest.cancel()`。
     */
    private suspend fun cronetGet(url: String): String? {
        val startedAt = System.currentTimeMillis()
        val response = cronetHttp.execute(
            CronetHttpClient.Request(
                url = url,
                headers = browserHeaders(refererFor(url), uaFor(url)),
                useCookies = true,
                timeoutMs = 20_000L,
                redirectTransform = Wenku8Hosts::secureMainSiteUrl,
            )
        )
        val html = response?.body?.let { String(it, GB18030) }
        if (response == null) {
            logger?.warn(
                TAG_BYPASS,
                "CRONET GET ${LogSanitizer.sanitizeUrl(url)} -> 失败/超时（${elapsedMs(startedAt)} ms）",
            )
        } else {
            logger?.info(
                TAG_BYPASS,
                "CRONET GET ${LogSanitizer.sanitizeUrl(url)} -> ${response.statusCode}" +
                    "（${response.body.size} bytes）",
            )
        }
        return html
    }

    /**
     * Cronet POST（表单）。搜索 `/so.php` 只接受 POST，而直连 POST 会被 Cloudflare 403；
     * Cronet 与 GET 共用同一套 TLS 指纹与 Cookie，是这条请求唯一可靠的通道。
     *
     * @return 最终 URL（精确命中会 302 到 `/book/{id}.htm`）+ 解码后的 HTML。
     */
    private suspend fun cronetPost(url: String, formBytes: ByteArray): Pair<String, String>? {
        val startedAt = System.currentTimeMillis()
        val response = cronetHttp.execute(
            CronetHttpClient.Request(
                url = url,
                method = "POST",
                headers = browserHeaders(url.substringBefore('?'), sameOriginForm = true) + mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Origin" to base,
                ),
                body = formBytes,
                useCookies = true,
                timeoutMs = 20_000L,
                redirectTransform = Wenku8Hosts::secureMainSiteUrl,
            )
        )
        if (response == null) {
            logger?.warn(
                TAG_BYPASS,
                "CRONET POST ${LogSanitizer.sanitizeUrl(url)} -> 失败/超时（${elapsedMs(startedAt)} ms）",
            )
            return null
        }
        logger?.info(
            TAG_BYPASS,
            "CRONET POST ${LogSanitizer.sanitizeUrl(response.url)} -> ${response.statusCode}" +
                "（${response.body.size} bytes）",
        )
        return response.url to String(response.body, GB18030)
    }

    /**
     * Loads the page in a hidden WebView so Cloudflare's JS challenge runs like in a
     * real browser, then reads back the rendered DOM. Carries the app's session cookies.
     *
     * 挑战通过后会做两件事（参考 LightNovelReader 的 cf_clearance 思路）：
     * 1. 记录本次使用的 UA（cf_clearance 与该 UA 绑定，后续复用需一致）；
     * 2. 把 WebView 写入的 Cookie（含 cf_clearance / __cf_bm）持久化到 CookieStore，
     *    之后 Cronet 直接带令牌请求，无需每次重跑 WebView。
     */
    private suspend fun webViewGet(url: String): String? {
        val startedAt = System.currentTimeMillis()
        val html = webViewGetRaw(url)
        if (html != null) {
            logger?.info(
                TAG_BYPASS,
                "WEBVIEW GET ${LogSanitizer.sanitizeUrl(url)} -> ${html.length} chars" +
                    "（${elapsedMs(startedAt)} ms）",
            )
        } else {
            logger?.warn(
                TAG_BYPASS,
                "WEBVIEW GET ${LogSanitizer.sanitizeUrl(url)} -> 失败/超时（${elapsedMs(startedAt)} ms）",
            )
        }
        return html
    }

    private suspend fun webViewGetRaw(url: String): String? =
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
                            var usedUa = DEVICE_UA
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
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val requested = request?.url?.toString() ?: return false
                                        val secure = Wenku8Hosts.secureMainSiteUrl(requested)
                                        if (secure == requested) return false
                                        view?.loadUrl(secure)
                                        return true
                                    }

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
                                                                    CookieManager.getInstance().flush()
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
     * 抓取图片原始字节（阅读器插图的「保存图片」用）。
     *
     * 与 [getBytes] 的唯一差别是 Referer：图片走站点防盗链，必须带
     * [Wenku8Hosts.IMAGE_REFERER]，用同域根地址会被拒绝。地址同样统一升级为 HTTPS
     * （站点页面给的是 http，明文流量已被 networkSecurityConfig 禁止）。
     */
    suspend fun imageBytes(url: String): ByteArray =
        getBytes(
            url = Wenku8Hosts.normalizeImageUrl(url),
            referer = Wenku8Hosts.IMAGE_REFERER,
        )

}
