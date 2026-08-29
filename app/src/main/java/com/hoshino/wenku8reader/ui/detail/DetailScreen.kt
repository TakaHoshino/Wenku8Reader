package com.hoshino.wenku8reader.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.JobStatus
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.rememberCoverRequest
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.StatusTag
import com.hoshino.wenku8reader.ui.components.TonalCard
import com.hoshino.wenku8reader.ui.components.expressiveLargeTopAppBarColors

/**
 * 书籍详情页（子页）。参考 SukiSU-Ultra：折叠大顶栏 + 封面信息卡片 +
 * 离线下载卡片 + 简介卡片，全部 surfaceBright 圆角卡片。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    vm: DetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val jobs by vm.downloadJobs.collectAsStateWithLifecycle()
    val job = jobs[vm.bookId]

    val info = ui.book
    val busy = job != null &&
        (job.status == JobStatus.RUNNING || job.status == JobStatus.PENDING)

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.favoriteMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg.asString(context))
        }
    }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        info?.title ?: stringResource(R.string.detail_title_default),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.toggleLocalFavorite() },
                        enabled = info != null,
                    ) {
                        Icon(
                            if (ui.inLocalLibrary) Icons.Filled.Bookmark
                            else Icons.Filled.BookmarkBorder,
                            contentDescription = stringResource(R.string.detail_favorite),
                        )
                    }
                },
                colors = expressiveLargeTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        when {
            ui.loading && info == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            info == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        ui.error?.asString(LocalContext.current) ?: stringResource(R.string.error_book_info),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.load() }) { Text(stringResource(R.string.action_retry)) }
                }
            }

            else -> {
                val entered = remember {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = entered,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 16 },
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Spacer(Modifier.height(2.dp))

                        // 封面 + 基本信息
                        TonalCard(shape = RoundedCornerShape(16.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                            ) {
                                AsyncImage(
                                    model = rememberCoverRequest(info.coverUrl, 120.dp, 170.dp, crossfade = true),
                                    contentDescription = stringResource(R.string.detail_cover),
                                    modifier = Modifier
                                        .size(120.dp, 170.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(info.title, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(6.dp))
                                    InfoLine(stringResource(R.string.detail_author, info.author.ifEmpty { "-" }))
                                    InfoLine(stringResource(R.string.detail_category, info.category.ifEmpty { "-" }))
                                    InfoLine(stringResource(R.string.detail_status, info.status.ifEmpty { "-" }))
                                    InfoLine(stringResource(R.string.detail_word_count, info.wordCount.ifEmpty { "-" }))
                                }
                            }
                            if (info.tags.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    info.tags.forEach { tag ->
                                        StatusTag(
                                            label = tag,
                                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }

                        // 阅读按钮
                        Button(
                            onClick = { onRead(vm.bookId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(
                                if (vm.hasProgress()) R.string.detail_continue_reading
                                else R.string.detail_start_reading
                            ))
                        }

                        // 离线下载
                        TonalCard(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.detail_offline_download),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { vm.download("txt") },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                    ) { Text(stringResource(R.string.detail_format_txt)) }
                                    OutlinedButton(
                                        onClick = { vm.download("epub") },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                    ) { Text(stringResource(R.string.detail_format_epub)) }
                                }

                                AnimatedVisibility(
                                    visible = job != null,
                                    enter = fadeIn(tween(250)) + expandVertically(),
                                    exit = fadeOut(tween(200)) + shrinkVertically(),
                                ) {
                                    val j = job
                                    Spacer(Modifier.height(12.dp))
                                    when (j?.status) {
                                        JobStatus.RUNNING -> {
                                            LinearProgressIndicator(
                                                progress = { j?.progress ?: 0f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                stringResource(R.string.detail_downloading, ((j?.progress ?: 0f) * 100).toInt()),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        JobStatus.PENDING -> Text(stringResource(R.string.detail_queued),
                                            style = MaterialTheme.typography.bodySmall)
                                        JobStatus.DONE -> AssistChip(
                                            onClick = {},
                                            label = { Text(stringResource(R.string.detail_saved, j?.filePath ?: "完成")) },
                                        )
                                        JobStatus.FAILED -> Text(stringResource(R.string.detail_failed, j?.error ?: "-"),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall)
                                        JobStatus.CANCELLED -> Text(stringResource(R.string.detail_cancelled),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall)
                                        null -> {}
                                    }
                                }
                            }
                        }

                        // 内容简介
                        TonalCard(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.detail_description),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    info.description.ifEmpty { stringResource(R.string.detail_no_description) },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
