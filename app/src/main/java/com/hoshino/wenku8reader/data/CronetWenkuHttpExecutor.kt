package com.hoshino.wenku8reader.data

import com.hoshino.wenku8reader.apicore.WenkuHttpExecutor
import com.hoshino.wenku8reader.apicore.WenkuHttpRequest
import com.hoshino.wenku8reader.apicore.WenkuHttpResponse

/**
 * 将 `wenku8-api-core` 的协议层接到应用唯一 HTTP 出口 [CronetHttpClient]。
 *
 * 封包 POST 的 Cookie 由核心层精确写入请求头，因此这里固定 `useCookies = false`，
 * 避免把 CookieStore 中的其他 Cookie 一并带到中转主机。
 */
class CronetWenkuHttpExecutor(
    private val cronetHttp: CronetHttpClient,
) : WenkuHttpExecutor {
    override suspend fun execute(request: WenkuHttpRequest): WenkuHttpResponse? {
        val response = cronetHttp.execute(
            CronetHttpClient.Request(
                url = request.url,
                method = request.method,
                headers = request.headers,
                body = request.body,
                useCookies = request.useCookies,
                timeoutMs = request.timeoutMs,
                maxRedirects = request.maxRedirects,
                redirectTransform = Wenku8Hosts::secureMainSiteUrl,
            )
        ) ?: return null
        return WenkuHttpResponse(
            url = response.url,
            statusCode = response.statusCode,
            headers = response.headers,
            body = response.body,
        )
    }
}
