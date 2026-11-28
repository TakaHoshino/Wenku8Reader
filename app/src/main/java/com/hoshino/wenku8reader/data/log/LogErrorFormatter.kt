package com.hoshino.wenku8reader.data.log

import org.chromium.net.NetworkException
import org.chromium.net.QuicException

/**
 * 生成适合写入单行日志的紧凑异常诊断。
 *
 * 不直接写完整 printStackTrace：日志文件只有约 1.5 MiB 上限，完整堆栈会迅速挤掉
 * 其他请求记录；但只写类名又不足以定位 HTTP/2、DNS、TLS 等底层问题。这里保留
 * 异常链、Cronet 结构化错误码和每层最多 4 个调用帧。
 */
internal object LogErrorFormatter {
    fun format(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSES) {
            if (depth > 0) append(" <- caused by ")
            append(current.javaClass.simpleName)
            current.message?.takeIf { it.isNotBlank() }?.let {
                append('(')
                append(it)
                append(')')
            }
            if (current is NetworkException) {
                append("[cronetError=")
                append(current.errorCode)
                append(' ')
                append(errorCodeName(current.errorCode))
                append(", internal=")
                append(current.cronetInternalErrorCode)
                append(", retryable=")
                append(current.immediatelyRetryable())
                if (current is QuicException) {
                    append(", quic=")
                    append(current.quicDetailedErrorCode)
                }
                append(']')
            }
            current.stackTrace
                .asSequence()
                .filterNot { frame ->
                    frame.className.startsWith("java.") ||
                        frame.className.startsWith("kotlinx.coroutines.")
                }
                .take(MAX_FRAMES_PER_CAUSE)
                .toList()
                .takeIf { it.isNotEmpty() }
                ?.let { frames ->
                    append(" at=")
                    append(
                        frames.joinToString(",") { frame ->
                            "${frame.className.substringAfterLast('.')}." +
                                "${frame.methodName}:${frame.lineNumber}"
                        }
                    )
                }
            current = current.cause
            depth++
        }
    }

    private fun errorCodeName(code: Int): String = when (code) {
        NetworkException.ERROR_HOSTNAME_NOT_RESOLVED -> "HOSTNAME_NOT_RESOLVED"
        NetworkException.ERROR_INTERNET_DISCONNECTED -> "INTERNET_DISCONNECTED"
        NetworkException.ERROR_NETWORK_CHANGED -> "NETWORK_CHANGED"
        NetworkException.ERROR_TIMED_OUT -> "TIMED_OUT"
        NetworkException.ERROR_CONNECTION_CLOSED -> "CONNECTION_CLOSED"
        NetworkException.ERROR_CONNECTION_TIMED_OUT -> "CONNECTION_TIMED_OUT"
        NetworkException.ERROR_CONNECTION_REFUSED -> "CONNECTION_REFUSED"
        NetworkException.ERROR_CONNECTION_RESET -> "CONNECTION_RESET"
        NetworkException.ERROR_ADDRESS_UNREACHABLE -> "ADDRESS_UNREACHABLE"
        NetworkException.ERROR_OTHER -> "OTHER"
        else -> "UNKNOWN"
    }

    private const val MAX_CAUSES = 4
    private const val MAX_FRAMES_PER_CAUSE = 4
}
