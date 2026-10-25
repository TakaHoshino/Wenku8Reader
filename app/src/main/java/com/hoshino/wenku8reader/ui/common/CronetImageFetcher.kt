package com.hoshino.wenku8reader.ui.common

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.hoshino.wenku8reader.data.CronetHttpClient
import okio.Buffer

/**
 * Coil 的专用图片请求类型。
 *
 * 不使用普通 String，是为了避免 Coil 默认的 HttpUriFetcher 抢先匹配；只有这个类型
 * 会由 [CronetImageFetcher] 处理。Keyer 仍以 URL 为键，保持既有内存/磁盘缓存兼容。
 */
data class CronetImageRequest(
    val url: String,
    val referer: String? = null,
) {
    override fun toString(): String = url
}

/**
 * Coil 的 HTTP Fetcher：图片下载同样经应用唯一的 Cronet 网络栈。
 *
 * Coil 只负责尺寸计算、解码和内存/磁盘缓存；这里仅把网络响应体交给它。
 */
class CronetImageFetcher private constructor(
    private val data: CronetImageRequest,
    private val options: Options,
    private val http: CronetHttpClient,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val headers = buildMap {
            options.headers.names().forEach { name ->
                options.headers.get(name)?.let { put(name, it) }
            }
            if (data.referer != null && keys.none { it.equals("Referer", true) }) {
                put("Referer", data.referer)
            }
        }
        val response = http.execute(
            CronetHttpClient.Request(
                url = data.url,
                headers = headers,
                timeoutMs = 30_000L,
            )
        ) ?: return null
        if (!response.isSuccessful) return null
        val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim()
        val source = ImageSource(Buffer().write(response.body), options.context)
        return SourceResult(source, mimeType, DataSource.NETWORK)
    }

    class Keyer : coil.key.Keyer<CronetImageRequest> {
        override fun key(data: CronetImageRequest, options: Options): String = data.url
    }

    class Factory(private val http: CronetHttpClient) : Fetcher.Factory<CronetImageRequest> {
        override fun create(
            data: CronetImageRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = CronetImageFetcher(data, options, http)
    }
}
