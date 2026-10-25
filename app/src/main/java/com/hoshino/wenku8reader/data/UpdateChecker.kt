package com.hoshino.wenku8reader.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.log.AppLogger
import com.hoshino.wenku8reader.data.log.LogSanitizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** GitHub Release 信息（更新检查结果）。 */
data class ReleaseInfo(
    val tag: String,          // 如 "v0.2.0"
    val versionName: String,  // 如 "0.2.0"
    val apkDownloadUrl: String, // 原始 github 下载地址
    val releaseName: String = "",
    /** 由 Release 描述首行的 `versionCode: <N>` 解析而来（null = 旧发布无此字段）。 */
    val versionCode: Long? = null,
)

/**
 * 更新检查：查询 GitHub Releases 最新版（`api.github.com`），下载 APK（直连或 gh-proxy 镜像），
 * 并用 FileProvider 拉起系统安装器。
 */
class UpdateChecker(
    /** 生产装配必传；纯逻辑单测可不构造网络栈。 */
    private val http: CronetHttpClient? = null,
    /** 网络访问日志（可空：单测与工具脚本不需要日志）。 */
    private val logger: AppLogger? = null,
) {

    companion object {
        private const val REPO = "TakaHoshino/Wenku8Reader"
        private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
        private const val API_LIST = "https://api.github.com/repos/$REPO/releases?per_page=20"
        private const val UA = "Wenku8Reader-Android"
        /** gh-proxy 镜像前缀（更新源选择用），后接原始下载地址。 */
        const val GH_PROXY_PREFIX = "https://gh-proxy.com/"

        /** 从 Release 描述首行解析 `versionCode: <N>`。 */
        private val VERSION_CODE_IN_BODY = Regex("versionCode:\\s*(\\d+)")

        /**
         * 查询包签名用的 flags。**必须同时带上已废弃的 [PackageManager.GET_SIGNATURES]**。
         *
         * Android 9/10（API 28/29）的 `PackageManager.getPackageArchiveInfo()` 只在 flags 含
         * `GET_SIGNATURES` 时才去收集证书：
         * `if ((flags & GET_SIGNATURES) != 0) PackageParser.collectCertificates(pkg, false)`
         * （AOSP android-9.0.0_r1 `PackageManager.java:4812`、android-10.0.0_r1 `:5588`）。
         * 只传 `GET_SIGNING_CERTIFICATES` 时 `pkg.mSigningDetails` 仍是 `UNKNOWN`，
         * `generatePackageInfo` 于是把 `pi.signingInfo` 置为 null——校验**恒定判失败**，
         * 用户看到的是「签名校验失败」，可 APK 其实是好的，等于这两代系统上应用内更新彻底不可用。
         * API 30 起平台自己就改成了 `GET_SIGNATURES || GET_SIGNING_CERTIFICATES`
         * （android-11.0.0_r1 `PackageManager.java:6073`），所以两个一起传在所有版本上都正确。
         *
         * 读证书仍然优先走 `signingInfo`（见 [signaturesOf]），这里只是把"收集开关"打开。
         */
        @Suppress("DEPRECATION")
        internal val SIGNATURE_QUERY_FLAGS: Int =
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
    }

    /**
     * 查询最新 Release：
     * - [stable] = true（正式版）：`releases/latest`（最新正式版，排除 prerelease）；
     * - [stable] = false（测试版）：`releases?per_page=20` 中取**最新发布**（按发布时间倒序，
     *   不论是否 prerelease，第一个带 APK 的）——测试版通道也能检到最新正式版。
     * 无可用 Release 时返回 null（404 / 无带 APK 的候选）。
     */
    suspend fun fetchLatest(stable: Boolean): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (stable) API_LATEST else API_LIST
            val resp = requireNotNull(http) { "CronetHttpClient 未配置" }.execute(
                CronetHttpClient.Request(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to UA,
                        "Accept" to "application/vnd.github+json",
                    ),
                    timeoutMs = 30_000L,
                )
            ) ?: error("检查更新失败：网络不可用")
            logger?.info("UPDATE", "GET $url -> ${resp.statusCode}")
            if (resp.statusCode == 404) return@runCatching null
            check(resp.isSuccessful) { "检查更新失败（HTTP ${resp.statusCode}）" }
            val text = resp.body.toString(Charsets.UTF_8)
            if (text.isBlank()) return@runCatching null
            val json = if (stable) {
                JSONObject(text)
            } else {
                pickLatestReleaseWithApk(text) ?: return@runCatching null
            }
            val tag = json.optString("tag_name", "")
            val apkUrl = apkUrlOf(json)
            if (tag.isBlank() || apkUrl == null) null
            else ReleaseInfo(
                tag = tag,
                versionName = tag.removePrefix("v"),
                apkDownloadUrl = apkUrl,
                releaseName = json.optString("name", ""),
                // 由发布描述首行 `versionCode: <N>` 解析（新发布均带；旧发布为 null → 回退 versionName 比较）
                versionCode = VERSION_CODE_IN_BODY
                    .find(json.optString("body", ""))
                    ?.groupValues?.get(1)?.toLongOrNull(),
            )
        }.onFailure {
            logger?.warn("UPDATE", "检查更新失败: ${it.javaClass.simpleName}: ${it.message.orEmpty()}")
        }
    }

    /** 按更新源拼 APK 下载地址（github 直连 / gh-proxy 镜像前缀）。 */
    fun apkUrl(release: ReleaseInfo, source: String): String =
        if (source == "gh_proxy") GH_PROXY_PREFIX + release.apkDownloadUrl else release.apkDownloadUrl

    /** 下载 APK 到 [dest]，进度回调 [onProgress](0..1)。 */
    suspend fun downloadApk(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            logger?.info("UPDATE", "下载 APK: ${LogSanitizer.sanitizeUrl(url)}")
            dest.parentFile?.mkdirs()
            var read = 0L
            val resp = dest.outputStream().use { output ->
                requireNotNull(http) { "CronetHttpClient 未配置" }.execute(
                    CronetHttpClient.Request(
                        url = url,
                        headers = mapOf("User-Agent" to UA),
                        timeoutMs = 120_000L,
                    ),
                    collectBody = false,
                ) { chunk, contentLength ->
                    output.write(chunk)
                    read += chunk.size
                    if (contentLength > 0) {
                        onProgress((read.toFloat() / contentLength).coerceIn(0f, 1f))
                    }
                }
            }
            check(resp != null) { "下载失败：网络不可用" }
            check(resp.isSuccessful) { "下载失败（HTTP ${resp.statusCode}）" }
            logger?.info("UPDATE", "下载完成: ${dest.name}（${dest.length()} bytes）")
            dest
        }.onFailure {
            runCatching { dest.delete() }
            logger?.warn("UPDATE", "下载 APK 失败: ${it.javaClass.simpleName}: ${it.message.orEmpty()}")
        }
    }

    /**
     * 校验下载到的 APK 与当前已安装应用的签名证书是否一致。
     *
     * 为什么必须校验：更新链路允许经 `gh-proxy.com` 第三方镜像前缀下载 APK，
     * 下载完成后直接拉起系统安装器。若不比对签名，中间人或被接管的镜像可下发
     * 任意 APK 诱导用户安装——这是本次评估中唯一具备「可被远程利用」性质的缺陷。
     * 签名不一致一律拒绝安装。
     */
    fun verifyApkSignature(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() == 0L) return false
        val pm = context.packageManager
        val archiveSigs = runCatching {
            pm.getPackageArchiveInfo(apk.absolutePath, SIGNATURE_QUERY_FLAGS)
                ?.let { signaturesOf(it) }
        }.getOrNull()
        if (archiveSigs.isNullOrEmpty()) return false

        val selfSigs = runCatching {
            signaturesOf(pm.getPackageInfo(context.packageName, SIGNATURE_QUERY_FLAGS))
        }.getOrNull()
        if (selfSigs.isNullOrEmpty()) return false

        return archiveSigs == selfSigs
    }

    /**
     * 取包签名证书的 SHA-256 指纹集合（支持多签名）。
     *
     * 优先用 `signingInfo`（API 28+ 的现代接口）；它为 null 时退回旧的 `signatures` 字段。
     * 两者在同一个查询里由同一批证书填充（见 [SIGNATURE_QUERY_FLAGS]），内容一致；
     * 保留这条退路是为了容忍平台/ROM 的实现差异——多一层总比"恒定判失败"强。
     */
    private fun signaturesOf(info: PackageInfo): Set<String>? {
        val certs: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: legacySignatures(info)
        } else {
            legacySignatures(info)
        }
        if (certs.isEmpty()) return null
        return certs.mapTo(mutableSetOf()) { bytes ->
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }
    }

    /** API 28 之前（以及 `signingInfo` 为空时的兜底）读取证书的旧入口。 */
    @Suppress("DEPRECATION")
    private fun legacySignatures(info: PackageInfo): List<ByteArray> =
        info.signatures?.map { it.toByteArray() } ?: emptyList()

    /**
     * 校签后用 FileProvider 拉起系统安装器（覆盖安装/更新包）。
     * 校签失败：删除可疑文件、提示用户、**不**安装（返回 false）。
     */
    fun installApk(context: Context, file: File): Boolean {
        if (!verifyApkSignature(context, file)) {
            runCatching { file.delete() }
            Toast.makeText(
                context,
                R.string.update_signature_mismatch,
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 解析版本号基础段：剥离 `v` 前缀与 prerelease 后缀（如 v0.3.0-dev.16 → [0,3,0]）。 */
    private fun parseVersion(v: String): List<Int> =
        v.removePrefix("v").substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }

    /** 语义化比较：release 是否比当前版本新。
     *  标签与当前版本都可能带 prerelease 后缀（如 v0.3.0-dev.21 / 0.3.0-dev.21），
     *  比较时取 `X.Y.Z` 基础段。
     *  ① 完整版本（含后缀）完全相同 → 绝不视为更新；
     *  ② 基础版本相等时，**同基础「测试版 → 正式版」视为更新**（如当前 0.3.0-dev.21 → 正式版 v0.3.0，
     *     正式版通道也能检出）；[allowEqual]（测试版通道）额外允许同基础的不同测试构建视为更新。 */
    fun isNewer(releaseTag: String, currentVersion: String, allowEqual: Boolean = false): Boolean {
        if (releaseTag.removePrefix("v") == currentVersion) return false
        val a = parseVersion(releaseTag)
        val b = parseVersion(currentVersion)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // 基础版本相等：
        if ('-' !in releaseTag && '-' in currentVersion) return true // 测试版 → 正式版（同基础）
        return allowEqual
    }
}

/** 取 release JSON 中第一个 APK 资产的下载地址（无则 null）。 */
internal fun apkUrlOf(json: JSONObject): String? {
    val assets = json.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        if (a.optString("name").endsWith(".apk")) return a.optString("browser_download_url")
    }
    return null
}

