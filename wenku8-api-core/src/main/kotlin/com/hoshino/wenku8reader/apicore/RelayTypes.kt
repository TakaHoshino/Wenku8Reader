package com.hoshino.wenku8reader.apicore

sealed interface RelayPostResult {
    data class Success(val bodyBytes: ByteArray) : RelayPostResult {
        override fun equals(other: Any?): Boolean =
            other is Success && bodyBytes.contentEquals(other.bodyBytes)

        override fun hashCode(): Int = bodyBytes.contentHashCode()
    }

    data object NotConfigured : RelayPostResult

    data class UpgradeRequired(
        val minVersionCode: Int? = null,
        val minVersionName: String? = null,
    ) : RelayPostResult

    data object AuthRejected : RelayPostResult

    data object RateLimited : RelayPostResult

    data class NetworkFailure(val debugDetail: String? = null) : RelayPostResult
}

interface WenkuSessionCookieStore {
    fun getPhpSessionId(): String
    fun setPhpSessionId(sessionId: String)
    fun clearPhpSessionId() {}
}

class InMemorySessionCookieStore : WenkuSessionCookieStore {
    private val lock = Any()

    @Volatile
    private var sessionId: String = ""

    override fun getPhpSessionId(): String = sessionId

    override fun setPhpSessionId(sessionId: String) {
        val trimmed = sessionId.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            this.sessionId = trimmed
        }
    }

    override fun clearPhpSessionId() {
        synchronized(lock) {
            sessionId = ""
        }
    }
}

interface WenkuActionTransport {
    suspend fun postSealedAction(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean = false,
    ): RelayPostResult

    suspend fun getBytes(url: String): ByteArray? = null
}

data class WenkuHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null,
    val useCookies: Boolean = false,
    val timeoutMs: Long,
    val maxRedirects: Int = 0,
)

class WenkuHttpResponse(
    val url: String,
    val statusCode: Int,
    val headers: List<Pair<String, String>>,
    body: ByteArray,
) {
    val body: ByteArray = body.copyOf()

    fun header(name: String): String? =
        headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
}

/**
 * 应用层网络适配边界。
 *
 * 生产实现必须复用项目的 `CronetHttpClient`，核心模块不得自行创建 HTTP 客户端。
 * 返回 null 表示超时或网络失败；HTTP 非 `2xx/3xx` 仍应返回给状态机分类。
 */
fun interface WenkuHttpExecutor {
    suspend fun execute(request: WenkuHttpRequest): WenkuHttpResponse?
}
