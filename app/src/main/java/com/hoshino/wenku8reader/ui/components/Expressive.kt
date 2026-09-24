@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.hoshino.wenku8reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.SegmentedListItem as M3SegmentedListItem
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgress
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgress
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TabRow as MiuixTabRow
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference as MiuixDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hoshino.wenku8reader.ui.theme.isMiuixStyle

/**
 * 本项目的 Material 3 Expressive 组件层（material3 1.5 的 Expressive API）。
 *
 * 与旧版的区别（旧版是 material3 1.3 时代用稳定 API 手工模仿 SukiSU 风格）：
 * - 顶栏改用官方 Flexible 大顶栏，展开/收起由 motion scheme 驱动；
 * - 分组列表改用官方 `SegmentedListItem` + [ListItemDefaults.segmentedShapes]，
 *   按下时按 M3 Expressive 规范做形状变化，分组圆角也由官方 token 决定；
 * - 分段控件改用官方 [ButtonGroup] + `toggleableItem`（按下项变宽、相邻项压缩）；
 * - 加载/进度改用 [LoadingIndicator] 与 [LinearWavyProgressIndicator]；
 * - 装饰形状来自 [MaterialShapes]（cookie / clover / flower 等）；
 * - 动效统一取自 `MaterialTheme.motionScheme`（见 theme 层），不再写死弹簧参数。
 */

// ---------------------------------------------------------------------------
// 脚手架与顶栏
// ---------------------------------------------------------------------------

@Composable
fun ExpressiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (isMiuixStyle()) {
        // MIUIX 的 Scaffold 自带弹层宿主（popupHost），容器色取 MIUIX 的 surface
        MiuixScaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            containerColor = MiuixTheme.colorScheme.surface,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
        return
    }
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * 顶栏配色：容器与页面背景同色（滚动时不变色）。
 *
 * 旧实现调用的是 material3 1.3 的 `TopAppBarDefaults.largeTopAppBarColors`，
 * 该 API 自 1.4 起已弃用（改名为 `topAppBarColors`），此处统一收敛到这一个来源。
 */
@Composable
fun expressiveTopAppBarColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    scrolledContainerColor: Color = containerColor,
): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = containerColor,
    scrolledContainerColor = scrolledContainerColor,
)

/** 静态小顶栏（64dp）：主 Tab 用，滚动时不做高度动画，避免逐帧布局级联。 */
@Composable
fun ExpressiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = expressiveTopAppBarColors(),
) {
    if (isMiuixStyle()) {
        MiuixSmallTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
    )
}

/**
 * 子页顶部滚动行为：滚动时折叠到 64dp。
 * 需要配合内容区的 `Modifier.nestedScroll(behavior.nestedScrollConnection)` 使用。
 */
@Composable
fun rememberExpressiveScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = rememberTopAppBarState())

/**
 * 大顶栏（Expressive Flexible 版）：展开态两行（标题 + 可选副标题），
 * 滚动后收起为单行小顶栏，过渡由 motion scheme 的弹簧驱动。
 */
@Composable
fun ExpressiveLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = expressiveTopAppBarColors(),
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    if (isMiuixStyle()) {
        // MIUIX 原生大标题栏（HyperOS 的"大标题 + 副标题"）：与 M3 的折叠顶栏是两套设计，
        // 这里直接用 miuix 组件，不再模仿 M3 的观感。
        MiuixTopAppBar(
            title = title,
            largeTitle = title,
            // miuix 的 subtitle 是非空 String（空串表示不显示）
            subtitle = subtitle.orEmpty(),
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }
    LargeFlexibleTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        subtitle = subtitle?.let { text ->
            { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
    )
}

// ---------------------------------------------------------------------------
// 容器：卡片 / 装饰形状 / 空状态
// ---------------------------------------------------------------------------

/**
 * 大圆角卡片（[MaterialTheme.shapes.large]，Expressive 形状刻度里的 20dp 档）。
 *
 * 默认 0 阴影：列表滚动时阴影逐帧重绘是常见卡顿源，surfaceBright 与
 * surfaceContainer 的明度差已足够区分层级。
 */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = MaterialTheme.shapes.large,
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (isMiuixStyle()) {
        MiuixCard(
            modifier = modifier.then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
            cornerRadius = 16.dp,
            colors = MiuixCardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        ) { content() }
        return
    }
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
    when {
        onLongClick != null -> Card(
            modifier = modifier
                .clip(shape)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                ),
            colors = colors,
            elevation = cardElevation,
            shape = shape,
        ) { content() }

        onClick != null -> Card(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            elevation = cardElevation,
            shape = shape,
        ) { content() }

        else -> Card(
            modifier = modifier,
            colors = colors,
            elevation = cardElevation,
            shape = shape,
        ) { content() }
    }
}

