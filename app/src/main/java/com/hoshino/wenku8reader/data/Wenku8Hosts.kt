package com.hoshino.wenku8reader.data

import java.net.URI

/**
 * wenku8 站点主机常量（单一来源）。
 *
 * 镜像清单此前在 `Wenku8Client`、`ReaderSettings`、`SettingsScreen` 三处各自硬编码，
 * 新增镜像需同步改多处、且极易漂移；图片防盗链 Referer 也在四处重复。
 * 统一收敛到此处后，镜像变更只需改 [MIRRORS] 一处。
 */
object Wenku8Hosts {

    /**
     * 可用镜像（第一个为默认主站）。
     *
     * ⚠️ **`https://www.wenku8.com` 已于 2026-10 下线**：实测（PC 与 Android 17 设备一致）
     * 该域名解析到 47.243.247.59，TLS 证书是 `s1.n9z.net`（SAN 只有 `s1.n9z.net`），
     * 主机名校验必然失败——每次冷启动都要白等一次 4.6s 的 `SSLPeerUnverifiedException`。
     * 域名若恢复为站点所有，再把它加回本列表即可。
     * 注意图片（`img.wenku8.com`）、下载（`dl.wenku8.com`）与官方 App API（`app.wenku8.com`）
     * 是**另外的域名**，不受此条影响。
     */
    val MIRRORS: List<String> = listOf(
        "https://www.wenku8.cc",
        "https://www.wenku8.net",
    )

    /** 默认主站镜像。 */
    const val DEFAULT_BASE: String = "https://www.wenku8.cc"

    /**
     * 图片请求携带的 Referer（取自主站地址）。
     *
     * 实测（2026-09）两个图片域名 `img.wenku8.com` / `pic.777743.xyz` 对 Referer
     * **并不敏感**——带 `.cc`/`.net`/`.com` 甚至完全不带 Referer 都返回 200。
     * 因此这里保留 Referer 只是为了兼容站点未来可能启用的防盗链策略，
     * 并非当前加载成功的必要条件。
     */
    const val IMAGE_REFERER: String = DEFAULT_BASE + "/"

    /** 镜像清单中的合法取值集合（设置页校验用）。 */
    fun isValidMirror(url: String): Boolean = url in MIRRORS

    /** 主站镜像的精确主机名；用于把站点返回的明文重定向升级回 HTTPS。 */
    private val MIRROR_HOSTS: Set<String> = MIRRORS.mapNotNull { url ->
        runCatching { URI(url).host }.getOrNull()
    }.toSet()

    /**
     * 站点部分入口仍会返回 `Location: http://www.wenku8.net/...`（例如未登录首页 →
     * `login.php`）。应用默认禁止明文流量，直接跟随会得到
     * `ERR_CLEARTEXT_NOT_PERMITTED`，表现为整站“无法访问”。
     *
     * 仅对两个主站镜像的明文地址做协议升级；只替换开头的 scheme，保留原始 path/query
     * 编码，避免 `URI` 重新序列化时二次编码 `jumpurl` 等嵌套参数。第三方地址不修改。
     */
    fun secureMainSiteUrl(url: String): String {
        val trimmed = url.trim()
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return url
        if (!parsed.scheme.equals("http", ignoreCase = true) || parsed.host !in MIRROR_HOSTS) {
            return url
        }
        return "https://" + trimmed.substringAfter("://")
    }

    /** 图片内容域名（站点给出的地址是明文 http，但这些域名同样支持 https）。 */
    private val IMAGE_HOSTS = listOf("img.wenku8.com", "pic.777743.xyz")

    /**
     * 把图片地址统一升级为 HTTPS。
     *
     * **为什么必须做**：站点页面里的封面/插图给的是**明文 http** 地址
     * （实测 `https://www.wenku8.cc/book/1191.htm` 输出
     * `http://img.wenku8.com/image/1/1191/1191s.jpg`）。应用已按安全评估收紧
     * `networkSecurityConfig`（默认禁止明文流量，仅放行官方 App API），
     * 因此若把 http 地址原样交给 Coil，Android 会直接拦截请求 → **封面全部加载失败**。
     * 两个图片域名都支持 HTTPS（实测 `https://` 返回 200），故在此就近升级协议：
     * 既恢复图片加载，又保持"除 App API 外不外发明文流量"的收紧策略。
     *
     * 其余协议/域名一律原样返回（不擅自改写第三方地址）。
     */
    fun normalizeImageUrl(url: String): String {
        val u = url.trim()
        if (u.isEmpty() || !u.startsWith("http://")) return u
        val host = u.removePrefix("http://").substringBefore('/').substringBefore(':')
        return if (host in IMAGE_HOSTS) "https://" + u.removePrefix("http://") else u
    }
}
