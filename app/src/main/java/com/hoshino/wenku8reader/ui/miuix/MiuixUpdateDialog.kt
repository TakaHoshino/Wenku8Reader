package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ReleaseInfo
import com.hoshino.wenku8reader.data.UpdateUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * MIUIX 版更新对话框——与 Material 版（`ui/update/UpdateDialogHost.kt` 的 M3 `AlertDialog`）
 * **完全独立**，用 miuix `WindowDialog` + miuix 进度条 + miuix 按钮，形态对齐 HyperOS 的系统弹窗。
 *
 * 行为与 Material 版保持一致：
 * - 下载中禁用动作按钮（按钮保持可见，避免弹窗内容跳动）；
 * - 下载失败时主按钮变「重试」、次按钮变「关闭」；
 * - 「跳过该版本」只在正常状态下出现（下载中/失败时无意义）。
 */
@Composable
fun MiuixUpdateDialog(
    state: UpdateUiState,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val release: ReleaseInfo = state.latest ?: return
    WindowDialog(
        show = true,
        title = stringResource(R.string.update_title, release.versionName),
        summary = stringResource(
            R.string.update_message,
            release.versionName,
            currentVersionName,
        ),
        onDismissRequest = onLater,
    ) {
        Column(Modifier.padding(top = 12.dp)) {
            if (state.downloading) {
                LinearProgressIndicator(
                    progress = state.downloadProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.update_downloading,
                        (state.downloadProgress * 100).toInt(),
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(12.dp))
            }

            state.downloadError?.let { error ->
                Text(
                    text = stringResource(R.string.update_download_failed, error),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onUpdate,
                enabled = !state.downloading,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.downloadError != null) R.string.action_retry
                        else R.string.update_now,
                    ),
                )
            }
            if (!state.downloading && state.downloadError == null) {
                Spacer(Modifier.height(2.dp))
                TextButton(
                    text = stringResource(R.string.update_skip),
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(2.dp))
            TextButton(
                text = stringResource(
                    if (state.downloadError != null) R.string.action_close
                    else R.string.update_later,
                ),
                onClick = onLater,
                enabled = !state.downloading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
