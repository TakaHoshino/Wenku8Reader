package com.hoshino.wenku8reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.ui.about.AboutScreen
import com.hoshino.wenku8reader.ui.author.AuthorBooksScreen
import com.hoshino.wenku8reader.ui.bookcase.BookcasePage
import com.hoshino.wenku8reader.ui.components.MainPagerState
import com.hoshino.wenku8reader.ui.components.rememberMainPagerState
import com.hoshino.wenku8reader.ui.detail.DetailScreen
import com.hoshino.wenku8reader.ui.downloads.DownloadsScreen
import com.hoshino.wenku8reader.ui.explore.ExplorePage
import com.hoshino.wenku8reader.ui.explore.SearchScreen
import com.hoshino.wenku8reader.ui.explore.TagBooksScreen
import com.hoshino.wenku8reader.ui.navigation.Routes
import com.hoshino.wenku8reader.ui.reader.ReaderScreen
import com.hoshino.wenku8reader.ui.settings.CustomizationScreen
import com.hoshino.wenku8reader.ui.settings.SettingsPage
import com.hoshino.wenku8reader.ui.settings.AppearanceSettingsPage
import com.hoshino.wenku8reader.ui.settings.NetworkSettingsPage
import com.hoshino.wenku8reader.ui.settings.UpdateSettingsPage
import com.hoshino.wenku8reader.ui.settings.ExperimentalSettingsPage
import com.hoshino.wenku8reader.ui.settings.StorageSettingsPage
import com.hoshino.wenku8reader.ui.stats.ReadingStatsScreen
import com.hoshino.wenku8reader.ui.toc.TocScreen
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost
import com.hoshino.wenku8reader.ui.theme.isMiuixStyle
import com.hoshino.wenku8reader.ui.components.isMiuixGlassSupported
import com.hoshino.wenku8reader.ui.miuix.MiuixSettingsPage
import com.hoshino.wenku8reader.ui.miuix.MiuixStoragePage
import com.hoshino.wenku8reader.ui.miuix.MiuixBookcasePage
import com.hoshino.wenku8reader.ui.miuix.MiuixAppearancePage
import com.hoshino.wenku8reader.ui.miuix.MiuixNetworkPage
import com.hoshino.wenku8reader.ui.miuix.MiuixUpdatePage
import com.hoshino.wenku8reader.ui.miuix.MiuixExperimentalPage
import com.hoshino.wenku8reader.ui.miuix.MiuixCustomizationPage
import com.hoshino.wenku8reader.ui.miuix.MiuixUpdateDialog
import com.hoshino.wenku8reader.ui.miuix.MiuixExplorePage
import com.hoshino.wenku8reader.ui.miuix.MiuixSearchPage
import com.hoshino.wenku8reader.ui.miuix.MiuixDetailPage
import com.hoshino.wenku8reader.ui.miuix.MiuixTocPage
import com.hoshino.wenku8reader.ui.miuix.MiuixAuthorBooksPage
import com.hoshino.wenku8reader.ui.miuix.MiuixTagBooksPage
import com.hoshino.wenku8reader.ui.miuix.MiuixStatsPage
import com.hoshino.wenku8reader.ui.miuix.lens
import com.hoshino.wenku8reader.ui.miuix.InnerShadow
import com.hoshino.wenku8reader.ui.miuix.innerShadow
import com.hoshino.wenku8reader.ui.miuix.rememberTiltBrightHighlight
import com.hoshino.wenku8reader.ui.miuix.vibrancy
import com.hoshino.wenku8reader.ui.miuix.MiuixDownloadsPage
import com.hoshino.wenku8reader.ui.miuix.MiuixAboutPage
import com.hoshino.wenku8reader.ui.miuix.LocalFloatingBarInset
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private data class TabDest(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TABS = listOf(
    TabDest(R.string.tab_explore, Icons.Filled.Explore, Icons.Outlined.Explore),
    TabDest(R.string.tab_bookcase, Icons.Filled.Book, Icons.Outlined.Book),
    TabDest(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** 启动更新检查的延迟：等首屏稳定后再发起，避免与启动渲染/首屏请求竞争网络与主线程。 */
private const val STARTUP_UPDATE_CHECK_DELAY_MS = 2000L

/**
 * 应用外壳。参考 SukiSU-Ultra：
 * - 主界面三个 Tab 用 HorizontalPager 承载，底栏点击以弹簧动画滑动切换（[MainPagerState]）；
 * - 详情 / 阅读器 / 下载 / 标签等子页走 NavHost 导航栈（自带顶栏，无底栏）；
 * - 在非首个 Tab 时按返回键先回首个 Tab，再回退导航栈。
 */
@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isMain = route == Routes.MAIN

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TABS.size })
    val mainPagerState = rememberMainPagerState(pagerState)

    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    // 启动时按设置检查更新（静默，有新版本才弹窗）
    val appContext = LocalContext.current.applicationContext as Wenku8Application
    val container = appContext.container
    // 底栏形态与液态玻璃来自设置（仅 MIUIX 风格生效）。
    // 这里刻意**只订阅需要的两个布尔**：原来直接 collect 整份 ReaderSettingsState，
    // 于是阅读器里拖一次字号滑杆（走同一个 StateFlow）也会让整个 Scaffold + NavHost 重组。
    // map + distinctUntilChanged 之后，只有这两个开关变化才会触发重组。
    val barStyle by remember(container) {
        container.readerSettings.flow
            .map { it.floatingBottomBar to it.bottomBarGlass }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = container.readerSettings.flow.value.let {
        it.floatingBottomBar to it.bottomBarGlass
    })
    val floatingBar = isMiuixStyle() && barStyle.first
    val glassEnabled = floatingBar && barStyle.second && isMiuixGlassSupported
    // 悬浮胶囊现在浮在手势条之上（底栏自己加了 navigationBarsPadding），
    // 因此滚动内容的底部余量要把系统导航栏高度一并算进去，最后一项才不会被压住。
    val navigationBarsBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    // 模糊源：内容由 NavHost 上的 `Modifier.layerBackdrop(backdrop)` 录制。
    //
    // 两个**不要踩的坑**（都验证过）：
    // 1) 不要把 `rememberLayerBackdrop { … drawContent() … }` 与"包裹消费者（底栏）的
    //    layerBackdrop 修饰符"同时使用：那条链会构成渲染树自引用，hwui 在
    //    RenderNode::prepareTreeImpl 里无限递归，实测直接让应用启动即闪退
    //    （crash buffer 里数百层 prepareTreeImpl/SkiaDisplayList 交替）。
    // 2) 消费者（底栏）必须留在被录制层**之外**，否则同样会自我引用。
    val backdrop = rememberLayerBackdrop()
    val updateState by container.updateCenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        delay(STARTUP_UPDATE_CHECK_DELAY_MS)
        // 必须读取**最新**的设置值：原实现在此读取的是 LaunchedEffect(Unit) 首次组合时
        // 捕获的旧快照，导致用户在延迟窗口内关掉「启动检查」仍会被检查（反之亦然）。
        // 更新中心自身还有 24h 节流（见 UpdateCenter.check），此处延迟只为避让首屏竞争。
        if (container.readerSettings.flow.value.checkUpdatesOnStartup) {
            container.updateCenter.check(manual = false)
        }
    }
    // 更新弹窗也按风格二选一：MIUIX 用 miuix WindowDialog，Material 用 M3 AlertDialog
    if (isMiuixStyle()) {
        MiuixUpdateDialog(
            state = updateState,
            currentVersionName = container.updateCenter.currentVersionName,
            onUpdate = container.updateCenter::download,
            onLater = container.updateCenter::later,
            onSkip = container.updateCenter::skip,
        )
    } else {
        UpdateDialogHost(
            state = updateState,
            currentVersionName = container.updateCenter.currentVersionName,
            onUpdate = container.updateCenter::download,
            onLater = container.updateCenter::later,
            onSkip = container.updateCenter::skip,
        )
    }

    BackHandler(enabled = isMain && pagerState.currentPage != 0) {
        mainPagerState.animateToPage(0)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            // 悬浮底栏**不占用** bottomBar 槽位（它是覆盖在内容之上的浮层，见下方 Box）：
            // 否则槽位高度（胶囊 + miuix 内部余量 + 系统栏 inset）会在内容下方留下一条空白带。
            if (isMain && !floatingBar) {
                MainBottomBar(
                    mainPagerState = mainPagerState,
                    backdrop = if (glassEnabled) backdrop else null,
                    floating = floatingBar,
                )
            }
        },
    ) { inner ->
        // 页面切换动效统一取自主题的 motion scheme：
        // Expressive 主题下是带弹性空间感的滑动，标准主题下自动退化为线性过渡。
        val motion = MaterialTheme.motionScheme
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.MAIN,
            modifier = Modifier
                .padding(bottom = inner.calculateBottomPadding())
                // 模糊源只登记页面内容；底栏（消费者）在外层 Box 里，不在这一层内，
                // 避免"自己模糊自己"的渲染树自引用（见 backdrop 处的说明）。
                .then(if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier),
            enterTransition = {
                fadeIn(motion.defaultEffectsSpec()) +
                    slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 }
            },
            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
            popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
            popExitTransition = {
                fadeOut(motion.fastEffectsSpec()) +
                    slideOutHorizontally(motion.fastSpatialSpec()) { it / 4 }
            },
        ) {
            composable(Routes.MAIN) {
                MainPagerScreen(
                    pagerState = pagerState,
                    floatingBarInset = if (floatingBar) 88.dp + navigationBarsBottom else 0.dp,
                    onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    onOpenTag = { tag -> nav.navigate(Routes.tag(tag)) },
                    onOpenDownloads = {
                        nav.navigate(Routes.DOWNLOADS) { launchSingleTop = true }
                    },
                    onSearch = { keyword, byAuthor ->
                        nav.navigate(Routes.search(keyword, byAuthor))
                    },
                    onOpenStats = { nav.navigate(Routes.STATS) { launchSingleTop = true } },
                    onOpenCustom = { nav.navigate(Routes.SETTINGS_CUSTOM) },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                    onOpenStorageSettings = { nav.navigate(Routes.STORAGE_SETTINGS) },
                    // 设置分类入口（PiliPlus 模式：主页只放分类，具体设置在二级页）
                    onOpenAppearance = { nav.navigate(Routes.SETTINGS_APPEARANCE) },
                    onOpenNetwork = { nav.navigate(Routes.SETTINGS_NETWORK) },
                    onOpenUpdate = { nav.navigate(Routes.SETTINGS_UPDATE) },
                    onOpenExperimental = { nav.navigate(Routes.SETTINGS_EXPERIMENTAL) },
                )
            }
            composable(
                Routes.SEARCH,
                arguments = listOf(
                    navArgument("keyword") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("byAuthor") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val keyword = android.net.Uri.decode(
                    entry.arguments?.getString("keyword") ?: "",
                )
                val byAuthor = entry.arguments?.getBoolean("byAuthor") ?: false
                if (isMiuixStyle()) {
                    MiuixSearchPage(
                        initialKeyword = keyword,
                        initialByAuthor = byAuthor,
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                } else {
                    SearchScreen(
                        initialKeyword = keyword,
                        initialByAuthor = byAuthor,
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                }
            }
            composable(Routes.STATS) {
                if (isMiuixStyle()) {
                    MiuixStatsPage(
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                } else {
                    ReadingStatsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                }
            }
            composable(Routes.SETTINGS_CUSTOM) {
                // MIUIX 模式使用完全独立的 miuix 阅读设置页（不复用 Material 版）
                if (isMiuixStyle()) {
                    MiuixCustomizationPage(onBack = { nav.popBackStack() })
                } else {
                    CustomizationScreen(onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.STORAGE_SETTINGS) {
                // MIUIX 模式使用完全独立的 miuix 存储页（不复用 Material 版）
                if (isMiuixStyle()) {
                    MiuixStoragePage(onBack = { nav.popBackStack() })
                } else {
                    StorageSettingsPage(onBack = { nav.popBackStack() })
                }
            }
            // 设置分类二级页：同一路由按风格走各自独立的实现
            composable(Routes.SETTINGS_APPEARANCE) {
                if (isMiuixStyle()) {
                    MiuixAppearancePage(onBack = { nav.popBackStack() })
                } else {
                    AppearanceSettingsPage(onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.SETTINGS_NETWORK) {
                if (isMiuixStyle()) {
                    MiuixNetworkPage(onBack = { nav.popBackStack() })
                } else {
                    NetworkSettingsPage(onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.SETTINGS_UPDATE) {
                if (isMiuixStyle()) {
                    MiuixUpdatePage(onBack = { nav.popBackStack() })
                } else {
                    UpdateSettingsPage(onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.SETTINGS_EXPERIMENTAL) {
                if (isMiuixStyle()) {
                    MiuixExperimentalPage(onBack = { nav.popBackStack() })
                } else {
                    ExperimentalSettingsPage(onBack = { nav.popBackStack() })
                }
            }
            composable(Routes.ABOUT) {
                if (isMiuixStyle()) {
                    MiuixAboutPage(onBack = { nav.popBackStack() })
                } else {
                    AboutScreen(onBack = { nav.popBackStack() })
                }
            }
            composable(
                Routes.TAG,
                arguments = listOf(navArgument("tag") { type = NavType.StringType }),
            ) { entry ->
                val tag = android.net.Uri.decode(entry.arguments?.getString("tag") ?: "")
                if (isMiuixStyle()) {
                    MiuixTagBooksPage(
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                } else {
                    TagBooksScreen(
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                }
            }
            composable(Routes.DOWNLOADS) {
                if (isMiuixStyle()) {
                    MiuixDownloadsPage(onBack = { nav.popBackStack() })
                } else {
                    DownloadsScreen(onBack = { nav.popBackStack() })
                }
            }
            composable(
                Routes.AUTHOR,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = android.net.Uri.decode(entry.arguments?.getString("name") ?: "")
                if (isMiuixStyle()) {
                    MiuixAuthorBooksPage(
                        authorName = name,
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                } else {
                    AuthorBooksScreen(
                        authorName = name,
                        onBack = { nav.popBackStack() },
                        onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                    )
                }
            }
            composable(
                Routes.TOC,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                if (isMiuixStyle()) {
                    MiuixTocPage(
                        onBack = { nav.popBackStack() },
                        onOpenChapter = { bookId, cid -> nav.navigate(Routes.reader(bookId, cid)) },
                    )
                } else {
                    TocScreen(
                        onBack = { nav.popBackStack() },
                        onOpenChapter = { bookId, cid -> nav.navigate(Routes.reader(bookId, cid)) },
                    )
                }
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                if (isMiuixStyle()) {
                    MiuixDetailPage(
                        onBack = { nav.popBackStack() },
                        onRead = { bookId -> nav.navigate(Routes.reader(bookId)) },
                        onOpenAuthor = { name -> nav.navigate(Routes.author(name)) },
                        onOpenTag = { tag -> nav.navigate(Routes.tag(tag)) },
                        onOpenToc = { bookId -> nav.navigate(Routes.toc(bookId)) },
                    )
                } else {
                    DetailScreen(
                        onBack = { nav.popBackStack() },
                        onRead = { bookId -> nav.navigate(Routes.reader(bookId)) },
                        onOpenAuthor = { name -> nav.navigate(Routes.author(name)) },
                        onOpenTag = { tag -> nav.navigate(Routes.tag(tag)) },
                        onOpenToc = { bookId -> nav.navigate(Routes.toc(bookId)) },
                    )
                }
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("cid") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                ReaderScreen(onBack = { nav.popBackStack() })
            }
        }
        // MIUIX 悬浮底栏：覆盖在内容之上——玻璃模糊采样的正是它背后的页面内容
        if (isMain && floatingBar) {
            MiuixFloatingBottomBar(
                mainPagerState = mainPagerState,
                backdrop = if (glassEnabled) backdrop else null,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        }
    }
}

/** 三个主 Tab 的分页载体。 */
@Composable
private fun MainPagerScreen(
    pagerState: PagerState,
    /** 悬浮底栏覆盖在内容之上时，各页把它加进**滚动内容**的底部内边距（见 LocalFloatingBarInset）。 */
    floatingBarInset: Dp = 0.dp,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onOpenStats: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenExperimental: () -> Unit,
) {
    CompositionLocalProvider(LocalFloatingBarInset provides floatingBarInset) {
    HorizontalPager(
        state = pagerState,
        // 只预组合相邻 1 页：原值 2 会让三个主 Tab 在启动瞬间**同时组合**，
        // Explore/Bookcase/Settings 的 LaunchedEffect 与 ViewModel 一并初始化
        //（含书架的全量 JSON 读取与设置页的缓存统计），明显拖慢冷启动。
        beyondViewportPageCount = 1,
    ) { page ->
        when (page) {
            0 ->
                // MIUIX 模式下探索页是独立的 miuix 实现
                if (isMiuixStyle()) {
                    MiuixExplorePage(
                        onOpenBook = onOpenBook,
                        onOpenTag = onOpenTag,
                        onOpenDownloads = onOpenDownloads,
                        onSearch = onSearch,
                    )
                } else {
                    ExplorePage(
                        onOpenBook = onOpenBook,
                        onOpenTag = onOpenTag,
                        onOpenDownloads = onOpenDownloads,
                        onSearch = onSearch,
                    )
                }
            1 ->
                // MIUIX 模式下书架是独立的 miuix 实现
                if (isMiuixStyle()) {
                    MiuixBookcasePage(
                        onOpenBook = onOpenBook,
                        onOpenDownloads = onOpenDownloads,
                        onOpenStats = onOpenStats,
                    )
                } else {
                    BookcasePage(
                        onOpenBook = onOpenBook,
                        onOpenDownloads = onOpenDownloads,
                        onOpenStats = onOpenStats,
                    )
                }
            2 ->
                // MIUIX 模式下设置页是独立的 miuix 实现（不与 Material 版共用任何 UI 代码）
                if (isMiuixStyle()) {
                    MiuixSettingsPage(
                        onOpenAppearance = onOpenAppearance,
                        onOpenReading = onOpenCustom,
                        onOpenNetwork = onOpenNetwork,
                        onOpenUpdate = onOpenUpdate,
                        onOpenStorageSettings = onOpenStorageSettings,
                        onOpenExperimental = onOpenExperimental,
                        onOpenDownloads = onOpenDownloads,
                        onOpenAbout = onOpenAbout,
                    )
                } else {
                    SettingsPage(
                        onOpenAppearance = onOpenAppearance,
                        onOpenReading = onOpenCustom,
                        onOpenNetwork = onOpenNetwork,
                        onOpenUpdate = onOpenUpdate,
                        onOpenExperimental = onOpenExperimental,
                        onOpenDownloads = onOpenDownloads,
                        onOpenAbout = onOpenAbout,
                        onOpenStorageSettings = onOpenStorageSettings,
                    )
                }
            // 显式兜底：新增 Tab 时若忘记补分支，这里会立刻暴露而不是静默渲染空白页
            else -> error("未知的 Tab 索引：$page（TABS 与 when 分支不一致）")
        }
    }
    }
}

/**
 * 底栏：
 * - Material 3 风格 → `NavigationBar`（与页面背景同色）；
 * - MIUIX 风格 → miuix `NavigationBar`（固定）或 `FloatingNavigationBar`（悬浮，HyperOS 的胶囊式底栏）；
 *   悬浮时若开启液态玻璃，则用 miuix-blur 的 `textureBlur` 对页面内容做实时模糊（见 [MiuixFloatingBottomBar]）。
 * 切换选中页统一走弹簧动画（[MainPagerState.animateToPage]）。
 */
@Composable
private fun MainBottomBar(
    mainPagerState: MainPagerState,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
    floating: Boolean = false,
) {
    if (isMiuixStyle()) {
        if (floating) {
            MiuixFloatingBottomBar(
                mainPagerState = mainPagerState,
                backdrop = backdrop,
            )
            return
        }
        MiuixNavigationBar {
            TABS.forEachIndexed { index, dest ->
                val selected = mainPagerState.selectedPage == index
                MiuixNavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) mainPagerState.animateToPage(index)
                    },
                    icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    label = stringResource(dest.labelRes),
                )
            }
        }
        return
    }
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) {
        TABS.forEachIndexed { index, dest ->
            val selected = mainPagerState.selectedPage == index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = stringResource(dest.labelRes),
                    )
                },
                label = {
                    Text(
                        stringResource(dest.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/**
 * MIUIX 悬浮底栏（HyperOS 风格的液态玻璃胶囊底栏）。
 *
 * 形态与效果照 SukiSU-Ultra 的悬浮底栏（其实现改编自 miuix 官方示例
 * `IosLiquidGlassNavigationBar`），但**不搬运**它的自定义着色器（vibrancy/lens 都是那边
 * 自己带的一套 `liquid/` 源码），只使用 miuix-blur 0.9.1 自带的公开能力：
 *
 * - 玻璃：`layerBackdrop` 把内容层登记为模糊源（登记在包住内容与底栏的那一层），这里用
 *   `drawBackdrop` 绘制：先模糊，再用 `BlurColors` 提亮/提对比/提饱和（近似 SukiSU 的
 *   vibrancy，替换掉它自带的着色器），最后用 `onDrawSurface` 铺一层**半透明**底色并加
 *   miuix 自带的玻璃高光描边（`Highlight.GlassStroke*`）模拟边缘反光。
 *   **底色必须足够透**（约 0.4）：早期版本用了 0.6~0.72 的主题色，肉眼看就是一块实色胶囊，
 *   模糊被完全盖住 —— 这正是"开了开关却没有液态玻璃效果"的原因；
 * - 滑块：选中项后面有一条随分页位置移动的胶囊滑块，位置直接读 `PagerState` 的连续偏移，
 *   所以拖动页面时滑块**跟手**，点击底栏时由弹簧收敛；
 * - 触感层级：选中项图标轻微放大 + 换主题色，未选中项降低不透明度。
 *
 * 未开启液态玻璃或系统 < Android 12L（`RenderEffect` 不可用）时，不调用任何模糊 API，
 * 退化为不透明容器 + 半透明滑块，保证可读性。
 */
@Composable
private fun MiuixFloatingBottomBar(
    mainPagerState: MainPagerState,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    modifier: Modifier = Modifier,
) {
    val glass = backdrop != null
    // 胶囊用圆角矩形而不是 CircleShape：折射着色器只认 CornerBasedShape，
    // CircleShape 会让 lens 直接跳过（这也是"照 SukiSU 写了 lens 却没效果"的常见原因）。
    // 半径取高度的一半，外形与 CircleShape 完全一致。
    val shape = RoundedCornerShape(FloatingBarHeight / 2)
    val accent = MiuixTheme.colorScheme.primary
    val container = MiuixTheme.colorScheme.surfaceContainer
    val barContent = MiuixTheme.colorScheme.onSurface
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    // 模糊半径按密度换算成 px（miuix 的 blur 取像素值）。这里刻意只取 4dp：
    // "看得见背景内容"是液态玻璃的前提，糊得太狠就只剩一块毛玻璃色块了；
    // 边缘的"液态感"主要由下面的折射（lens）负责。
    val blurRadiusPx = with(density) { 4.dp.toPx() }
    // 折射：边缘向内挤压背景模拟厚玻璃透镜；高度/强度取玻璃厚度的量级（约 18dp）。
    val refractionPx = with(density) { 18.dp.toPx() }
    // 滑块的折射高度/强度（SukiSU 用 10dp/14dp；这里按胶囊高度略放大，按下才拉满）
    val pillRefractionPx = with(density) { 12.dp.toPx() }
    // 橡皮筋位移幅度（SukiSU 用 4dp）
    val rubberBandPx = with(density) { 4.dp.toPx() }
    // 玻璃底色：必须够透（深色下略高一点保证可读）。这个不透明度就是"看不看得见玻璃"的关键——
    // 早期版本用 0.6~0.72 的主题色，肉眼就等同实色胶囊，模糊被完全盖住。
    val glassSurface = container.copy(alpha = if (isDark) 0.46f else 0.38f)
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    // 当前被按下的 Tab（null = 没有按下）：驱动滑块"滑到手指处 + 膨胀"与选中项放大
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    // 按压进度 0→1：滑块位移/放大、玻璃加深（色散折射）、表面提亮都读它
    val pressProgress by animateFloatAsState(
        targetValue = if (pressedIndex != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "floatingBarPressProgress",
    )
    // 边缘镜面高光跟随手机倾角（SukiSU 的做法），倾斜时高光会移动
    val tiltHighlight = rememberTiltBrightHighlight(extraDegrees = -45f)
    // 滑块自己的高光（SukiSU 给滑块单独一份、偏 90°，按下才淡入）
    val pillHighlight = rememberTiltBrightHighlight(extraDegrees = 90f)
    // 按住底栏左右拖动：dragValue 是以"格"为单位的连续位置，跟手移动后松手切到最近的标签
    var dragValue by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val innerPaddingPx = with(density) { FloatingBarInnerPadding.toPx() }
    val tabWidthPx = ((barWidthPx - innerPaddingPx * 2f) / TABS.size).coerceAtLeast(0f)

    Box(
        // 悬浮胶囊要浮在系统手势条之上（HyperOS 的做法），否则会与导航条叠在一起
        modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = FloatingBarOuterPadding, vertical = FloatingBarVerticalPadding)
                .fillMaxWidth()
                .height(FloatingBarHeight)
                .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = if (isDark) 0.26f else 0.12f,
                    ),
                )
                // 按住拖动切换标签（SukiSU 同款交互）：横向拖动超过触摸阈值后由这里接管，
                // 滑块跟手移动、整条底栏做橡皮筋位移，松手切到最近的标签。
                // 用 detectHorizontalDragGestures 而不是长按序列：按下即拖即可触发，
                // 轻点仍由各 Tab 的 clickable 处理（拖动一旦被消费，clickable 就不会触发）。
                .pointerInput(tabWidthPx, TABS.size) {
                    if (tabWidthPx <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            val pager = mainPagerState.pagerState
                            dragValue = pager.currentPage + pager.currentPageOffsetFraction
                            dragging = true
                            // 拖动优先于"按压高亮"，否则手指滑过时会出现两处高亮
                            pressedIndex = null
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragValue = (dragValue + dragAmount / tabWidthPx)
                                .coerceIn(0f, (TABS.size - 1).toFloat())
                        },
                        onDragEnd = {
                            val target = dragValue.roundToInt().coerceIn(0, TABS.size - 1)
                            dragging = false
                            if (target != mainPagerState.selectedPage) {
                                mainPagerState.animateToPage(target)
                            }
                        },
                        onDragCancel = { dragging = false },
                    )
                }
                // 橡皮筋：拖动时整条底栏沿拖动方向轻微位移，松手回落（幅度 4dp，克制不突兀）
                .graphicsLayer {
                    if (!dragging) return@graphicsLayer
                    val pager = mainPagerState.pagerState
                    val following = pager.currentPage + pager.currentPageOffsetFraction
                    val span = (TABS.size - 1).coerceAtLeast(1).toFloat()
                    val fraction = ((dragValue - following) / span).coerceIn(-1f, 1f)
                    translationX = rubberBandPx * fraction
                }
                .clip(shape)
                .then(
                    if (glass) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                // 顺序沿用 SukiSU：先增艳，再模糊，最后折射
                                vibrancy()
                                blur(blurRadiusPx, blurRadiusPx)
                                // 折射只在 Android 13+ 的 RuntimeShader 上生效，
                                // 低版本会自动跳过（模糊与高光仍在）
                                lens(
                                    refractionHeight = refractionPx,
                                    refractionAmount = refractionPx,
                                )
                                // 按下时再叠一层"色散折射"：边缘出现轻微彩色分离 + 深度感，
                                // 像玻璃被手指压厚了一层（SukiSU 压下滑块用的是同一组参数）
                                if (pressProgress > 0.01f) {
                                    lens(
                                        refractionHeight = refractionPx * 0.6f * pressProgress,
                                        refractionAmount = refractionPx * 0.8f * pressProgress,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f * pressProgress,
                                    )
                                }
                            },
                            highlight = {
                                // 跟倾角的镜面高光；暗色下用同一份（白色高光在深底上更明显）
                                tiltHighlight.copy(alpha = if (isDark) 0.75f else 0.6f)
                            },
                            // 底色要足够透，模糊才透得出来：0.6 以上在肉眼上等同实色胶囊
                            onDrawSurface = {
                                drawRect(glassSurface)
                                // 按住时整块玻璃轻微提亮（Plus 叠加，避免变灰）
                                if (pressProgress > 0f) {
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.06f * pressProgress),
                                        blendMode = BlendMode.Plus,
                                    )
                                }
                            },
                        )
                    } else {
                        Modifier.background(container, shape)
                    },
                )
                .padding(FloatingBarInnerPadding),
        ) {
            // 滑块：位置在 offset 的 lambda 里读取，避免拖动时每帧重组整条底栏
            if (tabWidthPx > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(with(density) { tabWidthPx.toDp() })
                        .offset {
                            val pager = mainPagerState.pagerState
                            val following = pager.currentPage + pager.currentPageOffsetFraction
                            val position = when {
                                // 正在拖动：滑块完全跟手
                                dragging -> dragValue
                                else -> {
                                    val pressed =
                                        (pressedIndex ?: mainPagerState.selectedPage).toFloat()
                                    lerp(following, pressed, pressProgress)
                                }
                            }
                            IntOffset((position * tabWidthPx).roundToInt(), 0)
                        }
                        // 按下/拖动时滑块放大：观感上像"玻璃被手指压得鼓起来"。
                        // 拖动时取 SukiSU 的 pressedScale（78/56 ≈ 1.39），按下取 1.16。
                        .graphicsLayer {
                            val s = if (dragging) 1.39f else 1f + 0.16f * pressProgress
                            scaleX = s
                            scaleY = s
                        }
                        .clip(shape)
                        .then(
                            if (glass) {
                                // 滑块是一层**独立的透镜玻璃**：只折射、不模糊（比磨砂底更清透），
                                // 按下时叠上色散与内阴影，边缘出现彩色分离与"被压下去"的深度。
                                // 这正是 SukiSU 悬浮底栏滑块的画法。
                                Modifier
                                    .drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { shape },
                                        effects = {
                                            // 注意：折射系数**不能**整项乘 pressProgress，
                                            // 否则未按下时 lens() 会因 refractionHeight <= 0 直接返回，
                                            // 当前标签就只剩一层纯色底（"当前标签没有玻璃效果"）。
                                            // 这里留 35% 的常驻折射做"透镜底子"，按下再拉到 100%。
                                            val lensWeight = 0.35f + 0.65f * pressProgress
                                            lens(
                                                refractionHeight = pillRefractionPx * lensWeight,
                                                refractionAmount = pillRefractionPx * 1.4f * lensWeight,
                                                depthEffect = true,
                                                chromaticAberration = 0.5f * pressProgress,
                                            )
                                        },
                                        highlight = {
                                            // 高光平时淡一点、按下变亮
                                            pillHighlight.copy(alpha = 0.25f + 0.55f * pressProgress)
                                        },
                                        onDrawSurface = {
                                            // 主题色标记"当前标签"
                                            drawRect(
                                                accent.copy(alpha = 0.16f + 0.08f * pressProgress),
                                            )
                                            // SukiSU 的薄纱：浅色主题压暗 10%、深色主题提亮 10%。
                                            // 这正是"当前标签看起来是一块玻璃"的关键——没有它，
                                            // 底下那层磨砂玻璃的纹理会被主题色盖平。
                                            drawRect(
                                                color = if (isDark) {
                                                    Color.White.copy(alpha = 0.10f)
                                                } else {
                                                    Color.Black.copy(alpha = 0.10f)
                                                },
                                                alpha = 1f - 0.6f * pressProgress,
                                            )
                                        },
                                    )
                                    .innerShadow(shape = shape) {
                                        if (pressProgress <= 0.01f) {
                                            null
                                        } else {
                                            InnerShadow(
                                                radius = 8.dp * pressProgress,
                                                color = Color.Black.copy(alpha = 0.15f),
                                                alpha = pressProgress,
                                            )
                                        }
                                    }
                            } else {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accent.copy(alpha = 0.18f + 0.1f * pressProgress),
                                            accent.copy(alpha = 0.1f + 0.08f * pressProgress),
                                        ),
                                    ),
                                )
                            },
                        ),
                )
            }
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEachIndexed { index, dest ->
                    val selected = mainPagerState.selectedPage == index
                    MiuixFloatingBarTab(
                        selected = selected,
                        // 拖动时离手指最近的那一格也放大到按压态
                        emphasized = dragging && index == dragValue.roundToInt(),
                        icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        label = stringResource(dest.labelRes),
                        accent = accent,
                        contentColor = barContent,
                        onPressChange = { pressed ->
                            pressedIndex = if (pressed) index else null
                        },
                        onClick = { if (!selected) mainPagerState.animateToPage(index) },
                    )
                }
            }
        }
    }
}

