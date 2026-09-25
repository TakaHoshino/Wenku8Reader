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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
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
 *
 * 注意：这里只服务 Material 侧页面。MIUIX 页面不再经过这个门面（它们各自用 miuix 顶栏，
 * 自带折叠），因此本文件不再需要"MIUIX 下返回不消费滚动的空实现"那套分派。
 */
@Composable
fun rememberExpressiveScrollBehavior(): TopAppBarScrollBehavior {
    val state = rememberTopAppBarState()
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = state)
}

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

/** 滑块：M3 Expressive 版本。MIUIX 侧用的是 miuix 原生 `Slider`（见 ui/miuix 下的各页）。 */
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

