package com.hoshino.wenku8reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.hoshino.wenku8reader.ui.settings.StorageSettingsPage
import com.hoshino.wenku8reader.ui.stats.ReadingStatsScreen
import com.hoshino.wenku8reader.ui.toc.TocScreen
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost
import com.hoshino.wenku8reader.ui.theme.isMiuixStyle
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
            if (isMain) {
                MainBottomBar(mainPagerState)
            }
        },
    ) { inner ->
        // 页面切换动效统一取自主题的 motion scheme：
        // Expressive 主题下是带弹性空间感的滑动，标准主题下自动退化为线性过渡。
        val motion = MaterialTheme.motionScheme
        NavHost(
            navController = nav,
            startDestination = Routes.MAIN,
            modifier = Modifier.padding(bottom = inner.calculateBottomPadding()),
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
                StorageSettingsPage(onBack = { nav.popBackStack() })
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
    }
}

/** 三个主 Tab 的分页载体。 */
@Composable
private fun MainPagerScreen(
    pagerState: PagerState,
    onOpenBook: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onOpenStats: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStorageSettings: () -> Unit,
) {
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
            1 -> BookcasePage(
                onOpenBook = onOpenBook,
                onOpenDownloads = onOpenDownloads,
                onOpenStats = onOpenStats,
            )
            2 -> SettingsPage(
                onOpenCustom = onOpenCustom,
                onOpenDownloads = onOpenDownloads,
                onOpenAbout = onOpenAbout,
                onOpenStorageSettings = onOpenStorageSettings,
            )
            // 显式兜底：新增 Tab 时若忘记补分支，这里会立刻暴露而不是静默渲染空白页
            else -> error("未知的 Tab 索引：$page（TABS 与 when 分支不一致）")
        }
    }
}

/** 底栏：NavigationBar（surfaceContainer 同色）+ 弹簧滑动切换。 */
@Composable
private fun MainBottomBar(mainPagerState: MainPagerState) {
    if (isMiuixStyle()) {
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
