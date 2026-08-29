package com.hoshino.wenku8reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.about.AboutScreen
import com.hoshino.wenku8reader.ui.bookcase.BookcasePage
import com.hoshino.wenku8reader.ui.components.MainPagerState
import com.hoshino.wenku8reader.ui.components.rememberMainPagerState
import com.hoshino.wenku8reader.ui.detail.DetailScreen
import com.hoshino.wenku8reader.ui.downloads.DownloadsScreen
import com.hoshino.wenku8reader.ui.explore.ExplorePage
import com.hoshino.wenku8reader.ui.explore.TagBooksScreen
import com.hoshino.wenku8reader.ui.navigation.Routes
import com.hoshino.wenku8reader.ui.reader.ReaderScreen
import com.hoshino.wenku8reader.ui.settings.CustomizationScreen
import com.hoshino.wenku8reader.ui.settings.SettingsPage
import com.hoshino.wenku8reader.ui.stats.ReadingStatsScreen

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
        NavHost(
            navController = nav,
            startDestination = Routes.MAIN,
            modifier = Modifier.padding(bottom = inner.calculateBottomPadding()),
            enterTransition = {
                fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = {
                fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 4 }
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
                    onOpenStats = { nav.navigate(Routes.STATS) { launchSingleTop = true } },
                    onOpenCustom = { nav.navigate(Routes.SETTINGS_CUSTOM) },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
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
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) {
                DetailScreen(
                    onBack = { nav.popBackStack() },
                    onRead = { id -> nav.navigate(Routes.reader(id)) },
                )
            }
            composable(
                Routes.READER,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
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
    onOpenStats: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 2,
    ) { page ->
        when (page) {
            0 -> ExplorePage(
                onOpenBook = onOpenBook,
                onOpenTag = onOpenTag,
                onOpenDownloads = onOpenDownloads,
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
            )
        }
    }
}

/** 底栏：NavigationBar（surfaceContainer 同色）+ 弹簧滑动切换。 */
@Composable
private fun MainBottomBar(mainPagerState: MainPagerState) {
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