/**
 * 装饰形状容器：把内容放进一个 M3 Expressive 形状（cookie / clover / flower…）里，
 * 用于空状态插画、关于页图标底、榜单名次徽标等装饰位。
 */
@Composable
fun DecorativeShapeBox(
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape.toShape())
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            // 在 Box 作用域内调用，因此 content 仍能拿到 BoxScope
            content()
        }
    }
}

/**
 * 统一空状态 / 错误态：装饰形状 + 标题 + 说明 + 可选操作按钮。
 * 各页原先各自手写 Column + Text，间距与文案风格分散在多个文件里。
 */
@Composable
fun ExpressiveEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (isMiuixStyle()) {
        MiuixEmptyState(
            title = title,
            modifier = modifier,
            description = description,
            icon = icon,
            shape = shape,
            error = error,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        return
    }
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DecorativeShapeBox(
            modifier = Modifier.size(88.dp),
            shape = shape,
            color = if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------------------
// 加载与进度
// ---------------------------------------------------------------------------

/**
 * 加载指示器（M3 Expressive）：在多个形状之间连续变形。
 * [size] 默认 48dp（规范里独立加载指示器的尺寸）。
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (isMiuixStyle()) {
        MiuixCircularProgress(modifier = modifier, size = size, progress = null)
        return
    }
    LoadingIndicator(
        modifier = modifier.size(size),
        color = color,
    )
}

/**
 * 活跃进度（下载 / 更新）：M3 Expressive 波浪进度条。
 *
 * 静止或长期存在的进度（如书架已读比例）用普通 LinearProgressIndicator ——
 * 波浪动效是为"正在进行"的短时任务准备的。
 */
@Composable
fun ActiveProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    if (isMiuixStyle()) {
        MiuixLinearProgress(modifier = modifier, progress = progress())
        return
    }
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

// ---------------------------------------------------------------------------
// 控件：分段按钮组 / 开关 / 标签
// ---------------------------------------------------------------------------

/**
 * Expressive 分段控件：官方 [ButtonGroup] + `toggleableItem`。
 * 按下时被按的项略微变宽、相邻项被压缩（Expressive 按钮组的标志性动效）。
 */
@Composable
fun ExpressiveToggleGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    if (isMiuixStyle()) {
        MiuixTabRow(
            tabs = labels,
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelect,
            modifier = modifier,
        )
        return
    }
    ButtonGroup(
        // 项过多时自动折叠进"更多"菜单（官方指示器：带 tooltip 的填充图标按钮）
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = modifier,
    ) {
        labels.forEachIndexed { index, label ->
            toggleableItem(
                checked = index == selectedIndex,
                label = label,
                onCheckedChange = { checked -> if (checked) onSelect(index) },
                icon = icons?.getOrNull(index)?.let { vector ->
                    { Icon(vector, contentDescription = null, modifier = Modifier.size(18.dp)) }
                },
                weight = 1f,
            )
        }
    }
}

/**
 * 开关：拇指带 ✓ / ✕（M3 Expressive 规范里的开关样式；material3 默认不画图标，
 * 需要由调用方通过 `thumbContent` 提供）。
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = expressiveSwitchColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showThumbIcon: Boolean = true,
) {
    if (isMiuixStyle()) {
        // MIUIX 的开关自带按压反馈与形态动画，不接受外部颜色/交互源
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = if (showThumbIcon && (checked || enabled)) {
            {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else null,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun expressiveSwitchColors(
    checkedIconColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledCheckedThumbColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
    disabledCheckedTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledCheckedIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledUncheckedThumbColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    disabledUncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f),
    disabledUncheckedBorderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledUncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
): SwitchColors = SwitchDefaults.colors(
    checkedIconColor = checkedIconColor,
    uncheckedIconColor = uncheckedIconColor,
    disabledCheckedThumbColor = disabledCheckedThumbColor,
    disabledCheckedTrackColor = disabledCheckedTrackColor,
    disabledCheckedIconColor = disabledCheckedIconColor,
    disabledUncheckedThumbColor = disabledUncheckedThumbColor,
    disabledUncheckedTrackColor = disabledUncheckedTrackColor,
    disabledUncheckedBorderColor = disabledUncheckedBorderColor,
    disabledUncheckedIconColor = disabledUncheckedIconColor,
)

/**
 * 滑块：M3 用 Expressive `Slider`，MIUIX 用 miuix 原生 `Slider`（带 HyperOS 的刻度与按压反馈）。
 * 两套风格的滑块外观本就不同，这里不做统一——MIUIX 模式以 HyperOS 观感为准。
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    if (isMiuixStyle()) {
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        return
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/** 状态小标签（书籍 Tags、格式标记等）。 */
@Composable
fun StatusTag(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(backgroundColor),
    shape: Shape = MaterialTheme.shapes.small,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(color = backgroundColor, shape = shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/**
 * MIUIX 风格的空状态：装饰形状 + MIUIX 字号/配色 + MIUIX 按钮。
 * （不直接用 miuix 的组件是因为它没有等价的"空状态"容器。）
 */
@Composable
private fun MiuixEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DecorativeShapeBox(
            modifier = Modifier.size(88.dp),
            shape = shape,
            color = if (error) colors.errorContainer else colors.primaryContainer,
            contentColor = if (error) colors.onErrorContainer else colors.onPrimaryContainer,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            color = if (error) colors.error else colors.onBackground,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            MiuixButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** MIUIX 分组卡片里的分隔线（HyperOS 设置页的细线，左右留白与内容对齐）。 */
@Composable
private fun MiuixSeparator() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
    )
}

/**
 * MIUIX 风格列表行（[SegmentedListItem] 在 MIUIX 模式下的渲染）。
 *
 * 为什么不用 miuix 的 `BasicComponent`：它的 `title`/`summary` 是 `String`，
 * 而本项目的行内容大量是任意 composable（封面、色点、Slider 等）。
 * 这里按 MIUIX 的排版取值（16dp 内边距、title/footnote 字号、onSurface 系列配色）自绘，
 * 与 `BasicComponent` 的观感一致。
 */
@Composable
private fun MiuixListRow(
    onClick: (() -> Unit)?,
    enabled: Boolean,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)?,
    overlineContent: @Composable (() -> Unit)?,
    leadingContent: @Composable (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            Box(Modifier.padding(end = 16.dp), contentAlignment = Alignment.Center) {
                leadingContent()
            }
        }
        Column(Modifier.weight(1f)) {
            if (overlineContent != null) {
                ProvideTextStyle(
                    MiuixTheme.textStyles.footnote2.copy(color = colors.onSurfaceSecondary),
                ) { overlineContent() }
            }
            ProvideTextStyle(MiuixTheme.textStyles.body1) { headlineContent() }
            if (supportingContent != null) {
                Spacer(Modifier.height(2.dp))
                ProvideTextStyle(
                    MiuixTheme.textStyles.footnote1.copy(color = colors.onSurfaceSecondary),
                ) { supportingContent() }
            }
        }
        if (trailingContent != null) {
            Box(Modifier.padding(start = 16.dp), contentAlignment = Alignment.Center) {
                trailingContent()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 分组列表（M3 Expressive SegmentedListItem）
// ---------------------------------------------------------------------------

/** 当前项在分组中的位置：由 [SegmentedColumn] 通过 CompositionLocal 下发。 */
private data class SegmentedSlot(val index: Int, val count: Int)

private val LocalSegmentedSlot = compositionLocalOf<SegmentedSlot?> { null }

/**
 * 分组卡片列：一组 [items] 合并为一张 surfaceBright 卡片组。
 *
 * 圆角与项间缝隙交给官方 token（[ListItemDefaults.segmentedShapes] /
 * [ListItemDefaults.SegmentedGap]）；[SegmentedListItem] 只需知道"这一项在组里的下标"。
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    items: List<@Composable () -> Unit>,
) {
    if (items.isEmpty()) return

    if (isMiuixStyle()) {
        Column(modifier = modifier) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                )
            }
            // MIUIX：整组放在一张圆角卡片里，项之间用细分隔线（HyperOS 设置页的经典形态）
            MiuixCard(
                cornerRadius = 16.dp,
                colors = MiuixCardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                ),
            ) {
                Column {
                    items.forEachIndexed { index, itemContent ->
                        if (index > 0) MiuixSeparator()
                        CompositionLocalProvider(
                            LocalSegmentedSlot provides SegmentedSlot(index, items.size),
                        ) {
                            itemContent()
                        }
                    }
                }
            }
        }
        return
    }

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, itemContent ->
                CompositionLocalProvider(
                    LocalSegmentedSlot provides SegmentedSlot(index, items.size),
                ) {
                    itemContent()
                }
            }
        }
    }
}

/**
 * 分组列表行：标题 + 次要文本 + 前导/尾随内容。
 *
 * 官方 `SegmentedListItem` 自带按下形状变化与正确的分组圆角；
 * 不可点击的行退化为普通 `ListItem`（仍按当前分组位置裁剪圆角）。
 */
@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val slot = LocalSegmentedSlot.current ?: SegmentedSlot(0, 1)

    if (isMiuixStyle()) {
        MiuixListRow(
            onClick = onClick,
            enabled = enabled,
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            overlineContent = overlineContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
        return
    }
    val shapes = ListItemDefaults.segmentedShapes(index = slot.index, count = slot.count)

    if (onClick != null) {
        M3SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier.alpha(if (enabled) 1f else 0.38f),
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright,
            ),
            content = headlineContent,
        )
    } else {
        ListItem(
            headlineContent = headlineContent,
            modifier = modifier
                .clip(shapes.shape)
                .alpha(if (enabled) 1f else 0.38f),
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceBright,
            ),
        )
    }
}

@Composable
fun SegmentedSwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    if (isMiuixStyle()) {
        MiuixSuperSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = summary,
            enabled = enabled,
            startAction = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
        )
        return
    }

    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        headlineContent = { Text(title) },
        leadingContent = icon?.let { { Icon(it, title) } },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
        supportingContent = summary?.let { { Text(it) } },
    )
}

@Composable
fun SegmentedDropdownItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onItemSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = if (items.isNotEmpty()) selectedIndex.coerceIn(0, items.lastIndex) else -1

    if (isMiuixStyle()) {
        // MIUIX 原生下拉偏好项：点开是 HyperOS 的圆角弹层（与 M3 的 DropdownMenu 是两套观感）
        MiuixDropdownPreference(
            items = items,
            selectedIndex = if (safeIndex >= 0) safeIndex else 0,
            title = title,
            summary = summary,
            enabled = enabled,
            startAction = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
            onSelectedIndexChange = onItemSelected,
        )
        return
    }

    Box {
        SegmentedListItem(
            onClick = if (enabled) {
                { expanded = true }
            } else null,
            enabled = enabled,
            leadingContent = icon?.let { { Icon(it, title) } },
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = {
                Text(
                    text = if (safeIndex >= 0) items[safeIndex] else "",
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.34f),
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    trailingIcon = {
                        if (index == safeIndex) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
