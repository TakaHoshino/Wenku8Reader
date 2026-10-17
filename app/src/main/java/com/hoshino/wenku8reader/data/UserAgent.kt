package com.hoshino.wenku8reader.data

/**
 * 浏览器 User-Agent 的构造（**纯函数**，`UserAgentTest` 覆盖）。
 *
 * 为什么不再"随机生成"：2026-10 站点的 Cloudflare 对自动化特征的识别变严，
 * 随机拼出来的 UA 会同时暴露三处破绽——
 * 1. 不存在的组合（如 "Android 8.1.0; Pixel 7"、"Android 15; Redmi K40"）；
 * 2. 不可能的版本号（`Chrome/140.0.12.3`、`Safari/537.118`——真实值固定是 `537.36`）；
 * 3. **同一个进程每次请求换 UA**：真实设备不会这么干，而 `cf_clearance` 又与 UA 绑定，
 *    换 UA 还会让挑战令牌复用失效。
 *
 * 参考 LightNovelReader 2026-10-02 的 `fix(web): 修正UA生成器`：改用**真实 Build 信息** +
 * 固定 WebKit/Safari 版本 + 固定 Chrome 主版本，并在进程内缓存。
 */
internal object UserAgent {

    /** Chrome 稳定版主版本（随稳定版更新；2026-10 为 153）。 */
    const val CHROME_MAJOR = 153

    /** WebKit / Safari 版本在 UA 缩减后是固定值，不要随机。 */
    private const val APPLE_WEBKIT_VERSION = "537.36"
    private const val SAFARI_VERSION = "537.36"

    /**
     * 用设备真实信息拼 UA。
     *
     * Chrome 用 `<major>.0.0.0`：这是 Chrome 启用 UA 缩减后的实际形态（不是"省略版本号"）。
     * 空值兜底成 `Android 10; K`——与 Chrome 缩减 UA 的写法一致。
     */
    fun build(
        androidVersion: String,
        model: String,
        chromeMajor: Int = CHROME_MAJOR,
    ): String {
        val os = androidVersion.trim().ifBlank { "10" }
        val device = model.trim().ifBlank { "K" }
        return "Mozilla/5.0 (Linux; Android $os; $device) " +
            "AppleWebKit/$APPLE_WEBKIT_VERSION (KHTML, like Gecko) " +
            "Chrome/$chromeMajor.0.0.0 Mobile Safari/$SAFARI_VERSION"
    }
}
