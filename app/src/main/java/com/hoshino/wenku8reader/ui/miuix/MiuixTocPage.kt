package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.Volume
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.toc.TocViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 目录页——与 Material 版（`ui/toc/TocScreen.kt`）**完全独立**。
 *
 * 形态照 HyperOS：小标题顶栏（返回 + 全展开/全收起）+ 分卷折叠列表；
 * 已读章节灰显并带「已读」，当前章节用主题色加粗；长列表右侧挂 miuix 滚动条。
 * 折叠/已读/当前章的逻辑全部来自同一个 [TocViewModel]。
 */
@Composable
fun MiuixTocPage(
    onBack: () -> Unit,
    onOpenChapter: (bookId: Int, cid: String) -> Unit,
    vm: TocViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    MiuixSubPage(
        title = if (ui.title.isNotBlank()) ui.title else stringResource(R.string.toc_title),
        onBack = onBack,
        actions = {
            MiuixIconButton(
                icon = if (ui.collapsedVolumes.isNotEmpty()) {
                    Icons.Filled.UnfoldMore
                } else {
                    Icons.Filled.UnfoldLess
                },
                contentDescription = stringResource(R.string.toc_expand_collapse_all),
                onClick = {
                    if (ui.collapsedVolumes.isNotEmpty()) vm.expandAll() else vm.collapseAll()
                },
            )
        },
    ) { inner ->
        when {
            ui.loading -> MiuixLoading(Modifier.fillMaxSize().padding(inner))

            // 加载失败（网络/解析）与「真的没有章节」在这里区分开：
            // 以前两者都落到「暂无章节」，用户看不到错在哪。
            ui.volumes.isEmpty() -> MiuixEmptyState(
                title = ui.error?.asString(LocalContext.current)
                    ?: stringResource(R.string.toc_empty),
                error = ui.error != null,
                actionText = if (ui.error != null) stringResource(R.string.action_retry) else null,
                onAction = if (ui.error != null) ({ vm.load() }) else null,
                modifier = Modifier.fillMaxSize().padding(inner),
            )

            else -> {
                val listState = rememberLazyListState()
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(inner),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            bottom = 24.dp + LocalFloatingBarInset.current,
                        ),
                    ) {
                        itemsIndexed(
                            ui.volumes,
                            key = { index, volume -> "$index:${volume.name}" },
                        ) { _, volume ->
                            MiuixVolumeSection(
                                volume = volume,
                                finished = ui.finished,
                                currentCid = ui.currentCid,
                                collapsed = volume.name in ui.collapsedVolumes,
                                onToggle = { vm.toggleVolume(volume.name) },
                                onOpenChapter = { cid -> onOpenChapter(vm.bookId, cid) },
                            )
                        }
                    }
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixVolumeSection(
    volume: Volume,
    finished: Set<String>,
    currentCid: String?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // 卷头（点击折叠/展开）
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (collapsed) {
                    Icons.Filled.ExpandMore
                } else {
                    Icons.Filled.ExpandLess
                },
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = volume.name.ifBlank { stringResource(R.string.toc_unnamed_volume) },
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
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
                        isFinished -> MiuixTheme.colorScheme.onSurfaceSecondary
                        isCurrent -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.onSurface
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChapter(chapter.cid) }
                            .padding(start = 40.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chapter.name,
                            style = MiuixTheme.textStyles.body1,
                            color = textColor,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isFinished) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.toc_read_mark),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
