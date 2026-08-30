package com.hoshino.wenku8reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.data.local.isDarkTheme
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.fontFamilyFor
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    vm: ReaderViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val rs by vm.readerSettingsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var immersive by rememberSaveable { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var autoTurn by rememberSaveable { mutableStateOf(false) }
    var suppressImmersiveUntil by remember { mutableStateOf(0L) }

    val pageMode = !rs.scrollMode
    val chapter = ui.currentChapter
    val loadError = ui.error
    val idx = ui.flatChapters.indexOfFirst { it.cid == ui.currentCid }
    val prev = if (idx > 0) ui.flatChapters[idx - 1] else null
    val next = if (idx in 0 until ui.flatChapters.lastIndex) ui.flatChapters[idx + 1] else null

    // 阅读时长埋点：有正文时在前台累计，退出阅读器时冲刷（见下方 ReadingTimeTracker）
    if (chapter != null) {
        ReadingTimeTracker(
            bookId = vm.bookId,
            bookName = ui.title,
            store = vm.readingStats,
        )
    }

    // 阅读器配色按主题模式分离：浅色/深色各自独立的背景色与字体色
    val isDarkTheme = rs.isDarkTheme(isSystemInDarkTheme())
    val textColor = if (isDarkTheme) Color(rs.readerTextColorDark) else Color(rs.readerTextColorLight)
    val paperColor = if (isDarkTheme) Color(rs.readerBackgroundDark) else Color(rs.readerBackgroundLight)

    // ---- system bars (immersive) ----
    val activity = remember(context) { context.findActivity() }
    val insetsController = remember(activity) {
        activity?.let { WindowInsetsControllerCompat(it.window, it.window.decorView) }
    }
    LaunchedEffect(immersive) {
        if (immersive) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Scaffold(
        containerColor = if (rs.backgroundMode == "image") Color.Transparent else paperColor,
        topBar = {
            AnimatedVisibility(
                visible = !immersive,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                ReaderTopBar(title = ui.title, onBack = onBack)
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !immersive,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                ReaderBottomBar(
                    prevEnabled = prev != null,
                    nextEnabled = next != null,
                    autoTurn = autoTurn,
                    onToggleAutoTurn = { autoTurn = !autoTurn },
                    onOpenToc = { showToc = true },
                    onSettings = { showSettings = true },
                    onPrev = { prev?.let { vm.loadChapter(it.cid) } },
                    onNext = { next?.let { vm.loadChapter(it.cid) } },
                )
            }
        },
    ) { _ ->
        BoxWithConstraints(
            Modifier.fillMaxSize(),
        ) {
            val bgImagePath = rs.backgroundImagePath
            if (rs.backgroundMode == "image" && bgImagePath != null) {
                AsyncImage(
                    model = File(bgImagePath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            val contentPadding = if (rs.autoPadding) {
                // 固定边距，与是否沉浸无关，保证分页与阅读位置稳定。
                PaddingValues(top = 64.dp, bottom = 80.dp, start = 16.dp, end = 16.dp)
            } else {
                // 手动边距也不得小于顶栏/底栏与状态指示器占位，避免遮住文字。
                PaddingValues(
                    top = maxOf(64.dp, rs.topPadding.dp),
                    bottom = maxOf(80.dp, rs.bottomPadding.dp),
                    start = rs.leftPadding.dp,
                    end = rs.rightPadding.dp,
                )
            }
            val contentWidthPx = with(density) {
                (maxWidth - contentPadding.calculateLeftPadding(layoutDirection) -
                    contentPadding.calculateRightPadding(layoutDirection)).roundToPx()
            }
            val contentHeightPx = with(density) {
                (maxHeight - contentPadding.calculateTopPadding() -
                    contentPadding.calculateBottomPadding()).roundToPx()
            }

            // ---- async pagination ----
            var pagedChapters by remember(chapter) { mutableStateOf<List<ReaderPage>>(emptyList()) }
            LaunchedEffect(
                chapter,
                rs.fontSize,
                rs.lineSpacing,
                contentWidthPx,
                contentHeightPx,
                pageMode,
            ) {
                if (pageMode && chapter != null) {
                    pagedChapters = withContext(Dispatchers.Default) {
                        paginateChapter(
                            density = density,
                            chapter = chapter,
                            maxWidthPx = contentWidthPx,
                            maxHeightPx = contentHeightPx,
                            fontSizeSp = rs.fontSize,
                            lineSpacing = rs.lineSpacing,
                        )
                    }
                } else {
                    pagedChapters = emptyList()
                }
            }
            val pagerState = rememberPagerState { pagedChapters.size }
            LaunchedEffect(chapter) {
                suppressImmersiveUntil = System.currentTimeMillis() + 500
                scrollState.scrollTo(0)
                pagerState.scrollToPage(0)
            }
            LaunchedEffect(pageMode) { if (pageMode) pagerState.scrollToPage(0) }

            // ---- volume key turn ----
            val turnPage: (Int) -> Unit = { delta ->
                scope.launch {
                    if (pageMode && chapter != null) {
                        if (pagedChapters.isEmpty()) return@launch
                        val target = pagerState.currentPage + delta
                        when {
                            target in 0 until pagedChapters.size -> pagerState.animateScrollToPage(target)
                            delta > 0 -> if (rs.autoNextChapter) next?.let { vm.loadChapter(it.cid) }
                            delta < 0 -> prev?.let { vm.loadChapter(it.cid) }
                        }
                    } else if (!pageMode) {
                        val step = (scrollState.viewportSize * 0.85f * delta).toInt()
                        scrollState.animateScrollTo(
                            (scrollState.value + step).coerceIn(0, scrollState.maxValue)
                        )
                    }
                }
            }
            val currentTurnPage by rememberUpdatedState(turnPage)
            LaunchedEffect(rs.volumeKeyTurnPage) {
                VolumeKeyTurn.enabled = rs.volumeKeyTurnPage
                // 音量键约定：上键 = 上一页，下键 = 下一页（与常见阅读器一致）
                VolumeKeyTurn.onVolumeUp = { currentTurnPage(-1) }
                VolumeKeyTurn.onVolumeDown = { currentTurnPage(1) }
            }
            DisposableEffect(Unit) {
                onDispose {
                    VolumeKeyTurn.enabled = false
                    VolumeKeyTurn.onVolumeUp = null
                    VolumeKeyTurn.onVolumeDown = null
                }
            }

            // ---- auto page turn ----
            LaunchedEffect(autoTurn, rs.autoTurnInterval, pagedChapters.size, next?.cid) {
                if (!autoTurn || !pageMode || pagedChapters.isEmpty()) return@LaunchedEffect
                while (true) {
                    delay(rs.autoTurnInterval * 1000L)
                    if (pagerState.currentPage < pagedChapters.lastIndex) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    } else if (rs.autoNextChapter) {
                        val nxt = next
                        if (nxt != null) {
                            vm.loadChapter(nxt.cid)
                        } else {
                            autoTurn = false
                            break
                        }
                    } else {
                        autoTurn = false
                        break
                    }
                }
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .drop(1)
                    .collect {
                        if (System.currentTimeMillis() > suppressImmersiveUntil) immersive = true
                    }
            }

            // ---- content + tap zones ----
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(pageMode, rs.pageTurnDirection, rs.clickTurnPage) {
                        detectTapGestures { offset ->
                            val w = size.width
                            val rtl = !rs.pageTurnDirection
                            when {
                                offset.x < w / 3f ->
                                    if (pageMode && rs.clickTurnPage) currentTurnPage(if (rtl) 1 else -1)
                                    else immersive = true
                                offset.x > w * 2f / 3f ->
                                    if (pageMode && rs.clickTurnPage) currentTurnPage(if (rtl) -1 else 1)
                                    else immersive = true
                                else -> immersive = !immersive
                            }
                        }
                    },
            ) {
                when {
                    ui.chapterLoading && chapter == null ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    loadError != null && chapter == null ->
                        Column(
                            Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                loadError.asString(context),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                if (ui.flatChapters.isEmpty()) vm.openReader()
                                else {
                                    val cid = ui.currentCid ?: ui.flatChapters.firstOrNull()?.cid
                                    cid?.let { vm.loadChapter(it) }
                                }
                            }) { Text(stringResource(R.string.action_retry)) }
                        }

                    chapter != null -> {
                        if (pageMode) {
                            if (pagedChapters.isEmpty()) {
                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                            } else {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { index ->
                                    when (val page = pagedChapters.getOrNull(index)) {
                                        is ReaderPage.Text -> Box(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(contentPadding),
                                        ) {
                                            Text(
                                                page.text,
                                                color = textColor,
                                                fontFamily = fontFamilyFor(rs.fontFamily),
                                                fontSize = rs.fontSize.sp,
                                                fontWeight = FontWeight(rs.fontWeight),
                                                lineHeight = (rs.fontSize * rs.lineSpacing).sp,
                                            )
                                        }
                                        is ReaderPage.Image -> SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(page.url)
                                                .setHeader("Referer", "https://www.wenku8.net/")
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = stringResource(R.string.reader_illustration),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(contentPadding)
                                                // LNR 风格占位底色：加载中/失败时保持稳定视觉
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                            contentScale = ContentScale.Fit,
                                        )
                                        null -> {}
                                    }
                                }
                            }
                        } else {
                            ScrollContent(
                                chapter = chapter,
                                rs = rs,
                                textColor = textColor,
                                scrollState = scrollState,
                                paddingValues = contentPadding,
                                positionText = stringResource(
                                    R.string.reader_chapter_position,
                                    (idx + 1).coerceAtLeast(0),
                                    ui.flatChapters.size,
                                ),
                            )
                        }
                    }

                    ui.tocLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    else -> Text(
                        stringResource(R.string.reader_toc_empty),
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 章节读完检测：翻到最后一页（页模式）或滚动到底（滚动模式）→ 标记"已读"
                LaunchedEffect(ui.currentCid) {
                    val finishCid = ui.currentCid ?: return@LaunchedEffect
                    snapshotFlow {
                        if (pageMode) {
                            if (pagedChapters.isEmpty()) 0
                            else (pagerState.currentPage + 1) * 100 / pagedChapters.size
                        } else {
                            if (scrollState.maxValue > 0) scrollState.value * 100 / scrollState.maxValue else 0
                        }
                    }.distinctUntilChanged().collect { percent ->
                        if (percent >= 100) vm.markChapterFinished(finishCid)
                    }
                }

                // status indicator: shown only in immersive, at the very bottom
                AnimatedVisibility(
                    visible = immersive,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    val readingPercent = if (pageMode) {
                        if (pagedChapters.isNotEmpty()) (pagerState.currentPage + 1) * 100 / pagedChapters.size else 0
                    } else {
                        if (scrollState.maxValue > 0) scrollState.value * 100 / scrollState.maxValue else 0
                    }
                    IndicatorBar(
                        title = chapter?.title ?: "",
                        progressPercent = readingPercent,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }

                // floating chapter progress: shown only when the bottom bar is visible
                AnimatedVisibility(
                    visible = !immersive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    val chapterProgress = if (pageMode) {
                        if (pagedChapters.size > 1) pagerState.currentPage.toFloat() / (pagedChapters.size - 1) else 0f
                    } else {
                        if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 3.dp,
                    ) {
                        Slider(
                            value = chapterProgress,
                            onValueChange = { progress ->
                                suppressImmersiveUntil = System.currentTimeMillis() + 800
                                scope.launch {
                                    if (pageMode && pagedChapters.isNotEmpty()) {
                                        // 侧滑翻页：按页跳转
                                        pagerState.scrollToPage(
                                            (progress * (pagedChapters.size - 1)).roundToInt()
                                                .coerceIn(0, pagedChapters.lastIndex)
                                        )
                                    } else if (scrollState.maxValue > 0) {
                                        scrollState.scrollTo((progress * scrollState.maxValue).roundToInt())
                                    }
                                }
                            },
                            steps = if (pageMode && pagedChapters.size > 1) {
                                (pagedChapters.size - 1).coerceIn(1, 60)
                            } else {
                                0
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }

    // ---- immersive auto-trigger on scroll ----
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .drop(1)
            .collect {
                if (System.currentTimeMillis() > suppressImmersiveUntil) immersive = true
            }
    }

    if (showSettings) {
        SettingsSheet(rs, vm) { showSettings = false }
    }
    if (showToc) {
        ChapterSelectionSheet(ui, vm) { showToc = false }
    }
}

// ------------------------------------------------------------------ //
// bars
// ------------------------------------------------------------------ //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back))
            }
        },
    )
}

@Composable
private fun ReaderBottomBar(
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    autoTurn: Boolean,
    onToggleAutoTurn: () -> Unit,
    onOpenToc: () -> Unit,
    onSettings: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterNavButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.reader_prev_chapter),
                prevEnabled,
                onPrev,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleAutoTurn) {
                Icon(
                    if (autoTurn) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.reader_auto_turn),
                    tint = if (autoTurn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenToc) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_toc))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.reader_settings))
            }
            Spacer(Modifier.weight(1f))
            ChapterNavButton(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.reader_next_chapter),
                nextEnabled,
                onNext,
            )
        }
    }
}

