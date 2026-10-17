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
 * 书架弹窗的结果：本地归属 + 站方书架。
 *
 * [siteShelf] 只在弹窗**确实列出了站方那一项**时才有意义（没列出时恒为 false），
 * 调用方据此判断"要不要发站方书架的网络请求"，不要拿它去表示"本地没有归属"。
 */
data class ShelfPickerResult(
    val shelves: Set<String>,
    val siteShelf: Boolean = false,
)

/**
 * 书架弹窗（Material 版）——**收藏 / 编辑所属书架 / 取消收藏三处共用同一个样式**。
 *
 * **复选框是多选的**：一本书可以同时属于多个书架，勾上几个就属于几个
 * （所以早先那套"移动到书架"的单选语义已被「所属书架」取代）。
 *
 * **「Wenku8书架」也是这里的一个复选框**（[siteShelfName] 非 null 时追加在本地书架后面）：
 * 收藏/取消收藏只有一个入口（详情页的星标），站方书架不再有自己的按钮。
 * 站方那一项的勾选变化会由调用方转成真实的网络请求（加入 / 移出站点书架）。
 *
 * 只在多书架开关打开时才会被调用；开关关闭时收藏/取消收藏走原来的单书架路径，
 * 连这个弹窗都不会出现。
 *
 * @param initial 预勾选的书架；收藏时传默认书架（保持"不挑就直接进默认"的旧习惯），
 *   编辑/取消收藏时传当前所在的那些书架。
 * @param confirmLabel 确认按钮文案（通常是"确定"）。
 * @param emptyConfirmLabel 一个都没勾时改用的确认文案（取消收藏传"取消收藏"：
 *   全部取消勾选即完全取消收藏）。为 null 时"一个都没勾"视为非法输入、确认按钮置灰——
 *   书架里的书必须至少属于一个书架。
 * @param siteShelfName 站方书架在弹窗里的名字；null 表示不显示这一项（未登录 / 开关关闭）。
 * @param siteShelfInitial 站方书架是否预勾选（当前是否已在网站书架里）。
 * @param onConfirm 参数为最终勾选的书架集合。
 */
@Composable
fun ShelfPickerDialog(
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
                    val checked = name in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - name else selected + name
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selected = if (on) selected + name else selected - name
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                // 站方书架：与本地书架同一套复选框，只是它的勾选会落到站点（网络请求）
                siteShelfName?.let { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { siteSelected = !siteSelected }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = siteSelected,
                            onCheckedChange = { siteSelected = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ShelfPickerResult(selected, siteSelected)) },
                enabled = selected.isNotEmpty() || siteSelected || emptyConfirmLabel != null,
            ) {
                Text(
                    if (selected.isEmpty() && !siteSelected && emptyConfirmLabel != null) emptyConfirmLabel
                    else confirmLabel,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
