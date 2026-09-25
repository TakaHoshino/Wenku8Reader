package com.hoshino.wenku8reader.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R

/**
 * 书架弹窗（Material 版）——**收藏 / 移动到书架 / 取消收藏三处共用同一个样式**。
 *
 * 用复选框而不是"右侧一个对勾图标"来表达选中态：既让列表读起来像"选择"，
 * 也让取消收藏时能一眼看清"这本书当前在哪个书架"。
 *
 * @param initial 预勾选的书架；收藏时传默认书架（保持"不挑就直接进默认"的旧习惯），
 *   移动/取消收藏时传当前所在书架。
 * @param interactive 是否可勾选。取消收藏时为 false——勾选框只用来**指出位置**，
 *   真正的动作由确认按钮承担（避免"取消收藏"和"改归属"两件事混在一个手势里）。
 * @param confirmLabel 确认按钮文案（收藏是"确定"，取消收藏是"取消收藏"）。
 * @param onConfirm 参数为选中的书架；未选中时为 null（此时确认按钮不可点）。
 */
@Composable
fun ShelfPickerDialog(
    title: String,
    shelves: List<String>,
    initial: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    message: String? = null,
    interactive: Boolean = true,
) {
    var selected by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // 书架多时列表要能滚动，否则底部条目点不到
                shelves.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (interactive) {
                                    Modifier.clickable { selected = name }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = name == selected,
                            // interactive=false 时传 null：勾选框仍然清晰可见，但不再响应点击
                            // （若用 enabled=false 会把它灰掉，看起来像"功能不可用"而不是"这是当前值"）
                            onCheckedChange = if (interactive) {
                                { selected = name }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected) },
                enabled = !interactive || selected != null,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
