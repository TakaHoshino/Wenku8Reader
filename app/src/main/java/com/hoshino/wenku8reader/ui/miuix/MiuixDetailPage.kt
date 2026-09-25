package com.hoshino.wenku8reader.ui.miuix

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.BookInfo
import com.hoshino.wenku8reader.data.JobStatus
import com.hoshino.wenku8reader.data.local.DEFAULT_SHELF
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.CoverImage
import com.hoshino.wenku8reader.ui.detail.DetailViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 书籍详情页——与 Material 版（`ui/detail/DetailScreen.kt`）**完全独立**。
 *
 * 形态照 HyperOS：小标题顶栏（返回 + 收藏）+ 信息卡（封面 / 作者 / 分类 / 状态 / 字数 / 标签）
 * + 主操作按钮 + 离线下载卡 + 简介卡。全部用 miuix 组件与 `MiuixTheme` 排版；
 * 收藏反馈在这里用 Toast 提示（与 MIUIX 存储页的做法一致），不再挂 M3 的 SnackbarHost。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiuixDetailPage(
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenToc: (Int) -> Unit,
    vm: DetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val jobs by vm.downloadJobs.collectAsStateWithLifecycle()
    val job = jobs[vm.bookId]
    val info = ui.book
    val busy = job != null &&
        (job.status == JobStatus.RUNNING || job.status == JobStatus.PENDING)

    val context = LocalContext.current
    // 多书架：收藏前选择目标书架；取消收藏前也要确认（并指出它当前在哪个书架）。
    // pickingForRemoval 区分这两件事——两者共用同一个弹窗（需求要求复用同一样式）。
    var showShelfPicker by remember { mutableStateOf(false) }
    var pickingForRemoval by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.favoriteMessages.collect { message ->
            Toast.makeText(context, message.asString(context), Toast.LENGTH_SHORT).show()
        }
    }
    MiuixSubPage(
        title = info?.title ?: stringResource(R.string.detail_title_default),
        onBack = onBack,
        actions = {
            // 收藏：五角星（已收藏为实心 + 主题色，未收藏为空心星），
            // 与「收藏」文案一致；这里不复用 MiuixIconButton 是因为需要按状态改 tint
            top.yukonga.miuix.kmp.basic.IconButton(
                // 多书架开启且尚未收藏 → 先选书架；其余情况（含关闭开关）走原来的收藏/移出
                onClick = {
                    if (ui.multiShelfEnabled) {
                        pickingForRemoval = ui.inLocalLibrary
                        showShelfPicker = true
                    } else {
                        vm.toggleLocalFavorite()
                    }
                },
            ) {
                Icon(
                    imageVector = if (ui.inLocalLibrary) {
                        Icons.Filled.Star
                    } else {
                        Icons.Filled.StarBorder
                    },
                    contentDescription = stringResource(R.string.detail_favorite),
                    tint = if (ui.inLocalLibrary) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                )
            }
        },
    ) { inner ->
        val book = info
        when {
            ui.loading && book == null -> MiuixLoading(
                Modifier.fillMaxSize().padding(inner),
            )

            book == null -> MiuixEmptyState(
                title = ui.error?.asString(context) ?: stringResource(R.string.error_book_info),
                error = true,
                icon = Icons.Filled.ErrorOutline,
                actionText = stringResource(R.string.action_retry),
                onAction = { vm.load() },
                modifier = Modifier.fillMaxSize().padding(inner),
            )

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                MiuixDetailInfoCard(
                    info = book,
                    onOpenAuthor = onOpenAuthor,
                    onOpenTag = onOpenTag,
                )

                // 主操作：开始 / 继续阅读
                Button(
                    onClick = { onRead(vm.bookId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (ui.hasProgress) R.string.detail_continue_reading
                            else R.string.detail_start_reading,
                        ),
                    )
                }

                // 次级操作：目录页
                TextButton(
                    text = stringResource(R.string.detail_toc),
                    onClick = { onOpenToc(vm.bookId) },
                    modifier = Modifier.fillMaxWidth(),
                )

                MiuixDownloadCard(
                    busy = busy,
                    status = job?.status,
                    progress = job?.progress ?: 0f,
                    filePath = job?.filePath,
                    error = job?.error,
                    onDownload = vm::download,
                )

                MiuixSection(title = stringResource(R.string.detail_description)) {
                    Text(
                        text = book.description.ifEmpty {
                            stringResource(R.string.detail_no_description)
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
            }
        }
    }

    if (showShelfPicker) {
        MiuixShelfPicker(
            title = stringResource(
                if (pickingForRemoval) R.string.shelf_picker_remove_title
                else R.string.shelf_picker_add_title,
            ),
            shelves = ui.shelves,
            // 收藏默认预勾选默认书架（不挑就直接进默认，沿用旧习惯）
            initial = if (pickingForRemoval) ui.currentShelf else DEFAULT_SHELF,
            confirmLabel = stringResource(
                if (pickingForRemoval) R.string.shelf_picker_remove_title
                else R.string.action_confirm,
            ),
            message = if (pickingForRemoval) stringResource(R.string.shelf_picker_remove_message) else null,
            // 取消收藏是"确认"而不是"选择"：勾选框只指出当前位置
            interactive = !pickingForRemoval,
            onDismiss = { showShelfPicker = false },
            onConfirm = { shelf ->
                showShelfPicker = false
                if (pickingForRemoval || shelf == null) vm.toggleLocalFavorite() else vm.addToShelf(shelf)
            },
        )
    }
}

/** 信息卡：封面 + 书名 / 作者 / 分类 / 状态 / 字数 + 标签流式排布。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiuixDetailInfoCard(
    info: BookInfo,
    onOpenAuthor: (String) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    MiuixSection {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            CoverImage(
                url = info.coverUrl,
                width = 110.dp,
                height = 156.dp,
                contentDescription = stringResource(R.string.detail_cover),
                cornerRadius = 12.dp,
                crossfade = true,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val hasAuthor = info.author.isNotBlank()
                Text(
                    text = stringResource(
                        R.string.detail_author,
                        info.author.ifEmpty { "-" },
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = if (hasAuthor) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .let { if (hasAuthor) it.clickable { onOpenAuthor(info.author) } else it }
                        .padding(vertical = 2.dp),
                )
                DetailInfoLine(stringResource(R.string.detail_category, info.category.ifEmpty { "-" }))
                DetailInfoLine(stringResource(R.string.detail_status, info.status.ifEmpty { "-" }))
                DetailInfoLine(stringResource(R.string.detail_word_count, info.wordCount.ifEmpty { "-" }))
            }
        }
        if (info.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                info.tags.forEach { tag ->
                    Text(
                        text = tag,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { onOpenTag(tag) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailInfoLine(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 离线下载卡：TXT / EPUB 两个 miuix 按钮 + 任务状态/进度。 */
@Composable
private fun MiuixDownloadCard(
    busy: Boolean,
    status: JobStatus?,
    progress: Float,
    filePath: String?,
    error: String?,
    onDownload: (String) -> Unit,
) {
    MiuixSection(title = stringResource(R.string.detail_offline_download)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onDownload("txt") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.detail_format_txt)) }
                Button(
                    onClick = { onDownload("epub") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.detail_format_epub)) }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                cornerRadius = 12.dp,
                insideMargin = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 10.dp,
                ),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                ),
            ) {
                when (status) {
                    JobStatus.RUNNING -> Column {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        DetailInfoLine(
                            stringResource(
                                R.string.detail_downloading,
                                (progress * 100).toInt(),
                            ),
                        )
                    }

                    JobStatus.PENDING -> DetailInfoLine(stringResource(R.string.detail_queued))

                    JobStatus.DONE -> Text(
                        text = stringResource(R.string.detail_saved, filePath ?: ""),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )

                    JobStatus.FAILED -> Text(
                        text = stringResource(R.string.detail_failed, error ?: "-"),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )

                    JobStatus.CANCELLED -> Text(
                        text = stringResource(R.string.detail_cancelled),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )

                    null -> DetailInfoLine(stringResource(R.string.detail_download_hint))
                }
            }
        }
    }
}
