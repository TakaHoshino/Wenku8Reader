package com.hoshino.wenku8reader.data.log

/**
 * 日志脱敏与单行化（**纯函数**，`LogSanitizerTest` 覆盖）。
 *
 * 日志会被用户导出并发送给开发者，所以"能记什么"必须先定死：
 * - URL 里的 `user:pass@` 与敏感查询参数（密码 / 令牌 / 会话）一律替换成 `***`；
 * - fragment 直接丢弃（不影响请求语义，却可能带令牌）；
 * - 单行化：异常信息、站点返回片段里的换行会破坏"一行一条记录"的结构；
 * - 截断：超长 URL / 错误信息没有诊断价值，还会把日志文件迅速撑大。
 *
 * **不在这里处理** Cookie、请求体与正文——调用方从源头就不该把它们传进来。
 */
internal object LogSanitizer {

    /** 值必须打码的查询参数（登录 / 会话 / 鉴权相关）。 */
    private val SENSITIVE_PARAMS = setOf(
        "password", "passwd", "pass", "pwd",
        "token", "access_token", "refresh_token",
        "session", "sessionid", "sid", "jsessionid",
        "cookie", "auth", "authorization", "key", "apikey",
        "ticket", "code", "secret",
    )

    private val MULTI_SPACE = Regex(" {2,}")

    private const val MAX_URL_LENGTH = 400
    private const val MAX_MESSAGE_LENGTH = 500

    /** 处理一条 URL：掩掉 userinfo、敏感查询参数与 fragment，并限长。 */
    fun sanitizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withoutFragment = trimmed.substringBefore('#')
        val queryIndex = withoutFragment.indexOf('?')
        if (queryIndex < 0) return truncate(maskUserInfo(withoutFragment), MAX_URL_LENGTH)

        val base = maskUserInfo(withoutFragment.substring(0, queryIndex))
        val query = withoutFragment.substring(queryIndex + 1)
        val maskedQuery = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) {
                pair
            } else {
                val key = pair.substring(0, eq)
                if (key.lowercase() in SENSITIVE_PARAMS) "$key=***" else pair
            }
        }
        return truncate("$base?$maskedQuery", MAX_URL_LENGTH)
    }

    /** 把 `scheme://user:pass@host/...` 的 userinfo 换成 `***`。 */
    private fun maskUserInfo(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val authorityStart = schemeEnd + 3
        val slash = url.indexOf('/', authorityStart)
        val authorityEnd = if (slash < 0) url.length else slash
        val at = url.lastIndexOf('@', authorityEnd - 1)
        if (at < authorityStart) return url
        return url.substring(0, authorityStart) + "***@" + url.substring(at + 1)
    }

    /** 单行化 + 去掉重复空白 + 限长。 */
    fun sanitizeMessage(raw: String): String {
        val oneLine = raw
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .let { MULTI_SPACE.replace(it, " ") }
            .trim()
        return truncate(oneLine, MAX_MESSAGE_LENGTH)
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"
}
