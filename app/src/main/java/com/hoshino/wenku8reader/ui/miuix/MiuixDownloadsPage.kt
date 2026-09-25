package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.JobStatus
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.downloads.DownloadsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 下载管理页——与 Material 版（`ui/downloads/DownloadsScreen.kt`）完全独立：
 * 大标题顶栏 + 每个任务一张 miuix 卡片（书名/格式/进度/状态），进行中的任务用 miuix 进度条。
 * 逻辑复用 [DownloadsViewModel]（任务状态机在数据层，与 UI 无关）。
 */
@Composable
fun MiuixDownloadsPage(
    onBack: () -> Unit,
    vm: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()

    MiuixSubPage(title = stringResource(R.string.downloads_title), onBack = onBack) { inner ->
        if (jobs.isEmpty()) {
            MiuixEmptyState(
                title = stringResource(R.string.downloads_empty),
                icon = Icons.Filled.Download,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )
            return@MiuixSubPage
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp + LocalFloatingBarInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(jobs.values.sortedByDescending { it.bookId }, key = { it.bookId }) { job ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(14.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                ) {
                    Column {
                        Text(
                            text = job.bookName,
                            style = MiuixTheme.textStyles.title3,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (job.format == "txt") {
                                "${job.format.uppercase()} · ${job.encoding.uppercase()}"
                            } else {
                                job.format.uppercase()
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        if (job.status == JobStatus.RUNNING) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = job.progress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                        AnimatedContent(
                            targetState = job.status,
                            transitionSpec = {
                                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                            },
                            label = "miuixJobStatus",
                        ) { status ->
                            Text(
                                text = when (status) {
                                    JobStatus.RUNNING -> stringResource(R.string.downloads_running)
                                    JobStatus.PENDING -> stringResource(R.string.detail_queued)
                                    JobStatus.DONE -> stringResource(
                                        R.string.downloads_done,
                                        job.filePath ?: "-",
                                    )
                                    JobStatus.FAILED -> stringResource(
                                        R.string.downloads_failed,
                                        job.error ?: "-",
                                    )
                                    JobStatus.CANCELLED -> stringResource(R.string.downloads_cancelled)
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = when (status) {
                                    JobStatus.DONE -> MiuixTheme.colorScheme.primary
                                    JobStatus.FAILED, JobStatus.CANCELLED -> MiuixTheme.colorScheme.error
                                    else -> MiuixTheme.colorScheme.onSurfaceSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
