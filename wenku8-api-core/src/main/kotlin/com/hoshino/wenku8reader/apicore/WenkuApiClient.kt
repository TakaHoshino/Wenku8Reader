package com.hoshino.wenku8reader.apicore

import java.nio.charset.StandardCharsets

sealed interface WenkuApiResult<out T> {
    data class Success<T>(val data: T) : WenkuApiResult<T>
    data object ApiNotConfigured : WenkuApiResult<Nothing>
    data class UpgradeRequired(
        val minVersionCode: Int? = null,
        val minVersionName: String? = null,
    ) : WenkuApiResult<Nothing>
    data object AuthRejected : WenkuApiResult<Nothing>
    data class NetworkError(val debugDetail: String? = null) : WenkuApiResult<Nothing>
    data class ServerErrorCode(val numericCode: Int) : WenkuApiResult<Nothing>
    data object EmptyResponse : WenkuApiResult<Nothing>
    data class ParseError(val rawPreview: String = "") : WenkuApiResult<Nothing>
}

/**
 * 类型化业务网关。XML 与正文解析由调用方注入，避免核心模块耦合 UI 数据模型。
 */
class WenkuApiClient(
    private val transport: WenkuActionTransport,
) {
    suspend fun <T : Any> fetchXmlResponse(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean = true,
        parser: (String) -> T?,
    ): WenkuApiResult<T> {
        val bytes = when (
            val posted = transport.postSealedAction(
                rawActionQuery = rawActionQuery,
                isIdempotentReadOnly = isIdempotentReadOnly,
            )
        ) {
            is RelayPostResult.Success -> posted.bodyBytes
            RelayPostResult.NotConfigured -> return WenkuApiResult.ApiNotConfigured
            is RelayPostResult.UpgradeRequired -> return WenkuApiResult.UpgradeRequired(
                posted.minVersionCode,
                posted.minVersionName,
            )
            RelayPostResult.AuthRejected -> return WenkuApiResult.AuthRejected
            RelayPostResult.RateLimited ->
                return WenkuApiResult.NetworkError("rate_limited")
            is RelayPostResult.NetworkFailure ->
                return WenkuApiResult.NetworkError(posted.debugDetail)
        }

        val raw = String(bytes, StandardCharsets.UTF_8).trim()
        if (raw.isEmpty()) return WenkuApiResult.ParseError("")
        raw.takeIf { it.length <= 4 }?.toIntOrNull()?.let {
            return WenkuApiResult.ServerErrorCode(it)
        }
        if (looksLikeHtml(raw)) return WenkuApiResult.ParseError(raw.take(120))
        val parsed = parser(raw) ?: return WenkuApiResult.ParseError(raw.take(120))
        return WenkuApiResult.Success(parsed)
    }

    suspend fun fetchTextResponse(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean = true,
        rejectNullLiteral: Boolean = false,
    ): WenkuApiResult<String> {
        val bytes = when (
            val posted = transport.postSealedAction(
                rawActionQuery = rawActionQuery,
                isIdempotentReadOnly = isIdempotentReadOnly,
            )
        ) {
            is RelayPostResult.Success -> posted.bodyBytes
            RelayPostResult.NotConfigured -> return WenkuApiResult.ApiNotConfigured
            is RelayPostResult.UpgradeRequired -> return WenkuApiResult.UpgradeRequired(
                posted.minVersionCode,
                posted.minVersionName,
            )
            RelayPostResult.AuthRejected -> return WenkuApiResult.AuthRejected
            RelayPostResult.RateLimited ->
                return WenkuApiResult.NetworkError("rate_limited")
            is RelayPostResult.NetworkFailure ->
                return WenkuApiResult.NetworkError(posted.debugDetail)
        }

        val raw = String(bytes, StandardCharsets.UTF_8)
        val trimmed = raw.trim { it.isWhitespace() || it == '\uFEFF' }
        if (trimmed.isEmpty()) return WenkuApiResult.EmptyResponse
        if (rejectNullLiteral && trimmed.equals("null", ignoreCase = true)) {
            return WenkuApiResult.EmptyResponse
        }
        trimmed.takeIf { it.length <= 4 }?.toIntOrNull()?.let {
            return WenkuApiResult.ServerErrorCode(it)
        }
        if (looksLikeHtml(trimmed)) return WenkuApiResult.ParseError(trimmed.take(120))
        return WenkuApiResult.Success(raw)
    }

    suspend fun executeNumericMutation(
        rawActionQuery: String,
    ): WenkuApiResult<Unit> {
        val bytes = when (
            val posted = transport.postSealedAction(
                rawActionQuery = rawActionQuery,
                isIdempotentReadOnly = false,
            )
        ) {
            is RelayPostResult.Success -> posted.bodyBytes
            RelayPostResult.NotConfigured -> return WenkuApiResult.ApiNotConfigured
            is RelayPostResult.UpgradeRequired -> return WenkuApiResult.UpgradeRequired(
                posted.minVersionCode,
                posted.minVersionName,
            )
            RelayPostResult.AuthRejected -> return WenkuApiResult.AuthRejected
            RelayPostResult.RateLimited ->
                return WenkuApiResult.NetworkError("rate_limited")
            is RelayPostResult.NetworkFailure ->
                return WenkuApiResult.NetworkError(posted.debugDetail)
        }

        val trimmed = String(bytes, StandardCharsets.UTF_8).trim()
        val code = trimmed.toIntOrNull()
            ?: return WenkuApiResult.ParseError(trimmed.take(120))
        return if (code == 1) {
            WenkuApiResult.Success(Unit)
        } else {
            WenkuApiResult.ServerErrorCode(code)
        }
    }

    private fun looksLikeHtml(value: String): Boolean =
        value.startsWith("<!doctype html", ignoreCase = true) ||
            value.startsWith("<html", ignoreCase = true)
}
