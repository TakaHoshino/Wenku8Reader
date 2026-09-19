package com.hoshino.wenku8reader.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.JobStatus
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ActiveProgressBar
import com.hoshino.wenku8reader.ui.components.ExpressiveEmptyState
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.TonalCard
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior

/**
 * 下载管理页（子页）：Expressive Flexible 大顶栏（返回）+ 卡片化任务列表。
 *
 * 进行中的任务用 M3 Expressive 的波浪进度条（[ActiveProgressBar]）——
 * 波浪动效只用于"正在进行"的短时任务，完成/失败状态仍为纯文本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    vm: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val scrollBehavior = rememberExpressiveScrollBehavior()

    ExpressiveScaffold(
        topBar = {
            ExpressiveLargeTopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
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
        if (jobs.isEmpty()) {
            ExpressiveEmptyState(
                title = stringResource(R.string.downloads_empty),
                icon = Icons.Filled.Download,
                shape = MaterialShapes.Clover4Leaf,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                items(
                    jobs.values.sortedByDescending { it.bookId },
                    key = { it.bookId },
                ) { j ->
                    TonalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    j.bookName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    // txt 额外显示编码（如 TXT · UTF8）；epub 无编码概念，只显示格式
                                    if (j.format == "txt") {
                                        "${j.format.uppercase()} · ${j.encoding.uppercase()}"
                                    } else {
                                        j.format.uppercase()
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (j.status == JobStatus.RUNNING) {
                                ActiveProgressBar(
                                    progress = { j.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            AnimatedContent(
                                targetState = j.status,
                                transitionSpec = {
                                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                                },
                                label = "jobStatus",
                            ) { st ->
                                when (st) {
                                    JobStatus.RUNNING -> Text(
                                        stringResource(R.string.downloads_running),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    JobStatus.PENDING -> Text(
                                        stringResource(R.string.detail_queued),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    JobStatus.DONE -> Text(
                                        stringResource(R.string.downloads_done, j.filePath ?: "-"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    JobStatus.FAILED -> Text(
                                        stringResource(R.string.downloads_failed, j.error ?: "-"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    JobStatus.CANCELLED -> Text(
                                        stringResource(R.string.downloads_cancelled),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
