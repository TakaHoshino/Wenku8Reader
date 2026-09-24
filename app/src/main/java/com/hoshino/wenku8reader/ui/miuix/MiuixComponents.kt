package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 独立界面层的基础件。
 *
 * 设计原则（与 Material 侧完全分离）：
 * - 这里**只用 miuix 组件**（Scaffold / TopAppBar / Card / BasicComponent 系列 / Slider / 进度），
 *   不再经过 `ui/components/Expressive.kt` 那套"M3 ↔ MIUIX 分派门面"；
 * - 颜色、字号一律取自 `MiuixTheme`，不读 `MaterialTheme`；
 * - 页面结构直接照 HyperOS 的习惯来（分组卡片 + 缩进分隔线 + 26dp 标题内边距），
 *   不迁就 Material 3 的观感。
 *
 * 图标仍用 `material-icons-extended` 的 `ImageVector`：miuix-icons 的图标集很小
 * （只有 search/check/arrow 等几个），不足以覆盖本项目需要的书籍/下载/统计等图标。
 */

/** MIUIX 页面骨架：miuix Scaffold + 大标题顶栏（无返回键版本，主 Tab 用）。 */
@Composable
fun MiuixPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                subtitle = subtitle.orEmpty(),
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        containerColor = MiuixTheme.colorScheme.surface,
    ) { inner -> content(inner) }
}

/** MIUIX 二级页骨架：miuix Scaffold + 小标题顶栏（带返回键）。 */
@Composable
fun MiuixSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    MiuixIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
                actions = actions,
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) { inner -> content(inner) }
}

/** MIUIX 图标按钮（miuix 原生按压反馈）。 */
@Composable
fun MiuixIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * MIUIX 分组卡片：一张圆角卡片 + 标题 + 内部若干行（行之间由调用方插入 [MiuixRowDivider]）。
 * 对应 HyperOS 设置页里"一组设置项一张卡"的形态。
 */
@Composable
fun MiuixSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 26.dp, bottom = 8.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(vertical = 4.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        ) {
            content()
        }
    }
}

/** 分组卡片内部的分隔线：左右缩进与文字对齐（HyperOS 的做法）。 */
@Composable
fun MiuixRowDivider(startIndent: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(
            start = startIndent,
            end = 16.dp,
        ),
    )
}

/** 普通设置项（标题 + 说明 + 可选前导图标/尾随内容），miuix `BasicComponent`。 */
@Composable
fun MiuixRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    top.yukonga.miuix.kmp.basic.BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        startAction = icon?.let { vector -> { MiuixRowIcon(vector) } },
        endActions = trailing ?: {},
        onClick = onClick,
        enabled = enabled,
    )
}

/** 跳转项（标题 + 说明 + 内置 chevron），miuix `ArrowPreference`。 */
@Composable
fun MiuixArrowRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        modifier = modifier,
        startAction = icon?.let { vector -> { MiuixRowIcon(vector) } },
        onClick = onClick,
        enabled = enabled,
    )
}

/** 开关项，miuix `SwitchPreference`。 */
@Composable
fun MiuixSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        modifier = modifier,
        startAction = icon?.let { vector -> { MiuixRowIcon(vector) } },
        enabled = enabled,
    )
}

/** 下拉项，miuix `WindowDropdownPreference`（HyperOS 圆角弹层）。 */
@Composable
fun MiuixDropdownRow(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    WindowDropdownPreference(
        items = items,
        selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        title = title,
        summary = summary,
        modifier = modifier,
        startAction = icon?.let { vector -> { MiuixRowIcon(vector) } },
        enabled = enabled,
        onSelectedIndexChange = onSelected,
    )
}

/** 滑块项：标题 + 数值 + miuix `Slider`（HyperOS 的刻度与按压反馈）。 */
@Composable
fun MiuixSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueText: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 行内小图标（统一尺寸与配色，避免每个调用点各写一遍）。 */
@Composable
fun MiuixRowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.width(24.dp).height(24.dp),
        tint = MiuixTheme.colorScheme.primary,
    )
}

/** MIUIX 加载态。 */
@Composable
fun MiuixLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = null)
    }
}

/** MIUIX 空态/错误态（标题 + 说明 + 可选操作）。 */
@Composable
fun MiuixEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            top.yukonga.miuix.kmp.basic.Button(onClick = onAction) { Text(actionText) }
        }
    }
}

/**
 * 页面内容的统一外边距（HyperOS 设置页的左右 16dp + 底部留白）。
 * 注意：不用 `WindowInsets.safeDrawing`——miuix 顶栏/底栏自带 windowInsets padding。
 */
val MiuixPagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