/** 悬浮底栏尺寸（与 SukiSU 一致：64dp 胶囊 + 4dp 内缩 + 12dp 外边距）。 */
private val FloatingBarHeight = 64.dp
private val FloatingBarInnerPadding = 4.dp
private val FloatingBarOuterPadding = 12.dp
private val FloatingBarVerticalPadding = 8.dp

/**
 * 单个 Tab：选中放大 1.1 倍并换主题色；**按下时整格放大到 1.2 倍**，并在手指位置叠加一层
 * 玻璃高光（SukiSU 悬浮底栏同款：按下时那个按钮会"以液态玻璃样式放大"）。
 *
 * 高光位置取自 [PressInteraction.Press] 的 `pressPosition`——框架已经把触摸点带出来了，
 * 因此不需要像 SukiSU 那样自建拖动手势类；着色也只用 `Brush.radialGradient` + `BlendMode.Plus`，
 * 不依赖 RuntimeShader，Android 8 起都有。
 */
@Composable
private fun RowScope.MiuixFloatingBarTab(
    selected: Boolean,
    emphasized: Boolean,
    icon: ImageVector,
    label: String,
    accent: Color,
    contentColor: Color,
    onPressChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var pressPosition by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                pressPosition = interaction.pressPosition
            }
        }
    }
    LaunchedEffect(pressed) { onPressChange(pressed) }

    val scale by animateFloatAsState(
        // 按下的倍率取 1.2、选中 1.1：与 SukiSU 的 `lerp(1f, 1.2f, pressProgress)` 一致
        targetValue = when {
            pressed || emphasized -> 1.2f
            selected -> 1.1f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "miuixFloatingTabScale",
    )
    val tint = if (selected) accent else contentColor.copy(alpha = 0.72f)
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            // 触摸点高光：手指按住哪儿，哪儿就亮起来（叠加混合，暗色下尤其明显）
            .drawWithContent {
                val position = pressPosition
                if (pressed && position != Offset.Unspecified) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = position,
                            radius = size.minDimension * 1.1f,
                        ),
                        radius = size.minDimension * 1.1f,
                        center = position,
                        blendMode = BlendMode.Plus,
                    )
                }
                drawContent()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            // 整格一起缩放（图标 + 文案），与 SukiSU 的 Tab 缩放口径一致
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        MiuixText(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = tint,
            maxLines = 1,
        )
    }
}
