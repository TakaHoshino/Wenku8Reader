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
     * 图片防盗链 Referer。
     *
     * 封面（`img.wenku8.com`）与插图（`pic.777743.xyz`）均校验来源站点，
     * 缺失 Referer 会返回 403，因此统一使用主站地址。
     */
    const val IMAGE_REFERER: String = DEFAULT_BASE + "/"

    /** 镜像清单中的合法取值集合（设置页校验用）。 */
    fun isValidMirror(url: String): Boolean = url in MIRRORS
}
