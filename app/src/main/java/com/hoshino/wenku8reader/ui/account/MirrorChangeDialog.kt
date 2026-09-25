package com.hoshino.wenku8reader.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.miuix.MiuixDialogButtons
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import com.hoshino.wenku8reader.ui.theme.isMiuixStyle
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 切换主镜像前的确认框：只在**当前登录的是用户账户**时出现。
 *
 * 为什么需要它：Cookie 不跨域，换镜像等于必须重新登录；如果默默切过去，用户的账户会话就
 * 被静默丢弃了。这里明确告知"会退出当前账户"，确认后再按"退出账户 → 内置账号在新域登录"
 * 处理（见 `SettingsViewModel.confirmPrimaryMirrorChange`），阅读与探索无感续用。
 *
 * 按仓库既有口径**按风格分派**（同 `ReaderImagePreview` 的对话框）：两套 UI 共用这一个入口，
 * 由 `isMiuixStyle()` 决定用 miuix `WindowDialog` 还是 M3 `AlertDialog`。
 */
@Composable
internal fun MirrorChangeDialog(vm: SettingsViewModel) {
    val pending by vm.pendingMirror.collectAsStateWithLifecycle()
    if (pending == null) return

    val title = stringResource(R.string.account_mirror_confirm_title)
    val message = stringResource(R.string.account_mirror_confirm_message)
    val confirmText = stringResource(R.string.action_confirm)
    val cancelText = stringResource(R.string.action_cancel)

    if (isMiuixStyle()) {
        WindowDialog(
            show = true,
            title = title,
            summary = message,
            onDismissRequest = vm::dismissPrimaryMirrorChange,
        ) {
            MiuixDialogButtons(
                confirmText = confirmText,
                dismissText = cancelText,
                onConfirm = vm::confirmPrimaryMirrorChange,
                onDismiss = vm::dismissPrimaryMirrorChange,
            )
        }
    } else {
        AlertDialog(
            onDismissRequest = vm::dismissPrimaryMirrorChange,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = vm::confirmPrimaryMirrorChange) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPrimaryMirrorChange) { Text(cancelText) }
            },
        )
    }
}
