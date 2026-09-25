package com.hoshino.wenku8reader.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ReleaseInfo
import com.hoshino.wenku8reader.data.UpdateUiState
import com.hoshino.wenku8reader.ui.components.ActiveProgressBar

/**
 * 更新对话框宿主：监听 [UpdateUiState]，发现新版本（[UpdateUiState.latest] 非空）时弹出
 * 「立即更新 / 稍后提醒 / 跳过该版本」，下载中显示进度，失败显示错误与重试。
 */
@Composable
fun UpdateDialogHost(
    state: UpdateUiState,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val release: ReleaseInfo = state.latest ?: return
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_title, release.versionName)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.update_message,
                        release.versionName,
                        currentVersionName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.downloading) {
                    Spacer(Modifier.height(12.dp))
                    // 下载属于"正在进行"的短时任务：用 Expressive 波浪进度条
                    ActiveProgressBar(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.update_downloading, (state.downloadProgress * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.downloadError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_download_failed, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            // 下载中不允许再次点击：用 enabled = !downloading 反向表达，
            // 而不是「空分支 + 注释」——按钮保持可见，避免弹窗内容跳动。
            TextButton(
                onClick = onUpdate,
                enabled = !state.downloading,
            ) {
                Text(
                    stringResource(
                        if (state.downloadError != null) R.string.action_retry
                        else R.string.update_now
                    )
                )
            }
        },
        dismissButton = {
            // 下载中同样只禁用，不隐藏（避免半包提示消失造成的误解）
            TextButton(
                onClick = onLater,
                enabled = !state.downloading,
            ) {
                Text(
                    stringResource(
                        if (state.downloadError != null) R.string.action_close
                        else R.string.update_later
                    )
                )
            }
            // 「跳过该版本」只在正常状态下提供（下载中/下载失败时无意义）
            if (!state.downloading && state.downloadError == null) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
            }
        },
    )
}
