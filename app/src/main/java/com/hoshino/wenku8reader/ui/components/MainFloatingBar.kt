package com.hoshino.wenku8reader.ui.components

// 主界面悬浮底栏（MIUIX）：把本项目的三个 Tab 接到移植来的 SukiSU 液态玻璃底栏上。
// 从 MainScaffold.kt 拆出——它是"适配层"，与导航壳（MainScaffold）职责不同。

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.ui.components.MainPagerState
import com.hoshino.wenku8reader.ui.miuix.FloatingBottomBarItem
import com.hoshino.wenku8reader.ui.miuix.MiuixLiquidBottomBar
import com.hoshino.wenku8reader.ui.navigation.TABS
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 主界面悬浮底栏（MIUIX）：把本项目的三个 Tab 接到移植来的 SukiSU 液态玻璃底栏上。
 *
 * 玻璃/折射/高光/拖拽全部由 [MiuixLiquidBottomBar] 负责（那套实现是从 SukiSU-Ultra
 * 整段移植的，见该文件头部说明）；这里只做适配：
 * - 选中态与切换都交给 [MainPagerState]（它与 HorizontalPager 双向同步）；
 * - 深浅色取自 miuix 主题，避免与系统主题设置不一致。
 */
@Composable
internal fun MiuixMainFloatingBar(
    mainPagerState: MainPagerState,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop,
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // 胶囊外留白：SukiSU 的胶囊本身宽度由内容决定（IntrinsicSize.Min），这里用外层 padding
    // 控制它离屏幕底边与手势条的距离。
    Box(
        modifier = modifier.padding(
            start = FloatingBarOuterPadding,
            end = FloatingBarOuterPadding,
            bottom = FloatingBarVerticalPadding,
        ),
    ) {
        MiuixLiquidBottomBar(
            selectedIndex = { mainPagerState.selectedPage },
            onSelected = { index -> mainPagerState.animateToPage(index) },
            backdrop = backdrop,
            tabsCount = TABS.size,
            isDark = isDark,
            isBlurEnabled = glassEnabled,
        ) {
            TABS.forEachIndexed { index, dest ->
                val selected = mainPagerState.selectedPage == index
                FloatingBottomBarItem(
                    onClick = { mainPagerState.animateToPage(index) },
                    // SukiSU 的胶囊宽度由内容撑开（IntrinsicSize.Min），它的标签更长所以够宽；
                    // 本项目三个 Tab 都只有两个字，不加下限会挤成一团。76dp 是它的单格宽度量级。
                    modifier = Modifier.widthIn(min = 76.dp),
                ) {
                    MiuixIcon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = stringResource(dest.labelRes),
                        modifier = Modifier.size(24.dp),
                    )
                    MiuixText(
                        text = stringResource(dest.labelRes),
                        style = MiuixTheme.textStyles.footnote2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 悬浮胶囊相对屏幕左右的外留白。 */
private val FloatingBarOuterPadding = 12.dp
private val FloatingBarVerticalPadding = 8.dp
