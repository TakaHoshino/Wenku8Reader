@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.hoshino.wenku8reader.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.material3.SegmentedListItem as M3SegmentedListItem


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
