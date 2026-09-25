package com.hoshino.wenku8reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.data.local.isDarkTheme
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.fontFamilyFor
import com.hoshino.wenku8reader.ui.miuix.MiuixChapterSheet
import com.hoshino.wenku8reader.ui.miuix.MiuixReaderSettingsSheet
import com.hoshino.wenku8reader.ui.theme.isMiuixStyle
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ------------------------------------------------------------------ //
// 常量：集中定义便于统一调整，避免同一口径的数字散落多处而漂移
// ------------------------------------------------------------------ //

/** 正文上下预留边距：必须不小于顶栏/底栏与状态指示器占位，否则会遮住文字。 */
private val CONTENT_TOP_INSET = 64.dp
private val CONTENT_BOTTOM_INSET = 80.dp

/** 阅读进度整数口径的"完成"阈值（100%）。 */
private const val PROGRESS_COMPLETE_PERCENT = 100

/** 插图预览目标：URL + 本章内的序号（序号用于生成保存时的文件名）。 */
private data class ReaderPreviewTarget(val url: String, val index: Int)

/** 按 [ReaderPreviewTarget] 生成保存用的文件名（不含扩展名，扩展名由预览按图片类型补）。 */
private fun ReaderPreviewTarget.fileName(bookTitle: String): String {
    val safeTitle = bookTitle.ifBlank { "wenku8" }
    return "${safeTitle}_插图${index + 1}"
}

/**
 * 阅读进度整数百分比（0..100）：翻页模式取「当前页/总页数」，滚动模式取滚动比例。
 *
 * 抽成普通函数（而非 @Composable）是为了让组合期与 [snapshotFlow] 内部共用同一口径，
 * 避免同一段三分支逻辑在多处重复、口径各自漂移。
 */
private fun readingPercentOf(
    pageMode: Boolean,
    currentPage: Int,
    pageCount: Int,
    scrollFraction: Float,
): Int = if (pageMode) {
    if (pageCount > 0) (currentPage + 1) * 100 / pageCount else 0
} else {
    (scrollFraction * 100).roundToInt()
}

/**
 * 阅读进度比例（0f..1f）：翻页模式按页索引跨度，滚动模式按滚动比例；供底部进度条 Slider 使用。
 * 与 [readingPercentOf] 同源，保证指示器数值与可拖动进度条始终一致。
 */
private fun readingFractionOf(
    pageMode: Boolean,
    currentPage: Int,
    pageCount: Int,
    scrollFraction: Float,
): Float = if (pageMode) {
    if (pageCount > 1) currentPage.toFloat() / (pageCount - 1) else 0f
} else {
    scrollFraction
}

/**
 * 滚动模式的进度比例（0f..1f）。
 *
 * 滚动模式现在是按段落懒加载的 [LazyListState]，没有 `ScrollState.value/maxValue` 那对像素口径，
 * 于是用「已滚过的段落数 + 当前段落内的偏移比例」估算——对进度指示与"切模式不丢进度"足够，
 * 且与旧的像素比例在单调性上一致。
 *
 * 注意：这里读的是 `firstVisibleItemScrollOffset`（每帧都会变），与旧实现读 `ScrollState.value`
 * 的重组开销同级，不算回归。
 */
