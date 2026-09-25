package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.DEFAULT_SHELF
import com.hoshino.wenku8reader.data.local.SHELF_NAME_MAX_LENGTH
import com.hoshino.wenku8reader.data.local.ShelfNameError
import com.hoshino.wenku8reader.data.local.validateShelfName
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.shelf.ShelfEditorState
import com.hoshino.wenku8reader.ui.shelf.ShelfManageViewModel
import com.hoshino.wenku8reader.ui.shelf.ShelfRow
import com.hoshino.wenku8reader.ui.shelf.shelfNameErrorText
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「管理书架」页（MIUIX / HyperOS 版）。
 *
 * 与 Material 版（`ui/shelf/ShelfManageScreen.kt`）完全独立，只共用同一个
 * [ShelfManageViewModel]：这里是 miuix 的小标题顶栏 + 分组卡片 + `WindowDialog`，
 * 不出现任何 Material 组件。
 */
@Composable
fun MiuixShelfManagePage(
    onBack: () -> Unit,
    vm: ShelfManageViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<ShelfEditorState?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfRow?>(null) }
    val customShelves = remember(ui.rows) { ui.rows.map { it.name }.filter { it != DEFAULT_SHELF } }

    MiuixSubPage(
        title = stringResource(R.string.shelf_manage_title),
        onBack = onBack,
        actions = {
            MiuixIconButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.shelf_new),
                onClick = { editor = ShelfEditorState.New },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            MiuixSection(title = stringResource(R.string.shelf_manage_title)) {
                ui.rows.forEachIndexed { index, row ->
                    if (index > 0) MiuixRowDivider()
                    MiuixRow(
                        title = row.name,
                        summary = stringResource(R.string.shelf_book_count, row.bookCount),
                        trailing = if (row.deletable) {
                            {
                                MiuixIconButton(
                                    icon = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.shelf_rename),
                                    onClick = { editor = ShelfEditorState.Rename(row.name) },
                                )
                                MiuixIconButton(
                                    icon = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.shelf_delete),
                                    onClick = { pendingDelete = row },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editor?.let { current ->
        val renaming = current.from
        MiuixShelfNameDialog(
            title = stringResource(
                if (renaming == null) R.string.shelf_new else R.string.shelf_rename,
            ),
            initialText = renaming.orEmpty(),
            existing = customShelves,
            renaming = renaming,
            onDismiss = { editor = null },
            onConfirm = { name ->
                editor = null
                if (renaming == null) vm.create(name) else vm.rename(renaming, name)
            },
        )
    }

    pendingDelete?.let { row ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.shelf_delete_confirm_title),
            summary = stringResource(R.string.shelf_delete_confirm_message, row.name),
            onDismissRequest = { pendingDelete = null },
        ) {
            MiuixDialogButtons(
                confirmText = stringResource(R.string.shelf_delete),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = {
                    pendingDelete = null
                    vm.delete(row.name)
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

/** MIUIX 版的新建/重命名弹窗（与 Material 版同规则，只换组件）。 */
@Composable
private fun MiuixShelfNameDialog(
    title: String,
    initialText: String,
    existing: List<String>,
    renaming: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    val error = if (text.isBlank()) null else validateShelfName(text, existing, renaming)

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column {
            // miuix 的 TextField 没有 label 参数（仓库现有用法也都没传），标签单独一行
            Text(
                text = stringResource(R.string.shelf_name_label),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            TextField(
                value = text,
                onValueChange = { if (it.length <= SHELF_NAME_MAX_LENGTH + 10) text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = shelfNameErrorText(error),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            MiuixDialogButtons(
                confirmText = stringResource(R.string.action_confirm),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = { if (text.isNotBlank() && error == null) onConfirm(text) },
                onDismiss = onDismiss,
            )
        }
    }
}
