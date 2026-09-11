package com.hoshino.wenku8reader.data

/**
 * wenku8 站点主机常量（单一来源）。
 *
 * 镜像清单此前在 `Wenku8Client`、`ReaderSettings`、`SettingsScreen` 三处各自硬编码，
 * 新增镜像需同步改多处、且极易漂移；图片防盗链 Referer 也在四处重复。
 * 统一收敛到此处后，镜像变更只需改 [MIRRORS] 一处。
 */
object Wenku8Hosts {

    /** 可用镜像（第一个为默认主站）。 */
    val MIRRORS: List<String> = listOf(
        "https://www.wenku8.cc",
        "https://www.wenku8.net",
        "https://www.wenku8.com",
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
