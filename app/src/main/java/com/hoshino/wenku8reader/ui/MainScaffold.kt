package com.hoshino.wenku8reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.hoshino.wenku8reader.ui.components.miuixGlass
import com.hoshino.wenku8reader.ui.miuix.MiuixSettingsPage
import com.hoshino.wenku8reader.ui.miuix.MiuixStoragePage
import com.hoshino.wenku8reader.ui.miuix.MiuixBookcasePage
import com.hoshino.wenku8reader.ui.miuix.MiuixAppearancePage
import com.hoshino.wenku8reader.ui.miuix.MiuixNetworkPage
import com.hoshino.wenku8reader.ui.miuix.MiuixUpdatePage
import com.hoshino.wenku8reader.ui.miuix.MiuixExperimentalPage
import com.hoshino.wenku8reader.ui.miuix.LocalFloatingBarInset
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import kotlinx.coroutines.delay

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
    // 底栏形态与液态玻璃来自设置（仅 MIUIX 风格生效）
    val appSettings by container.readerSettings.flow.collectAsStateWithLifecycle()
    val floatingBar = isMiuixStyle() && appSettings.floatingBottomBar
    val glassEnabled = floatingBar && appSettings.bottomBarGlass && isMiuixGlassSupported
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
    UpdateDialogHost(
        state = updateState,
        currentVersionName = container.updateCenter.currentVersionName,
        onUpdate = container.updateCenter::download,
        onLater = container.updateCenter::later,
        onSkip = container.updateCenter::skip,
    )

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
                // 悬浮底栏的模糊源：把页面内容登记进 backdrop，底栏再对它做实时模糊
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
                    floatingBarInset = if (floatingBar) 88.dp else 0.dp,
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
                SearchScreen(
                    initialKeyword = keyword,
                    initialByAuthor = byAuthor,
                    onBack = { nav.popBackStack() },
                    onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.STATS) {
                ReadingStatsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.SETTINGS_CUSTOM) {
                CustomizationScreen(onBack = { nav.popBackStack() })
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
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.TAG,
                arguments = listOf(navArgument("tag") { type = NavType.StringType }),
            ) { entry ->
                val tag = android.net.Uri.decode(entry.arguments?.getString("tag") ?: "")
                TagBooksScreen(
                    onBack = { nav.popBackStack() },
                    onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.AUTHOR,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = android.net.Uri.decode(entry.arguments?.getString("name") ?: "")
                AuthorBooksScreen(
                    authorName = name,
                    onBack = { nav.popBackStack() },
                    onOpenBook = { id -> nav.navigate(Routes.detail(id)) },
                )
            }
            composable(
                Routes.TOC,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                TocScreen(
                    onBack = { nav.popBackStack() },
                    onOpenChapter = { bookId, cid -> nav.navigate(Routes.reader(bookId, cid)) },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                DetailScreen(
                    onBack = { nav.popBackStack() },
                    onRead = { bookId -> nav.navigate(Routes.reader(bookId)) },
                    onOpenAuthor = { name -> nav.navigate(Routes.author(name)) },
                    onOpenTag = { tag -> nav.navigate(Routes.tag(tag)) },
                    onOpenToc = { bookId -> nav.navigate(Routes.toc(bookId)) },
                )
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
            0 -> ExplorePage(
                onOpenBook = onOpenBook,
                onOpenTag = onOpenTag,
                onOpenDownloads = onOpenDownloads,
                onSearch = onSearch,
            )
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
 *   悬浮时若开启液态玻璃，则用 [miuixGlass] 对页面内容做实时模糊。
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
 * MIUIX 悬浮底栏（HyperOS 风格胶囊底栏）。
 *
 * 玻璃效果分两步：`layerBackdrop` 把页面内容登记为模糊源（在 MainScaffold 里作用于 NavHost），
 * 这里用 [miuixGlass] 把该源实时模糊后绘制在胶囊形状内；未开启或系统 < Android 12L 时
 * 退化为半透明底色（不调用任何模糊 API）。
 */
@Composable
private fun MiuixFloatingBottomBar(
    mainPagerState: MainPagerState,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    modifier: Modifier = Modifier,
) {
    val cornerRadius = 28.dp
    val shape = RoundedCornerShape(cornerRadius)
    val glass = backdrop != null
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        FloatingNavigationBar(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .miuixGlass(backdrop = backdrop, shape = shape),
            // 玻璃态用半透明容器，模糊才有"透出来"的观感；降级时用不透明容器保证可读性
            color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (glass) 0.72f else 1f),
            cornerRadius = cornerRadius,
        ) {
            TABS.forEachIndexed { index, dest ->
                val selected = mainPagerState.selectedPage == index
                FloatingNavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) mainPagerState.animateToPage(index)
                    },
                    icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    label = stringResource(dest.labelRes),
                )
            }
        }
    }
}