/**
 * 从 `releases` 列表 JSON 里挑出**最新的一条带 APK 的发布**（测试版通道用）。
 *
 * 必须显式按 `published_at` 排序，**不能相信接口返回的顺序**：2026-09-25 实测 GitHub 的
 * releases 列表会把最新的一条排到中间——`v0.8.0-dev.100` 的 `id` 与 `created_at` 都是最新，
 * 却排在 `dev.94` 之后（新建的探针 release 同样没排到首位，且带随机参数绕过缓存后顺序不变，
 * 说明是 GitHub 侧的顺序本身不可靠）。原来"取第一条带 APK 的"因此会把旧包当成最新，
 * 测试版通道会一直提示已经过期好几个版本的 dev 包。
 *
 * `published_at` 是 ISO-8601（`2026-09-25T11:10:05Z`），同一格式下字典序即时间序；
 * 缺失该字段的条目排在最后。空列表或坏 JSON 返回 null（调用方按"没有更新"处理）。
 */
internal fun pickLatestReleaseWithApk(json: String): JSONObject? = runCatching {
    val arr = JSONArray(json)
    (0 until arr.length())
        .mapNotNull { i -> arr.optJSONObject(i) }
        .filter { apkUrlOf(it) != null }
        .sortedByDescending { it.optString("published_at") }
        .firstOrNull()
}.getOrNull()