@Composable
private fun ChapterNavButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * 状态指示器（沉浸时屏幕最底部的电量/时间/章节名/进度）。
 * 电池与时钟状态只在本组件内维护，避免每 30 秒触发整个阅读器重组。
 */
@Composable
private fun IndicatorBar(
    title: String,
    progressPercent: Int,
    color: Color,
) {
    val context = LocalContext.current
    var batteryPercent by remember { mutableIntStateOf(readBattery(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryPercent = readBattery(context)
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val timeText = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val batteryIcon = when {
            batteryPercent >= 80 -> Icons.Filled.BatteryFull
            batteryPercent >= 50 -> Icons.Filled.BatteryStd
            batteryPercent >= 20 -> Icons.Filled.Battery4Bar
            else -> Icons.Filled.BatteryAlert
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_battery, batteryPercent),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
            Spacer(Modifier.width(4.dp))
            Icon(batteryIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Text(timeText, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.reader_progress_percent, progressPercent),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

// ------------------------------------------------------------------ //
// scroll content
// ------------------------------------------------------------------ //
@Composable
private fun ScrollContent(
    chapter: ChapterContent,
    rs: ReaderSettingsState,
    textColor: Color,
    scrollState: ScrollState,
    paddingValues: PaddingValues,
    positionText: String,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(paddingValues),
    ) {
        Text(
            chapter.title,
            color = textColor,
            fontFamily = fontFamilyFor(rs.fontFamily),
            fontSize = (rs.fontSize + 4).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = ((rs.fontSize + 4) * rs.lineSpacing).sp,
        )
        if (chapter.text.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                chapter.text,
                color = textColor,
                fontFamily = fontFamilyFor(rs.fontFamily),
                fontSize = rs.fontSize.sp,
                fontWeight = FontWeight(rs.fontWeight),
                lineHeight = (rs.fontSize * rs.lineSpacing).sp,
            )
        }
        if (chapter.images.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            chapter.images.forEach { url ->
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .setHeader("Referer", "https://www.wenku8.net/")
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.reader_illustration),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        // LNR 风格占位底色：加载中/失败时保持稳定视觉
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentScale = ContentScale.FillWidth,
                    loading = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    },
                )
            }
        }
        if (chapter.text.isBlank() && chapter.images.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.reader_no_content),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            positionText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(32.dp))
    }
}

