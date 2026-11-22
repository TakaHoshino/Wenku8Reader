package com.hoshino.wenku8reader.apicore

/**
 * 将原始 Action 封成服务端接受的 `WK8-RNE-1` 请求。
 *
 * 真实实现应放在受信任的 Android/原生适配层。`exactCookieHeader` 只包含精确的
 * `PHPSESSID=...`，不要在此记录或持久化。
 */
fun interface WenkuEnvelopeSigner {
    fun seal(rawActionQuery: String, exactCookieHeader: String?): SealedRequest?
}

/** 未配置原生 API Core 时的安全实现。 */
object UnavailableEnvelopeSigner : WenkuEnvelopeSigner {
    override fun seal(rawActionQuery: String, exactCookieHeader: String?): SealedRequest? = null
}
