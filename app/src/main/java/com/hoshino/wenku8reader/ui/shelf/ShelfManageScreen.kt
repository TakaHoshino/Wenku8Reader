package com.hoshino.wenku8reader.ui.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.SegmentedColumn
import com.hoshino.wenku8reader.ui.components.SegmentedListItem
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior

/**
 * 「管理书架」页（Material 3 Expressive 版）。
 *
 * 与 MIUIX 版（`ui/miuix/MiuixShelfManagePage.kt`）**完全独立**，只共用同一个
 * [ShelfManageViewModel]。默认书架那一行不给删除/重命名入口——它不可删、不可改名
 * 是数据层的硬约定（见 `ShelfOps` 的说明）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfManageScreen(
    onBack: () -> Unit,
    vm: ShelfManageViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = rememberExpressiveScrollBehavior()

    // 校验失败（重名/超长）走 Snackbar：弹窗里已经就地标红，这里是"确实提交了但被拒"的兜底
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it.asString(context)) }
    }

    var editor by remember { mutableStateOf<ShelfEditorState?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfRow?>(null) }
    // 校验只认**自建**书架（默认书架不是"已存在的名字"，而是永远占用的内置名）
    val customShelves = remember(ui.rows) { ui.rows.map { it.name }.filter { it != DEFAULT_SHELF } }

    ExpressiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveLargeTopAppBar(
                title = stringResource(R.string.shelf_manage_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { editor = ShelfEditorState.New }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.shelf_new),
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(R.string.shelf_manage_title),
                    items = ui.rows.map { row ->
                        {
                            SegmentedListItem(
                                headlineContent = { Text(row.name) },
                                supportingContent = {
                                    Text(stringResource(R.string.shelf_book_count, row.bookCount))
                                },
                                trailingContent = if (row.deletable) {
                                    {
                                        // 必须自己包一层 Row：ListItem 的 trailingContent 是**单子项**插槽，
                                        // 直接并排放两个 IconButton 会让它们叠在同一位置（表现为图标重叠）。
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                editor = ShelfEditorState.Rename(row.name)
                                            }) {
                                                Icon(
                                                    Icons.Filled.Edit,
                                                    contentDescription = stringResource(R.string.shelf_rename),
                                                )
                                            }
                                            IconButton(onClick = { pendingDelete = row }) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = stringResource(R.string.shelf_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editor?.let { current ->
        val renaming = current.from
        ShelfNameDialog(
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.shelf_delete_confirm_title)) },
            text = { Text(stringResource(R.string.shelf_delete_confirm_message, row.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.delete(row.name)
                    },
                ) { Text(stringResource(R.string.shelf_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * 新建/重命名书架的输入弹窗。
 *
 * 校验直接调用数据层的 `validateShelfName`（同一份规则），只在**这里**就地标红——
 * 不重复实现一套 UI 侧规则，也不会出现"UI 放行、数据层拒绝"的错位。
 */
@Composable
internal fun ShelfNameDialog(
    title: String,
    initialText: String,
    existing: List<String>,
    renaming: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    val error = if (text.isBlank()) null else validateShelfName(text, existing, renaming)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    // 允许多输几个字符再报"太长"，否则用户会以为输入框卡住了
                    onValueChange = { if (it.length <= SHELF_NAME_MAX_LENGTH + 10) text = it },
                    label = { Text(stringResource(R.string.shelf_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { err -> { Text(shelfNameErrorText(err)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && error == null,
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 校验错误文案（与 ViewModel 侧走的是同一批字符串）。 */
@Composable
internal fun shelfNameErrorText(error: ShelfNameError): String = when (error) {
    ShelfNameError.EMPTY -> stringResource(R.string.shelf_name_error_empty)
    ShelfNameError.TOO_LONG -> stringResource(R.string.shelf_name_error_too_long, SHELF_NAME_MAX_LENGTH)
    ShelfNameError.DUPLICATE -> stringResource(R.string.shelf_name_error_duplicate)
}
