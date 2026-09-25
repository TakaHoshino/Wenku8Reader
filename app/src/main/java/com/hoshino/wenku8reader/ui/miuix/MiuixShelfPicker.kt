package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 书架弹窗（MIUIX 版）——与 Material 版（`ui/shelf/ShelfPicker.kt`）功能相同、代码完全独立。
 *
 * 三处共用：收藏、移动到书架、取消收藏（后者 [interactive] 传 false，勾选框只用来指出
 * 当前所在书架，动作交给确认按钮）。选中态一律用 miuix 的 `Checkbox` 表达。
 */
@Composable
fun MiuixShelfPicker(
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

    WindowDialog(
        show = true,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            state = if (name == selected) ToggleableState.On else ToggleableState.Off,
                            // interactive=false 时传 null：勾选框保持可见但不再响应点击
                            onClick = if (interactive) {
                                { selected = name }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(text = name, style = MiuixTheme.textStyles.body1)
                    }
                }
            }
            MiuixDialogButtons(
                confirmText = confirmLabel,
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = { if (!interactive || selected != null) onConfirm(selected) },
                onDismiss = onDismiss,
            )
        }
    }
}
