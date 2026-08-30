package com.hoshino.wenku8reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** GitHub Release 信息（更新检查结果）。 */
data class ReleaseInfo(
    val tag: String,          // 如 "v0.2.0"
    val versionName: String,  // 如 "0.2.0"
    val apkDownloadUrl: String, // 原始 github 下载地址
    val releaseName: String = "",
)

/**
 * 更新检查：查询 GitHub Releases 最新版（`api.github.com`），下载 APK（直连或 gh-proxy 镜像），
 * 并用 FileProvider 拉起系统安装器。
 */
class UpdateChecker {

    private val client = OkHttpClient()

    companion object {
        private const val REPO = "TakaHoshino/Wenku8Reader"
        private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
        private const val API_LIST = "https://api.github.com/repos/$REPO/releases?per_page=20"
        private const val UA = "Wenku8Reader-Android"
        /** gh-proxy 镜像前缀（更新源选择用），后接原始下载地址。 */
        const val GH_PROXY_PREFIX = "https://gh-proxy.com/"
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
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@runCatching null
                check(resp.isSuccessful) { "检查更新失败（HTTP ${resp.code}）" }
                val text = resp.body!!.string()
                val json = if (stable) {
                    JSONObject(text)
                } else {
                    val arr = org.json.JSONArray(text)
                    var best: JSONObject? = null
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (apkUrlOf(o) != null) {
                            best = o
                            break
                        }
                    }
                    best ?: return@runCatching null
                }
                val tag = json.optString("tag_name", "")
                val apkUrl = apkUrlOf(json)
                if (tag.isBlank() || apkUrl == null) null
                else ReleaseInfo(tag, tag.removePrefix("v"), apkUrl, json.optString("name", ""))
            }
        }
    }

    /** 取 release JSON 中第一个 APK 资产的下载地址（无则 null）。 */
    private fun apkUrlOf(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) return a.optString("browser_download_url")
        }
        return null
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
            val req = Request.Builder().url(url).header("User-Agent", UA).build()
            client.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "下载失败（HTTP ${resp.code}）" }
                val total = resp.body?.contentLength() ?: -1L
                dest.parentFile?.mkdirs()
                resp.body!!.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            dest
        }
    }

    /** 用 FileProvider 拉起系统安装器（覆盖安装/更新包）。 */
    fun installApk(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
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
