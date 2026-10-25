package com.hoshino.wenku8reader.data

import android.content.Context
import com.hoshino.wenku8reader.data.log.AppLogger
import com.hoshino.wenku8reader.data.log.LogSanitizer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * 应用的统一 HTTP 网络栈。
 *
 * 所有可替换的 HTTP 请求（wenku8、GitHub 更新、图片）都应通过这里，而不是另行创建
 * OkHttp/HttpURLConnection。Cronet 与 Cloudflare 后面的真实浏览器使用同一套 Chromium
 * 网络能力，TLS/HTTP2 指纹也更不容易触发硬拦截。
 *
 * 这里是刻意与业务无关的薄封装：状态码不抛异常，调用方自行决定重试、解析与错误提示。
 * 若网络失败/超时则返回 null。
 */
class CronetHttpClient(
    context: Context,
    private val cookieStore: CookieStore,
    private val logger: AppLogger? = null,
) {

    data class Request(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray? = null,
        val useCookies: Boolean = false,
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
        val redirectTransform: (String) -> String = { it },
    )

    data class Response(
        val url: String,
        val statusCode: Int,
        val headers: List<Pair<String, String>>,
        val body: ByteArray,
    ) {
        fun header(name: String): String? =
            headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

        fun contentLength(): Long =
            header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 } ?: -1L

        val isSuccessful: Boolean get() = statusCode in 200..299
    }

    private sealed interface Hop {
        data class Complete(
            val url: String,
            val statusCode: Int,
            val headers: List<Pair<String, String>>,
            val body: ByteArray,
        ) : Hop

        data class Redirect(val statusCode: Int, val location: String) : Hop
    }

    private val appContext = context.applicationContext
    private val executor: Executor = Dispatchers.IO.asExecutor()

    private val engineLazy: Lazy<CronetEngine?> = lazy {
        runCatching { CronetEngine.Builder(appContext).build() }.getOrNull()
    }

    val isAvailable: Boolean get() = engineLazy.value != null

    /**
     * 执行一个逻辑请求（含重定向）。
     *
     * [collectBody] = false 时不在内存里保留响应正文，适合下载大文件；
     * [onBodyChunk] 会按到达顺序收到数据块与 Content-Length（未知时为 -1）。
     */
    suspend fun execute(
        request: Request,
        collectBody: Boolean = true,
        onBodyChunk: ((ByteArray, Long) -> Unit)? = null,
    ): Response? {
        val engine = engineLazy.value ?: return null
        return withTimeoutOrNull(request.timeoutMs) {
            var currentUrl = request.url
            var currentMethod = request.method.uppercase()
            var currentBody = request.body
            if (currentBody != null && currentMethod == "GET") currentMethod = "POST"
            val visited = HashSet<String>()

            repeat(request.maxRedirects + 1) {
                if (!visited.add("$currentMethod $currentUrl")) {
                    logger?.warn(TAG, "重定向成环: ${LogSanitizer.sanitizeUrl(currentUrl)}")
                    return@withTimeoutOrNull null
                }

                when (
                    val hop = requestOnce(
                        engine = engine,
                        url = currentUrl,
                        method = currentMethod,
                        headers = request.headers,
                        body = currentBody,
                        useCookies = request.useCookies,
                        collectBody = collectBody,
                        onBodyChunk = onBodyChunk,
                    )
                ) {
                    null -> return@withTimeoutOrNull null
                    is Hop.Complete -> return@withTimeoutOrNull Response(
                        url = hop.url,
                        statusCode = hop.statusCode,
                        headers = hop.headers,
                        body = hop.body,
                    )
                    is Hop.Redirect -> {
                        val nextUrl = request.redirectTransform(hop.location)
                        logger?.info(
                            TAG,
                            "重定向 ${hop.statusCode}: " +
                                "${LogSanitizer.sanitizeUrl(currentUrl)} -> " +
                                LogSanitizer.sanitizeUrl(nextUrl),
                        )
                        if (hop.statusCode != 307 && hop.statusCode != 308) {
                            currentBody = null
                            if (currentMethod != "HEAD") currentMethod = "GET"
                        }
                        currentUrl = nextUrl
                    }
                }
            }
            logger?.warn(
                TAG,
                "重定向超过 ${request.maxRedirects} 次: " +
                    LogSanitizer.sanitizeUrl(request.url),
            )
            null
        }
    }

    fun close() {
        if (engineLazy.isInitialized()) {
            runCatching { engineLazy.value?.shutdown() }
        }
    }

    private suspend fun requestOnce(
        engine: CronetEngine,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        useCookies: Boolean,
        collectBody: Boolean,
        onBodyChunk: ((ByteArray, Long) -> Unit)?,
    ): Hop? = suspendCancellableCoroutine { cont ->
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull()
        if (httpUrl == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val readBuffer = ByteBuffer.allocateDirect(64 * 1024)
        val collected = if (collectBody) ByteArrayOutputStream() else null
        var bytesRead = 0L
        var contentLength = -1L

        fun saveCookies(info: UrlResponseInfo) {
            if (!useCookies) return
            runCatching {
                info.allHeadersAsList
                    .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                    .mapNotNull { (_, value) ->
                        runCatching { Cookie.parse(httpUrl, value) }.getOrNull()
                    }
                    .takeIf { it.isNotEmpty() }
                    ?.let { cookieStore.saveFromResponse(httpUrl, it) }
            }
        }

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String?,
            ) {
                saveCookies(info)
                val location = newLocationUrl
                if (location == null) {
                    if (cont.isActive) cont.resume(null)
                } else if (cont.isActive) {
                    cont.resume(Hop.Redirect(info.httpStatusCode, location))
                }
                request.cancel()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                saveCookies(info)
                contentLength = info.allHeadersAsList
                    .firstOrNull { it.key.equals("Content-Length", true) }
                    ?.value
                    ?.toLongOrNull()
                    ?.takeIf { it >= 0 }
                    ?: -1L
                request.read(readBuffer)
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer,
            ) {
                byteBuffer.flip()
                val chunk = ByteArray(byteBuffer.remaining())
                byteBuffer.get(chunk)
                byteBuffer.clear()
                if (chunk.isNotEmpty()) {
                    collected?.write(chunk)
                    bytesRead += chunk.size
                    onBodyChunk?.let { callback ->
                        runCatching { callback(chunk, contentLength) }
                    }
                }
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                if (cont.isActive) {
                    cont.resume(
                        Hop.Complete(
                            url = info.url,
                            statusCode = info.httpStatusCode,
                            headers = info.allHeadersAsList.map { it.key to it.value },
                            body = collected?.toByteArray() ?: ByteArray(0),
                        )
                    )
                }
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: CronetException,
            ) {
                logger?.warn(
                    TAG,
                    "$method ${LogSanitizer.sanitizeUrl(url)} -> " +
                        "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                )
                if (cont.isActive) cont.resume(null)
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                if (cont.isActive) cont.resume(null)
            }
        }

        val builder = runCatching {
            engine.newUrlRequestBuilder(url, callback, executor)
                .setHttpMethod(method)
        }.getOrNull()
        if (builder == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        if (useCookies && headers.keys.none { it.equals("Cookie", true) }) {
            val cookieHeader = cookieStore.loadForRequest(httpUrl)
                .joinToString("; ") { "${it.name}=${it.value}" }
            if (cookieHeader.isNotEmpty()) builder.addHeader("Cookie", cookieHeader)
        }
        if (body != null) {
            if (headers.keys.none { it.equals("Content-Type", true) }) {
                builder.addHeader("Content-Type", "application/x-www-form-urlencoded")
            }
            builder.setUploadDataProvider(uploadProvider(body), executor)
        }
        val request = runCatching { builder.build() }.getOrNull()
        if (request == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { request.cancel() } }
        request.start()
    }

    @Suppress("DEPRECATION")
    private fun uploadProvider(bytes: ByteArray): UploadDataProvider =
        org.chromium.net.UploadDataProviders.create(bytes)

    private companion object {
        const val TAG = "NET"
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val DEFAULT_MAX_REDIRECTS = 6
    }
}
