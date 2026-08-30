package com.hoshino.wenku8reader.ui.toc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold

/**
 * 目录页（独立二级页面）：分卷可折叠列表。
 * - 默认所有卷展开；全卷已读的卷首次加载自动折叠；
 * - 已读章节灰色 + "已读"标记；当前章节高亮；
 * - 点击章节跳转阅读器（从该章节开始，重读已读章节会触发状态重置）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocScreen(
    onBack: () -> Unit,
    onOpenChapter: (bookId: Int, cid: String) -> Unit,
    vm: TocViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (ui.title.isNotBlank()) ui.title else stringResource(R.string.toc_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { if (ui.collapsedVolumes.isNotEmpty()) vm.expandAll() else vm.collapseAll() }) {
                        Icon(
                            if (ui.collapsedVolumes.isNotEmpty()) Icons.Filled.UnfoldMore
                            else Icons.Filled.UnfoldLess,
                            contentDescription = stringResource(R.string.toc_expand_collapse_all),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            ui.volumes.isEmpty() -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.toc_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                itemsIndexed(ui.volumes, key = { index, volume -> "$index:${volume.name}" }) { _, volume ->
                    VolumeSection(
                        volume = volume,
                        finished = ui.finished,
                        currentCid = ui.currentCid,
                        collapsed = volume.name in ui.collapsedVolumes,
                        onToggle = { vm.toggleVolume(volume.name) },
                        onOpenChapter = { cid -> onOpenChapter(vm.bookId, cid) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun VolumeSection(
    volume: Volume,
    finished: Set<String>,
    currentCid: String?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // 卷头（可折叠）
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                volume.name.ifBlank { stringResource(R.string.toc_unnamed_volume) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                volume.chapters.forEach { chapter ->
                    val isFinished = chapter.cid in finished
                    val isCurrent = chapter.cid == currentCid
                    val textColor = when {
                        isFinished -> MaterialTheme.colorScheme.outline   // 已读：灰色
                        isCurrent -> MaterialTheme.colorScheme.primary    // 当前章节：强调色
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChapter(chapter.cid) }
                            .padding(start = 40.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chapter.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            fontWeight = if (isCurrent) FontWeight.W600 else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isFinished) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.toc_read_mark),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