private fun LazyListState.scrollFraction(): Float {
    val total = layoutInfo.totalItemsCount
    if (total <= 1) return 0f
    val current = layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstVisibleItemIndex }
    val itemSize = current?.size ?: 0
    val within = if (itemSize > 0) {
        firstVisibleItemScrollOffset.toFloat() / itemSize
    } else {
        0f
    }
    return ((firstVisibleItemIndex + within) / (total - 1)).coerceIn(0f, 1f)
}

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
    // 滚动模式按段落懒加载（章节正文不再是一个巨型 Text）
    val listState = rememberLazyListState()

    var immersive by rememberSaveable { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var autoTurn by rememberSaveable { mutableStateOf(false) }
    var suppressImmersiveUntil by remember { mutableStateOf(0L) }
    // 长按插图 → 全屏预览（记录 URL 与序号，序号用于生成保存时的文件名）
    var previewTarget by remember { mutableStateOf<ReaderPreviewTarget?>(null) }

    val pageMode = !rs.scrollMode
    val chapter = ui.currentChapter
    val paragraphs = remember(chapter) {
        chapter?.text?.let(::splitReaderParagraphs).orEmpty()
    }
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

    // 返回键：预览打开时先关预览，而不是直接退出阅读器
    BackHandler(enabled = previewTarget != null) { previewTarget = null }

    Box(Modifier.fillMaxSize()) {
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
                PaddingValues(
                    top = CONTENT_TOP_INSET,
                    bottom = CONTENT_BOTTOM_INSET,
                    start = 16.dp,
                    end = 16.dp,
                )
            } else {
                // 手动边距也不得小于顶栏/底栏与状态指示器占位，避免遮住文字。
                PaddingValues(
                    top = maxOf(CONTENT_TOP_INSET, rs.topPadding.dp),
                    bottom = maxOf(CONTENT_BOTTOM_INSET, rs.bottomPadding.dp),
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
                listState.scrollToItem(0)
                pagerState.scrollToPage(0)
            }

            // 模式切换不丢进度：滚动模式下持续记录进度比例（不参与组合读取，故不会引起重组），
            // 切回翻页模式时先记下比例，等分页结果就绪后再反推目标页落位
            //（分页是异步的，切换瞬间 pagedChapters 仍为空，无法立即跳页）。
            var scrollRatioForRestore by remember { mutableStateOf(0f) }
            LaunchedEffect(pageMode) {
                if (pageMode) return@LaunchedEffect
                snapshotFlow { listState.scrollFraction() }
                    .distinctUntilChanged()
                    .collect { scrollRatioForRestore = it }
            }
            LaunchedEffect(pageMode, pagedChapters.size) {
                val ratio = scrollRatioForRestore
                if (!pageMode || pagedChapters.isEmpty() || ratio <= 0f) return@LaunchedEffect
                // 用后立即清零：后续改字号等触发重新分页时不得再跳回旧位置
                scrollRatioForRestore = 0f
                pagerState.scrollToPage(
                    (ratio * pagedChapters.size).toInt().coerceIn(0, pagedChapters.lastIndex),
                )
            }

            // ---- volume key turn ----
            val turnPage: (Int) -> Unit = { delta ->
                scope.launch {
                    if (pageMode && chapter != null) {
                        if (pagedChapters.isEmpty()) return@launch
                        val target = pagerState.currentPage + delta
                        when {
                            target in 0 until pagedChapters.size ->
                                pagerState.animateScrollToPage(target)

                            delta > 0 ->
                                if (rs.autoNextChapter) next?.let { vm.loadChapter(it.cid) }

                            delta < 0 ->
                                prev?.let { vm.loadChapter(it.cid) }
                        }
                    } else if (!pageMode) {
                        // 段落懒加载模式下按"视口高度的 85%"滚动：与旧的按页翻动观感一致
                        val step = listState.layoutInfo.viewportSize.height * 0.85f * delta
                        listState.animateScrollBy(step)
                    }
                }
            }
            // 音量键回调在组合之外被 MainActivity 调用，必须经 rememberUpdatedState 转发，
            // 否则单例会长期持有过期的 turnPage 闭包（读到旧的页号/章节）。
            val currentTurnPage by rememberUpdatedState(turnPage)
            // 注册与清理集中在同一个 DisposableEffect：设置关闭或退出阅读器时，
            // 由同一处把 enabled 与两个回调一起清空，避免残留闭包继续吞掉音量键。
            DisposableEffect(rs.volumeKeyTurnPage) {
                if (rs.volumeKeyTurnPage) {
                    // 音量键约定：上键 = 上一页，下键 = 下一页（与常见阅读器一致）
                    VolumeKeyTurn.onVolumeUp = { currentTurnPage(-1) }
                    VolumeKeyTurn.onVolumeDown = { currentTurnPage(1) }
                    VolumeKeyTurn.enabled = true
                }
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
                        detectTapGestures(
                            onTap = { offset ->
                                val w = size.width
                                val rtl = !rs.pageTurnDirection
                                when {
                                    offset.x < w / 3f ->
                                        if (pageMode && rs.clickTurnPage) currentTurnPage(if (rtl) 1 else -1)
                                        else immersive = true
                                    offset.x > w * 2f / 3f ->
                                        if (pageMode && rs.clickTurnPage) currentTurnPage(if (rtl) -1 else 1)
                                        else immersive = true

                                    else ->
                                        immersive = !immersive
                                }
                            },
                            // 长按必须显式接住：否则它会被当成"松手后才触发的点击"，
                            // 于是长按选字/长按插图的同时还会顺手切换沉浸态。
                            // 文本选择与插图预览各自处理长按，这里只需消费掉它。
                            onLongPress = {},
                        )
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
                                            // 文本选择：长按选词、拖动句柄；选区工具栏由系统提供
                                            SelectionContainer {
                                                Text(
                                                    page.text,
                                                    color = textColor,
                                                    fontFamily = fontFamilyFor(rs.fontFamily),
                                                    fontSize = rs.fontSize.sp,
                                                    fontWeight = FontWeight(rs.fontWeight),
                                                    lineHeight = (rs.fontSize * rs.lineSpacing).sp,
                                                )
                                            }
                                        }
                                        is ReaderPage.Image ->
                                            // 插图组件化：长按进入全屏预览（可缩放/保存）
                                            ReaderIllustration(
                                                url = page.url,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(contentPadding),
                                                // 分页模式：整页只放一张图，按 Fit 保证不裁切
                                                contentScale = ContentScale.Fit,
                                                onClick = { immersive = !immersive },
                                                onLongPress = { previewTarget = ReaderPreviewTarget(page.url, index) },
                                            )
                                        null -> {}
                                    }
                                }
                            }
                        } else {
                            // 参数列表内不内嵌 stringResource 调用，避免多行嵌套难以阅读
                            val positionText = stringResource(
                                R.string.reader_chapter_position,
                                (idx + 1).coerceAtLeast(0),
                                ui.flatChapters.size,
                            )
                            ScrollContent(
                                chapter = chapter,
                                paragraphs = paragraphs,
                                rs = rs,
                                textColor = textColor,
                                listState = listState,
                                paddingValues = contentPadding,
                                positionText = positionText,
                                onImageClick = { immersive = !immersive },
                                onImageLongPress = { imageIndex, url ->
                                    previewTarget = ReaderPreviewTarget(url, imageIndex)
                                },
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

                // 章节读完检测：翻到最后一页（页模式）或滚动到底（滚动模式）→ 标记"已读"。
                // snapshotFlow 只派生"是否已到末尾"这一布尔值：滚动模式下滚动位置每帧都在变，
                // 若直接发射数值会按 60Hz 触发收集端；布尔化（配合 distinctUntilChanged）
                // 使其只在"读完状态"翻转时发射一次。
                // pageMode 作为 key：模式切换后必须用新口径重新监听。
                LaunchedEffect(ui.currentCid, pageMode) {
                    val finishCid = ui.currentCid ?: return@LaunchedEffect
                    snapshotFlow {
                        readingPercentOf(
                            pageMode = pageMode,
                            currentPage = pagerState.currentPage,
                            pageCount = pagedChapters.size,
                            scrollFraction = listState.scrollFraction(),
                        ) >= PROGRESS_COMPLETE_PERCENT
                    }
                        .distinctUntilChanged()
                        .collect { finished -> if (finished) vm.markChapterFinished(finishCid) }
                }

                // status indicator: shown only in immersive, at the very bottom
                AnimatedVisibility(
                    visible = immersive,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    val progressPercent = readingPercentOf(
                        pageMode = pageMode,
                        currentPage = pagerState.currentPage,
                        pageCount = pagedChapters.size,
                        scrollFraction = listState.scrollFraction(),
                    )
                    IndicatorBar(
                        title = chapter?.title ?: "",
                        progressPercent = progressPercent,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }

                // floating chapter progress: shown only when the bottom bar is visible
                AnimatedVisibility(
                    visible = !immersive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = CONTENT_BOTTOM_INSET),
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    val chapterProgress = readingFractionOf(
                        pageMode = pageMode,
                        currentPage = pagerState.currentPage,
                        pageCount = pagedChapters.size,
                        scrollFraction = listState.scrollFraction(),
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        // 与主题的 Expressive 形状刻度一致（large = 20dp）
                        shape = MaterialTheme.shapes.large,
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
                                    } else {
                                        val itemCount = listState.layoutInfo.totalItemsCount
                                        if (itemCount > 1) {
                                            listState.scrollToItem(
                                                (progress * (itemCount - 1)).roundToInt()
                                                    .coerceIn(0, itemCount - 1)
                                            )
                                        }
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

        // 插图全屏预览：叠在阅读器之上（可缩放/平移、点空白返回、长按保存）
        previewTarget?.let { target ->
            ReaderImagePreview(
                url = target.url,
                displayName = target.fileName(ui.title),
                onDismiss = { previewTarget = null },
            )
        }
    }

    // ---- immersive auto-trigger on scroll ----
    LaunchedEffect(listState) {
        // 布尔派生 + distinctUntilChanged：只在"是否已滚动"翻转时发射一次，
        // 避免滚动模式下每帧（60Hz）重复写入 immersive（值不变属于无效工作）。
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
            .distinctUntilChanged()
            .collect { scrolled ->
                if (!scrolled) return@collect
                // 章节切换后会短暂抑制，拖动进度条时抑制窗口还会被持续续期：
                // 只能等到窗口真正结束（而不是固定延时一次），否则可能在用户操作中突然进入沉浸。
                while (true) {
                    val remaining = suppressImmersiveUntil - System.currentTimeMillis()
                    if (remaining <= 0L) break
                    delay(remaining)
                }
                immersive = true
            }
    }

    // 面板按 UI 风格二选一：MIUIX 用 miuix WindowBottomSheet，Material 用 M3 ModalBottomSheet
    if (showSettings) {
        if (isMiuixStyle()) {
            MiuixReaderSettingsSheet(rs, vm) { showSettings = false }
        } else {
            SettingsSheet(rs, vm) { showSettings = false }
        }
    }
    if (showToc) {
        if (isMiuixStyle()) {
            MiuixChapterSheet(ui, vm) { showToc = false }
        } else {
            ChapterSelectionSheet(ui, vm) { showToc = false }
        }
    }
}

// ------------------------------------------------------------------ //
// helpers
// ------------------------------------------------------------------ //

/** 从 Compose 的 Context 链上找到宿主 Activity（沉浸式系统栏控制需要窗口）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
