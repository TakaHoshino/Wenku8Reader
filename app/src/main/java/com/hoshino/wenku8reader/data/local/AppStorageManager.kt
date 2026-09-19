package com.hoshino.wenku8reader.data.local

import android.content.Context
import android.webkit.WebView
import coil.imageLoader
import com.hoshino.wenku8reader.data.Wenku8Client
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用存储占用明细（字节）。
 *
 * 与系统「应用信息 → 存储」的口径对齐：
 * - [cacheDirTotal] 对应系统的「缓存」= `context.cacheDir` 全部内容
 *   （Coil 图片缓存、更新安装包、WebView/Chromium 缓存、临时文件、其他）；
 * - [dataDirTotal] 对应系统的「用户数据」= 数据目录，其中网页离线缓存
 *   （`filesDir/html_cache`）、`shared_prefs`、WebView 数据目录（`app_webview`）、
 *   `code_cache` 是其中占比最大的几块。
 *
 * 旧实现只统计 `filesDir/html_cache`（磁盘网页缓存）并把它叫作"缓存"，
 * 于是系统显示 33MB/36MB 而应用内只显示 2.8MB——差的正是本类型里其余各项。
 */
data class StorageBreakdown(
    /** 网页离线缓存：filesDir/html_cache */
    val htmlCache: Long = 0L,
    /** Coil 磁盘图片缓存：cacheDir/image_cache（含应用内存图片缓存，仅清理时涉及） */
    val imageCache: Long = 0L,
    /** 下载好的更新安装包：cacheDir/updates */
    val updatePackage: Long = 0L,
    /** WebView/Chromium 磁盘缓存：cacheDir 下的 webview 相关目录 */
    val webViewCache: Long = 0L,
    /** 应用自建临时文件（如选择阅读背景图时的中转文件） */
    val tempFiles: Long = 0L,
    /** cacheDir 中其余内容（Cronet、系统组件等写入） */
    val otherCache: Long = 0L,
    /** SharedPreferences（阅读进度、已读标记、设置、书架排序等） */
    val preferences: Long = 0L,
    /** 数据目录中的其余文件（阅读背景图、WebView 数据、数据库等） */
    val appData: Long = 0L,
    /** 数据目录中的 code_cache（JIT/ART 生成，系统管理，不可由应用清理） */
    val codeCache: Long = 0L,
) {
    /** 系统「缓存」口径 */
    val cacheDirTotal: Long get() = imageCache + updatePackage + webViewCache + tempFiles + otherCache

    /** 系统「用户数据」口径 */
    val dataDirTotal: Long get() = htmlCache + preferences + appData + codeCache

    /** 应用可清理的合计（缓存目录 + 网页离线缓存 + SharedPreferences） */
    val clearableTotal: Long get() = cacheDirTotal + htmlCache + preferences
}

/**
 * 存储占用统计与清理的单一来源。
 *
 * 为什么需要它：缓存分散在四个互不相干的位置（网页离线缓存、Coil 图片缓存、
 * 更新安装包、WebView 缓存），此前只有网页离线缓存被统计/清理，
 * 其余空间用户既看不到也清不掉。
 *
 * 所有方法均为挂起函数并在 [Dispatchers.IO] 上遍历目录——统计要 walk 整个
 * cacheDir/dataDir，绝不能放在主线程。
 */
