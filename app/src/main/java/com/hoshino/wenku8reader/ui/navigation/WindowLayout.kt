package com.hoshino.wenku8reader.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material adaptive window classes, measured with the current app window rather than the display. */
internal enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal fun windowWidthClass(width: Dp): WindowWidthClass = when {
    width < 600.dp -> WindowWidthClass.COMPACT
    width < 840.dp -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

internal val WindowWidthClass.usesSideNavigation: Boolean
    get() = this != WindowWidthClass.COMPACT

/**
 * Extra symmetric gutter needed to keep tablet reading lines from becoming uncomfortably wide.
 * The user-configured reader margins are added after this value.
 */
internal fun readerHorizontalGutter(
    windowWidth: Dp,
    maxTextWidth: Dp = 760.dp,
): Dp {
    if (windowWidth <= maxTextWidth) return 0.dp
    return (windowWidth - maxTextWidth) / 2
}

/** Pad landscape windows use a true facing-page spread; phones remain single-column. */
internal fun readerColumnCount(windowWidth: Dp): Int =
    if (windowWidth >= 840.dp) 2 else 1
