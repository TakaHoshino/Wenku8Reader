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
import com.hoshino.wenku8reader.ui.shelf.ShelfPickerResult
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 书架弹窗（MIUIX 版）——与 Material 版（`ui/shelf/ShelfPicker.kt`）功能相同、代码完全独立。
 *
 * 三处共用：收藏、编辑所属书架、取消收藏。**复选框是多选的**：一本书可以同时属于多个书架；
 * 取消收藏时取消勾选即从对应书架移除，全部取消勾选则完全取消收藏（确认文案随之变化）。
 * 「Wenku8书架」也是这里的一个复选框（[siteShelfName] 非 null 时追加），
 * 站方不再有独立的收藏按钮；它的勾选变化由调用方转成真实的网络请求。
 * 只在多书架开关打开时才会被调用。
 */
@Composable
fun MiuixShelfPicker(
    title: String,
    shelves: List<String>,
    initial: Set<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (ShelfPickerResult) -> Unit,
    message: String? = null,
    emptyConfirmLabel: String? = null,
    siteShelfName: String? = null,
    siteShelfInitial: Boolean = false,
) {
    var selected by remember(initial) { mutableStateOf(initial) }
    var siteSelected by remember(siteShelfInitial) { mutableStateOf(siteShelfInitial) }

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
                    val checked = name in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - name else selected + name
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            state = if (checked) ToggleableState.On else ToggleableState.Off,
                            onClick = {
                                selected = if (checked) selected - name else selected + name
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(text = name, style = MiuixTheme.textStyles.body1)
                    }
                }
                // 站方书架：与本地书架同一套复选框，只是它的勾选会落到站点（网络请求）
                siteShelfName?.let { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { siteSelected = !siteSelected }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            state = if (siteSelected) ToggleableState.On else ToggleableState.Off,
                            onClick = { siteSelected = !siteSelected },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(text = name, style = MiuixTheme.textStyles.body1)
                    }
                }
            }
            MiuixDialogButtons(
                confirmText = if (selected.isEmpty() && !siteSelected && emptyConfirmLabel != null) {
                    emptyConfirmLabel
                } else {
                    confirmLabel
                },
                dismissText = stringResource(R.string.action_cancel),
                confirmEnabled = selected.isNotEmpty() || siteSelected || emptyConfirmLabel != null,
                onConfirm = { onConfirm(ShelfPickerResult(selected, siteSelected)) },
                onDismiss = onDismiss,
            )
        }
    }
}
