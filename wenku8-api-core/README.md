# wenku8-api-core

Wenku8 Android v2 API 的纯 Kotlin/JVM 复刻层，负责：

- 构造 `action=...` 业务查询
- 定义 `WK8-RNE-1` 封包请求 DTO 与签名器接口
- 组织 `POST /v2/api` 请求并交给应用的统一 HTTP 出口
- 管理 `PHPSESSID`、gzip、超时和 HTTP 状态映射
- 将 XML、文本和数字响应转换成类型化结果

该模块不包含 `libmewx.so`，也不会伪造 `WK8-RNE-1`。生产使用必须注入一个真实的
`WenkuEnvelopeSigner`；未注入时，`UnavailableEnvelopeSigner` 会安全返回
`RelayPostResult.NotConfigured`。

```kotlin
val transport = WenkuRelayTransport(
    signer = UnavailableEnvelopeSigner,
    httpExecutor = cronetWenkuHttpExecutor,
)
val client = WenkuApiClient(transport)

val action = WenkuActionBuilder.novelListWithInfo(
    sortedBy = WenkuActionBuilder.Sort.LAST_UPDATE,
    page = 1,
    language = WenkuActionBuilder.Language.SC,
)

val result = client.fetchXmlResponse(action) { xml ->
    // 交给现有 AppParsers 或 WenkuXmlParsers 解析。
    parseNovelList(xml)
}
```

原生适配器通常放在 Android 应用层，因为它需要 `Context` 初始化，并应遵守原生库的授权、
ABI 和分发约束：

```kotlin
object NativeEnvelopeSigner : WenkuEnvelopeSigner {
    override fun seal(
        rawActionQuery: String,
        exactCookieHeader: String?,
    ): SealedRequest? = nativeSeal(rawActionQuery, exactCookieHeader)
}
```

不要把登录参数、正文或原始封包写入日志。