// ------------------------------------------------------------------ //
// chapter selection (volume-grouped)
// ------------------------------------------------------------------ //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSelectionSheet(
    ui: ReaderUiState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    var expandedVolumes by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(ui.currentCid, ui.volumes) {
        ui.volumes.firstOrNull { v -> v.chapters.any { it.cid == ui.currentCid } }?.name?.let {
            if (it !in expandedVolumes) expandedVolumes = expandedVolumes + it
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.reader_toc),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            ui.volumes.forEach { volume ->
                val expanded = volume.name in expandedVolumes
                item(key = "vol_${volume.name}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedVolumes = if (expanded) expandedVolumes - volume.name
                                else expandedVolumes + volume.name
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            volume.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (expanded) {
                    items(volume.chapters, key = { it.cid }) { ch ->
                        val current = ch.cid == ui.currentCid
                        ListItem(
                            headlineContent = {
                                Text(
                                    ch.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                    color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                )
                            },
                            supportingContent = { if (current) Text(stringResource(R.string.reader_current)) },
                            modifier = Modifier.clickable {
                                vm.loadChapter(ch.cid)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ //
// settings sheet (tabs)
// ------------------------------------------------------------------ //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    rs: ReaderSettingsState,
    vm: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.reader_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SecondaryTabRow(selectedTabIndex = tab) {
                listOf(
                    R.string.reader_settings_appearance,
                    R.string.reader_settings_action,
                    R.string.reader_settings_margin,
                ).forEachIndexed { index, res ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(stringResource(res)) },
                    )
                }
            }
            when (tab) {
                0 -> AppearanceSettings(rs, vm)
                1 -> ActionSettings(rs, vm)
                else -> MarginSettings(rs, vm)
            }
        }
    }
}

@Composable
private fun AppearanceSettings(rs: ReaderSettingsState, vm: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            SettingSliderRow(
                stringResource(R.string.reader_font_size),
                rs.fontSize.toFloat(),
                14f..28f,
            ) { vm.setFontSize(it.roundToInt()) }
        }
        item {
            SettingSliderRow(
                stringResource(R.string.settings_font_weight),
                rs.fontWeight.toFloat(),
                300f..700f,
            ) { vm.setFontWeight(it.roundToInt()) }
        }
        item {
            SettingSliderRow(
                stringResource(R.string.settings_line_spacing),
                rs.lineSpacing,
                1.2f..2.5f,
            ) { vm.setLineSpacing((it * 10f).roundToInt() / 10f) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "default" to R.string.settings_font_default,
                    "sans" to R.string.settings_font_sans,
                    "serif" to R.string.settings_font_serif,
                    "mono" to R.string.settings_font_mono,
                ).forEach { (key, res) ->
                    FilterChip(
                        selected = rs.fontFamily == key,
                        onClick = { vm.setFontFamily(key) },
                        label = { Text(stringResource(res)) },
                    )
                }
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_turn_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !rs.scrollMode,
                        onClick = { vm.setScrollMode(false) },
                        label = { Text(stringResource(R.string.reader_mode_page)) },
                    )
                    FilterChip(
                        selected = rs.scrollMode,
                        onClick = { vm.setScrollMode(true) },
                        label = { Text(stringResource(R.string.reader_mode_scroll)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSettings(rs: ReaderSettingsState, vm: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            SettingRow(stringResource(R.string.reader_turn_direction)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rs.pageTurnDirection,
                        onClick = { vm.setPageTurnDirection(true) },
                        label = { Text(stringResource(R.string.reader_turn_left)) },
                    )
                    FilterChip(
                        selected = !rs.pageTurnDirection,
                        onClick = { vm.setPageTurnDirection(false) },
                        label = { Text(stringResource(R.string.reader_turn_right)) },
                    )
                }
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_click_turn)) {
                Switch(checked = rs.clickTurnPage, onCheckedChange = { vm.setClickTurnPage(it) })
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_volume_turn)) {
                Switch(checked = rs.volumeKeyTurnPage, onCheckedChange = { vm.setVolumeKeyTurnPage(it) })
            }
        }
        item {
            SettingRow(stringResource(R.string.reader_auto_next)) {
                Switch(checked = rs.autoNextChapter, onCheckedChange = { vm.setAutoNextChapter(it) })
            }
        }
        item {
            var intervalText by remember { mutableStateOf(rs.autoTurnInterval.toString()) }
            LaunchedEffect(rs.autoTurnInterval) { intervalText = rs.autoTurnInterval.toString() }
            SettingRow(stringResource(R.string.reader_auto_interval)) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        intervalText = digits
                        digits.toIntOrNull()?.takeIf { it in 1..999 }
                            ?.let { vm.setAutoTurnInterval(it) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

@Composable
private fun MarginSettings(rs: ReaderSettingsState, vm: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        item {
            SettingRow(stringResource(R.string.reader_auto_margin)) {
                Switch(checked = rs.autoPadding, onCheckedChange = { vm.setAutoPadding(it) })
            }
        }
        if (!rs.autoPadding) {
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_top), rs.topPadding.toFloat(), 0f..128f) { vm.setTopPadding(it.roundToInt()) }
            }
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_bottom), rs.bottomPadding.toFloat(), 0f..128f) { vm.setBottomPadding(it.roundToInt()) }
            }
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_left), rs.leftPadding.toFloat(), 0f..128f) { vm.setLeftPadding(it.roundToInt()) }
            }
            item {
                SettingSliderRow(stringResource(R.string.reader_margin_right), rs.rightPadding.toFloat(), 0f..128f) { vm.setRightPadding(it.roundToInt()) }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

// ------------------------------------------------------------------ //
// helpers
// ------------------------------------------------------------------ //
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun readBattery(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return 100
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else 100
}

// ------------------------------------------------------------------ //
// 阅读时长埋点
// ------------------------------------------------------------------ //

/**
 * 阅读时长统计（阅读热力图数据源）：
 * - 仅应用在前台（Lifecycle RESUMED）且阅读器可见时累计；
 * - 每 60 秒把整段时长写入 [ReadingStatsStore] 并持久化；
 * - 退出阅读器（组合销毁）时把不足 60 秒的余量也冲刷进去，保证不丢。
 * 聚合口径：每日/每书分钟数 = ceil(秒数 / 60)，不足 1 分钟按 1 分钟计（由 UI 层聚合）。
 */
@Composable
private fun ReadingTimeTracker(
    bookId: Int,
    bookName: String,
    store: com.hoshino.wenku8reader.data.local.ReadingStatsStore,
) {
    if (bookId <= 0) return
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bookId) {
        while (true) {
            delay(1000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                pendingSeconds++
                if (pendingSeconds >= 60) {
                    store.addSeconds(bookId, bookName, pendingSeconds)
                    store.persist()
                    pendingSeconds = 0
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 冲刷余量，避免退出阅读器时丢失最后不足 60 秒的阅读
            if (pendingSeconds > 0) {
                store.addSeconds(bookId, bookName, pendingSeconds)
                store.persist()
            }
        }
    }
}
