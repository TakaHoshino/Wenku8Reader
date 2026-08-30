package com.hoshino.wenku8reader.data

import android.content.Context
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.AppPreferences
import com.hoshino.wenku8reader.data.local.ReaderSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val checking: Boolean = false,
    val latest: ReleaseInfo? = null,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
)

/**
 * 更新中心（应用级单例，经 AppContainer 注入）：
 * - check()：查询 GitHub Releases 最新版，比当前版本新且未被「跳过」时进入 [latest] 状态（弹窗）；
 *   已是最新 → 无提示（手动检查时由调用方 Toast 提示）；失败 → 记录错误由调用方提示。
 * - download()：按设置里的更新源（github 直连 / gh-proxy 镜像）下载 APK → FileProvider 安装。
 * - later()：稍后提醒（关闭弹窗）；skip()：记住该版本并关闭。
 */
class UpdateCenter(
    private val context: Context,
    private val checker: UpdateChecker,
    private val preferences: AppPreferences,
    private val settings: ReaderSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 手动检查的瞬时提示（已是最新 / 检查失败），由 UI 层 Toast 展示。 */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** 当前应用版本名（packageManager），用于新旧比较。 */
    val currentVersionName: String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0.0")

    /**
     * 查询最新版。[manual] = true 时（用户主动点「检查更新」），把
     * 「已是最新 / 无可用更新 / 检查失败」通过 [notices] 提示；启动检查不打扰。
     * 通道（正式版/测试版）与更新源读取自 [ReaderSettings]。
     */
    fun check(manual: Boolean = false) {
        if (_state.value.checking) return
        val stable = settings.flow.value.updateChannel != "beta"
        _state.update { it.copy(checking = true) }
        scope.launch {
            val result = checker.fetchLatest(stable)
            _state.update { it.copy(checking = false) }
            result.fold(
                onSuccess = { release ->
                    when {
                        release == null ->
                            if (manual) _notices.tryEmit(context.getString(R.string.update_none))
                        !checker.isNewer(release.versionName, currentVersionName, allowEqual = !stable) ->
                            if (manual) _notices.tryEmit(context.getString(R.string.update_up_to_date))
                        isSkipped(release) -> {
                            // 用户跳过过该基础版本线（自动与手动均不再弹窗）
                            if (manual) _notices.tryEmit(
                                context.getString(R.string.update_skipped, release.versionName)
                            )
                        }
                        else -> _state.update { it.copy(latest = release) }
                    }
                },
                onFailure = { e ->
                    if (manual) {
                        _notices.tryEmit(
                            context.getString(R.string.update_check_failed, e.message ?: "")
                        )
                    }
                },
            )
        }
    }

    /** 是否被跳过：按基础版本（X.Y.Z，忽略 -dev.N 后缀）比较，同基础版本线全部跳过。 */
    private fun isSkipped(release: ReleaseInfo): Boolean {
        val skipped = preferences.skippedUpdateVersion ?: return false
        return release.tag.removePrefix("v").substringBefore("-") == skipped
    }

    fun download() {
        val release = _state.value.latest ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, downloadProgress = 0f, downloadError = null) }
        scope.launch {
            val url = checker.apkUrl(release, settings.flow.value.updateSource)
            val dest = File(context.cacheDir, "updates/app-release.apk")
            val result = checker.downloadApk(url, dest) { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
            result.fold(
                onSuccess = { file ->
                    _state.update {
                        it.copy(downloading = false, latest = null, downloadProgress = 0f)
                    }
                    checker.installApk(context, file)
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            downloading = false,
                            downloadError = e.message ?: "下载失败",
                        )
                    }
                },
            )
        }
    }

    /** 稍后提醒：关闭弹窗。 */
    fun later() {
        _state.update { it.copy(latest = null, downloadError = null) }
    }

    /** 跳过该基础版本线：记住基础版本（如 0.3.0），同基础的测试构建不再提示（自动+手动），
     *  更高基础版本发布后才恢复提示。 */
    fun skip() {
        val tag = _state.value.latest?.tag
        if (tag != null) {
            preferences.skippedUpdateVersion = tag.removePrefix("v").substringBefore("-")
        }
        _state.update { it.copy(latest = null, downloadError = null) }
    }
}
