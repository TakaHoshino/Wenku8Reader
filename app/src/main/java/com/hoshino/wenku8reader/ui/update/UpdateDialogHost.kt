package com.hoshino.wenku8reader.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
                    LinearProgressIndicator(
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
            if (state.downloading) {
                // 下载中禁用
            } else if (state.downloadError != null) {
                TextButton(onClick = onUpdate) { Text(stringResource(R.string.action_retry)) }
            } else {
                TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_now)) }
            }
        },
        dismissButton = {
            if (state.downloading) {
                // 下载中不可取消（避免半包）
            } else if (state.downloadError != null) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.action_close)) }
            } else {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
            }
        },
    )
}
