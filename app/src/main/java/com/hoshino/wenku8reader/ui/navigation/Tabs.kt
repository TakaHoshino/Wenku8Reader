package com.hoshino.wenku8reader.ui.navigation

// 主界面三个 Tab 的定义（图标、文案、选中态）。
// 从 MainScaffold.kt 拆出：底栏、分页载体与浮层适配都要用它，
// 放在 navigation 包下与 Routes 一起，避免"谁定义 Tab"这个问题散落在大文件里。

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.hoshino.wenku8reader.R

internal data class TabDest(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

internal val TABS = listOf(
    TabDest(R.string.tab_explore, Icons.Filled.Explore, Icons.Outlined.Explore),
    TabDest(R.string.tab_bookcase, Icons.Filled.Book, Icons.Outlined.Book),
    TabDest(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

