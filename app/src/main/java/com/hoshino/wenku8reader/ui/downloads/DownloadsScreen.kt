package com.hoshino.wenku8reader.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.JobStatus
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.TonalCard
import com.hoshino.wenku8reader.ui.components.expressiveLargeTopAppBarColors

/**
 * 下载管理页（子页）。参考 SukiSU-Ultra：折叠大顶栏（返回）+ surfaceBright 任务卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    vm: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()

    ExpressiveScaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = expressiveLargeTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        if (jobs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.downloads_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                items(jobs.values.sortedByDescending { it.bookId },
                    key = { it.bookId }) { j ->
                    TonalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
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
                                    j.format.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (j.status == JobStatus.RUNNING) {
                                LinearProgressIndicator(
                                    progress = { j.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            AnimatedContent(
                                targetState = j.status,
                                transitionSpec = {
                                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                                },
                                label = "jobStatus",
                            ) { st ->
                                when (st) {
                                    JobStatus.RUNNING -> Text(stringResource(R.string.downloads_running),
                                        style = MaterialTheme.typography.bodySmall)
                                    JobStatus.PENDING -> Text(stringResource(R.string.detail_queued),
                                        style = MaterialTheme.typography.bodySmall)
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
