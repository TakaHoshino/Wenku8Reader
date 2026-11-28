package com.hoshino.wenku8reader.apicore

/**
 * v2 API 的单次请求封包。
 *
 * 服务端实际接受的是 [body] 中的二进制 `WK8-RNE-1` 数据；[headers] 通常至少包含
 * `X-WK8-Proto: 2`。构造时复制可变数据，避免签名器缓存被调用方意外修改。
 */
class SealedRequest(
    endpointUrl: String,
    contentType: String,
    headers: Map<String, String>,
    body: ByteArray,
) {
    val endpointUrl: String = endpointUrl.trim().also {
        require(it.isNotEmpty()) { "endpointUrl must not be blank" }
    }
    val contentType: String = contentType.trim().also {
        require(it.isNotEmpty()) { "contentType must not be blank" }
    }
    val headers: Map<String, String> = headers.toMap()
    val body: ByteArray = body.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedRequest) return false
        return endpointUrl == other.endpointUrl &&
            contentType == other.contentType &&
            headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = endpointUrl.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    override fun toString(): String =
        "SealedRequest(endpointUrl=$endpointUrl, contentType=$contentType, " +
            "headers=$headers, bodySize=${body.size})"
}
