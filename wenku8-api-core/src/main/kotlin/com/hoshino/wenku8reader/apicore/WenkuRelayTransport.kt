package com.hoshino.wenku8reader.apicore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 与参考 App 行为和参数一致的无状态 HTTP 传输层。
 *
 * 封包由 [WenkuEnvelopeSigner] 提供；本类只负责：
 * - 发送精确的 session Cookie
 * - gzip 解码
 * - 401 校时重试、403/426/429 分类
 * - 禁止重定向，避免 Cookie 与封包泄露到其他主机
 */
class WenkuRelayTransport(
    private val signer: WenkuEnvelopeSigner,
    private val httpExecutor: WenkuHttpExecutor,
    private val sessionCookieStore: WenkuSessionCookieStore = InMemorySessionCookieStore(),
    private val serverClockOffsetUpdater: (Long) -> Unit = {},
    private val onUpgradeRequired: (Int?, String?) -> Unit = { _, _ -> },
    private val onSoftUpdateHint: (Int?) -> Unit = {},
    private val allowedRelayHosts: Set<String> = DEFAULT_ALLOWED_RELAY_HOSTS,
    private val totalPostDeadlineMs: Long = 10_000L,
    private val monotonicClockMillis: () -> Long = {
        System.nanoTime() / 1_000_000L
    },
) : WenkuActionTransport {
    override suspend fun postSealedAction(
        rawActionQuery: String,
        @Suppress("UNUSED_PARAMETER")
        isIdempotentReadOnly: Boolean,
    ): RelayPostResult {
        if (rawActionQuery.isBlank()) {
            return RelayPostResult.NetworkFailure("empty_action_query")
        }

        val startedAt = monotonicClockMillis()
        return withTimeoutOrNull(totalPostDeadlineMs) {
            val first = sealForCurrentSession(rawActionQuery)
            when (first) {
                is SealOutcome.NotConfigured -> RelayPostResult.NotConfigured
                is SealOutcome.Failure -> RelayPostResult.NetworkFailure(first.reason)
                is SealOutcome.Success -> {
                    val initial = executeAttempt(first.attempt, remainingBudgetMs(startedAt))
                    when (initial) {
                        is AttemptOutcome.HttpClockSkew -> {
                            serverClockOffsetUpdater(initial.serverTimeMs)
                            val retry = sealForCurrentSession(rawActionQuery)
                            when (retry) {
                                is SealOutcome.NotConfigured -> RelayPostResult.NotConfigured
                                is SealOutcome.Failure ->
                                    RelayPostResult.NetworkFailure(retry.reason)
                                is SealOutcome.Success -> executeAttempt(
                                    retry.attempt,
                                    remainingBudgetMs(startedAt),
                                ).toRelayResult()
                            }
                        }
                        else -> initial.toRelayResult()
                    }
                }
            }
        } ?: RelayPostResult.NetworkFailure("deadline_exhausted")
    }

    override suspend fun getBytes(url: String): ByteArray? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.equals("Unknown", ignoreCase = true)) return null
        return try {
            val response = httpExecutor.execute(
                WenkuHttpRequest(
                    url = trimmed,
                    method = "GET",
                    headers = mapOf("Accept-Encoding" to "gzip"),
                    useCookies = false,
                    timeoutMs = totalPostDeadlineMs,
                    maxRedirects = 6,
                )
            ) ?: return null
            if (response.statusCode != 200) null else readDecodedBody(response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun clearSessionCookie() {
        sessionCookieStore.clearPhpSessionId()
    }

    private fun sealForCurrentSession(rawActionQuery: String): SealOutcome {
        val sessionId = sessionCookieStore.getPhpSessionId()
        val exactCookie = sessionId.takeIf { it.isNotBlank() }?.let { "PHPSESSID=$it" }
        val request = try {
            signer.seal(rawActionQuery, exactCookie)
        } catch (_: LinkageError) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        } catch (error: RuntimeException) {
            return SealOutcome.Failure(error.message ?: "sealing_failed")
        } ?: return SealOutcome.NotConfigured

        if (request.endpointUrl.equals("Unknown", ignoreCase = true)) {
            return SealOutcome.NotConfigured
        }
        val host = runCatching { URI(request.endpointUrl).host }.getOrNull()
            ?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank() || host !in allowedRelayHosts) {
            return SealOutcome.Failure("invalid_endpoint_host")
        }
        return SealOutcome.Success(
            SealedAttempt(
                request = request,
                exactCookieHeader = exactCookie,
            )
        )
    }

    private suspend fun executeAttempt(
        attempt: SealedAttempt,
        remainingBudgetMs: Long,
    ): AttemptOutcome {
        if (remainingBudgetMs <= 0) {
            return AttemptOutcome.TransportFailure(
                IOException("deadline_exhausted")
            )
        }
        val headers = buildMap {
            put("Accept-Encoding", "gzip")
            put("Content-Type", attempt.request.contentType)
            putAll(attempt.request.headers)
            attempt.exactCookieHeader?.let { put("Cookie", it) }
        }
        return try {
            val response = httpExecutor.execute(
                WenkuHttpRequest(
                    url = attempt.request.endpointUrl,
                    method = "POST",
                    headers = headers,
                    body = attempt.request.body,
                    useCookies = false,
                    timeoutMs = remainingBudgetMs.coerceAtLeast(1L),
                    maxRedirects = 0,
                )
            ) ?: return AttemptOutcome.TransportFailure(
                IOException("transport_unavailable")
            )
            extractPhpSessionId(response.headers
                .filter { it.first.equals("Set-Cookie", ignoreCase = true) }
                .map { it.second })
                ?.let(sessionCookieStore::setPhpSessionId)

            when (response.statusCode) {
                200 -> {
                    if (response.header(HEADER_UPDATE_HINT)?.trim() == "1") {
                        onSoftUpdateHint(
                            response.header(HEADER_MIN_VERSION)?.trim()?.toIntOrNull()
                        )
                    }
                    AttemptOutcome.HttpSuccess(readDecodedBody(response))
                }
                426 -> {
                    val (code, name) = parseUpgradeRequiredPayload(readControlBody(response))
                    AttemptOutcome.HttpUpgradeRequired(code, name)
                }
                401 -> {
                    val serverTimeMs = parseClockSkewServerTimeMs(
                        readControlBody(response)
                    )
                    if (serverTimeMs != null) {
                        AttemptOutcome.HttpClockSkew(serverTimeMs)
                    } else {
                        AttemptOutcome.HttpAuthRejected
                    }
                }
                403 -> AttemptOutcome.HttpAuthRejected
                429 -> AttemptOutcome.HttpRateLimited
                else -> AttemptOutcome.HttpNon200(response.statusCode)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AttemptOutcome.TransportFailure(error)
        }
    }

    private fun remainingBudgetMs(startedAt: Long): Long =
        totalPostDeadlineMs - (monotonicClockMillis() - startedAt).coerceAtLeast(0L)

    private fun readControlBody(response: WenkuHttpResponse): String =
        runCatching {
            String(readDecodedBody(response, maxBytes = MAX_CONTROL_JSON_BYTES), Charsets.UTF_8)
        }.getOrDefault("")

    private fun readDecodedBody(
        response: WenkuHttpResponse,
        maxBytes: Int = MAX_RESPONSE_BYTES,
    ): ByteArray {
        val contentEncoding = response.header("Content-Encoding")?.lowercase(Locale.ROOT).orEmpty()
        val effective = if (contentEncoding.contains("gzip")) {
            if (response.body.isEmpty()) return ByteArray(0)
            runCatching {
                GZIPInputStream(ByteArrayInputStream(response.body))
            }.getOrElse {
                // Cronet 可能在保留响应头的同时已解压正文；无法按 gzip 读取时按原始字节返回。
                return response.body.copyOf(minOf(response.body.size, maxBytes))
            }
        } else {
            ByteArrayInputStream(response.body)
        }

        return effective.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (total < maxBytes) {
                val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (count == -1) break
                output.write(buffer, 0, count)
                total += count
            }
            output.toByteArray()
        }
    }

    private sealed interface SealOutcome {
        data class Success(val attempt: SealedAttempt) : SealOutcome
        data class Failure(val reason: String) : SealOutcome
        data object NotConfigured : SealOutcome
    }

    private data class SealedAttempt(
        val request: SealedRequest,
        val exactCookieHeader: String?,
    )

    private sealed interface AttemptOutcome {
        data class HttpSuccess(val bodyBytes: ByteArray) : AttemptOutcome
        data class HttpUpgradeRequired(
            val minVersionCode: Int?,
            val minVersionName: String?,
        ) : AttemptOutcome
        data class HttpClockSkew(val serverTimeMs: Long) : AttemptOutcome
        data object HttpAuthRejected : AttemptOutcome
        data object HttpRateLimited : AttemptOutcome
        data class HttpNon200(val statusCode: Int) : AttemptOutcome
        data class TransportFailure(val error: Exception) : AttemptOutcome
    }

    private fun AttemptOutcome.toRelayResult(): RelayPostResult = when (this) {
        is AttemptOutcome.HttpSuccess -> RelayPostResult.Success(bodyBytes)
        is AttemptOutcome.HttpUpgradeRequired -> {
            onUpgradeRequired(minVersionCode, minVersionName)
            RelayPostResult.UpgradeRequired(minVersionCode, minVersionName)
        }
        is AttemptOutcome.HttpClockSkew -> RelayPostResult.AuthRejected
        AttemptOutcome.HttpAuthRejected -> RelayPostResult.AuthRejected
        AttemptOutcome.HttpRateLimited -> RelayPostResult.RateLimited
        is AttemptOutcome.HttpNon200 -> RelayPostResult.NetworkFailure("http_$statusCode")
        is AttemptOutcome.TransportFailure ->
            RelayPostResult.NetworkFailure(error.message ?: "transport_failure")
    }

    companion object {
        private const val HEADER_UPDATE_HINT = "X-WK8-Update-Hint"
        private const val HEADER_MIN_VERSION = "X-WK8-Min-Version"
        private const val MAX_CONTROL_JSON_BYTES = 4096
        private const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024

        val DEFAULT_ALLOWED_RELAY_HOSTS: Set<String> = setOf(
            "mewx-android.wenku8.app",
            "wenku8-relay.mewx.org",
        )

        internal fun extractPhpSessionId(setCookieHeaders: List<String>): String? {
            for (header in setCookieHeaders) {
                val marker = "PHPSESSID="
                val index = header.indexOf(marker)
                if (index < 0) continue
                val value = header.substring(index + marker.length)
                    .substringBefore(';')
                    .trim()
                if (value.isNotEmpty()) return value
            }
            return null
        }

        internal fun parseClockSkewServerTimeMs(rawBody: String): Long? {
            val trimmed = rawBody.trim()
            if (!CLOCK_SKEW_ERROR.containsMatchIn(trimmed)) return null
            return SERVER_TIME_MS.find(trimmed)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?.takeIf { it > 0 }
        }

        internal fun parseUpgradeRequiredPayload(rawBody: String): Pair<Int?, String?> {
            val trimmed = rawBody.trim()
            val code = MIN_VERSION_CODE.find(trimmed)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
            val name = MIN_VERSION_NAME.find(trimmed)
                ?.groupValues?.getOrNull(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            return code to name
        }

        private val CLOCK_SKEW_ERROR = Regex(
            """"error"\s*:\s*"CLOCK_SKEW""""
        )
        private val SERVER_TIME_MS = Regex(
            """"serverTimeMs"\s*:\s*(\d+)"""
        )
        private val MIN_VERSION_CODE = Regex(
            """"minVersionCode"\s*:\s*(\d+)"""
        )
        private val MIN_VERSION_NAME = Regex(
            """"minVersionName"\s*:\s*"((?:\\.|[^"\\])*)""""
        )

    }
}