class AppStorageManager(
    private val context: Context,
    private val client: Wenku8Client,
) {

    private val cacheDir: File get() = context.cacheDir
    private val dataDir: File get() = File(context.applicationInfo.dataDir)
    private val imageCacheDir: File get() = File(cacheDir, COIL_IMAGE_DIR)
    private val updateDir: File get() = File(cacheDir, UPDATE_DIR)
    private val sharedPrefsDir: File get() = File(dataDir, "shared_prefs")
    private val codeCacheDir: File get() = File(dataDir, "code_cache")

    /** 统计当前占用明细。 */
    suspend fun stats(): StorageBreakdown = withContext(Dispatchers.IO) {
        val htmlCache = client.htmlCacheSize()
        val imageCache = imageCacheDir.sizeOf()
        val updatePackage = updateDir.sizeOf()
        val webViewCache = webViewCacheDirs().sumOf { it.sizeOf() }
        val tempFiles = tempFiles().sumOf { it.length() }
        val cacheTotal = cacheDir.sizeOf()
        val preferences = sharedPrefsDir.sizeOf()
        val codeCache = codeCacheDir.sizeOf()
        val dataTotal = dataDir.sizeOf()

        StorageBreakdown(
            htmlCache = htmlCache,
            imageCache = imageCache,
            updatePackage = updatePackage,
            webViewCache = webViewCache,
            tempFiles = tempFiles,
            // 其余 = 缓存目录总量减去已归类的部分（不会为负：归类项都取自缓存目录内部）
            otherCache = (cacheTotal - imageCache - updatePackage - webViewCache - tempFiles)
                .coerceAtLeast(0L),
            preferences = preferences,
            appData = (dataTotal - htmlCache - preferences - codeCache).coerceAtLeast(0L),
            codeCache = codeCache,
        )
    }

    /**
     * 清理图片缓存：Coil 磁盘缓存 + 内存缓存。
     *
     * 用 Coil 的 `diskCache.clear()` 而不是直接删目录：Coil 内部持有 journal，
     * 绕过它删文件会让缓存索引与实际文件不一致（后续读写报错或留下孤儿文件）。
     * 目录删除仅作为兜底（例如 ImageLoader 尚未初始化、或存在历史遗留目录）。
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearImageCache(): Long = withContext(Dispatchers.IO) {
        val before = imageCacheDir.sizeOf()
        runCatching {
            val loader = context.imageLoader
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        deleteChildren(imageCacheDir)
        (before - imageCacheDir.sizeOf()).coerceAtLeast(0L)
    }

    /** 清理已下载的更新安装包（`cacheDir/updates`）。 */
    suspend fun clearUpdatePackages(): Long = withContext(Dispatchers.IO) {
        val before = updateDir.sizeOf()
        deleteChildren(updateDir)
        (before - updateDir.sizeOf()).coerceAtLeast(0L)
    }

    /**
     * 清理 WebView/Chromium 磁盘缓存。
     *
     * 只清 HTTP 缓存，**不动 Cookie/localStorage**：CF 的 `cf_clearance` 与登录态
     * 依赖 WebView 的 Cookie，清掉会触发重跑 CF 挑战（用户可感知的额外等待）。
     */
    suspend fun clearWebViewCache(): Long = withContext(Dispatchers.IO) {
        val before = webViewCacheDirs().sumOf { it.sizeOf() }
        // WebView.clearCache 必须在主线程调用；失败也不影响下面的目录清理
        withContext(Dispatchers.Main) {
            runCatching { WebView(context.applicationContext).clearCache(true) }
        }
        webViewCacheDirs().forEach { deleteChildren(it) }
        (before - webViewCacheDirs().sumOf { it.sizeOf() }).coerceAtLeast(0L)
    }

    /** 清理应用自建临时文件。 */
    suspend fun clearTempFiles(): Long = withContext(Dispatchers.IO) {
        val files = tempFiles()
        val before = files.sumOf { it.length() }
        files.forEach { runCatching { it.delete() } }
        (before - tempFiles().sumOf { it.length() }).coerceAtLeast(0L)
    }

    /**
     * 启动时回收**陈旧产物**：已下载很久的更新安装包与残留临时文件。
     *
     * 为什么按时间而不是直接清空：更新包可能正在被系统安装器读取（用户点了"安装"后
     * 本应用就被切到后台），一删就可能装不上；临时文件也可能正被复制流程占用。
     * 只删除超过 [STALE_ARTIFACT_MS] 的，既能自动回收（更新 APK 单份约 18MB，
     * 是系统「缓存」里占比最大的一项），又不会误伤正在进行的操作。
     */
    suspend fun pruneStaleArtifacts(): Long = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - STALE_ARTIFACT_MS
        val stale = buildList {
            updateDir.listFiles()
                ?.filter { it.isFile && it.lastModified() < cutoff }
                ?.let(::addAll)
            tempFiles().filterTo(this) { it.lastModified() < cutoff }
        }
        val before = stale.sumOf { it.length() }
        stale.forEach { runCatching { it.delete() } }
        before
    }

    /**
     * 清空缓存目录（图片缓存 / 更新安装包 / WebView 缓存 / 临时文件 / 其他）。
     *
     * [preserveUpdatePackage] = true 时保留更新安装包：更新正在下载时，文件句柄仍指向它，
     * 删掉会让下载"成功"却写出一个已删除的文件（校验必然失败）。
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearCacheDir(preserveUpdatePackage: Boolean): Long = withContext(Dispatchers.IO) {
        val before = cacheDir.sizeOf()
        runCatching {
            val loader = context.imageLoader
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        cacheDir.listFiles()?.forEach { child ->
            if (preserveUpdatePackage && child == updateDir) return@forEach
            runCatching { child.deleteRecursively() }
        }
        (before - cacheDir.sizeOf()).coerceAtLeast(0L)
    }

    // ------------------------------------------------------------------ //
    // helpers
    // ------------------------------------------------------------------ //

    /** WebView 的缓存目录名随系统版本变化（WebView / webview / app_webview 等），按前缀匹配。 */
    private fun webViewCacheDirs(): List<File> =
        cacheDir.listFiles()
            ?.filter { it.isDirectory && it.name.contains("webview", ignoreCase = true) }
            .orEmpty()

    private fun tempFiles(): List<File> =
        cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.startsWith(TEMP_PREFIX) || it.name.endsWith(".tmp")) }
            .orEmpty()

    private fun deleteChildren(dir: File) {
        dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
    }

    private companion object {
        /** Coil 磁盘缓存的默认目录名（Coil 2.x 未自定义时使用）。 */
        const val COIL_IMAGE_DIR = "image_cache"

        /** 更新安装包目录（见 UpdateCenter.download）。 */
        const val UPDATE_DIR = "updates"

        /** 应用自建临时文件前缀（见 CustomizationScreen.copyToInternal）。 */
        const val TEMP_PREFIX = "reader_background_"

        /** 陈旧产物的判定时长（1 小时）：足够覆盖"点了安装但安装器仍在后台读取"的窗口。 */
        const val STALE_ARTIFACT_MS = 60L * 60 * 1000
    }
}

/** 目录总大小（含子目录）。 */
internal fun File.sizeOf(): Long =
    if (!exists()) 0L else walkTopDown().filter { it.isFile }.sumOf { it.length() }
