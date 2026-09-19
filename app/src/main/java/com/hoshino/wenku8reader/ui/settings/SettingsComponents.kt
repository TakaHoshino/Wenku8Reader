package com.hoshino.wenku8reader.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * 下拉选项的选中下标：找不到时回退 0。
 *
 * `SegmentedDropdownItem.selectedIndex` 不接受负值，而设置值可能来自旧版本或被移除的选项，
 * 统一在此收敛为 -1 → 0，避免每个调用点都重复写一遍 `coerceAtLeast(0)`。
 * 设置主页与存储设置页都要用，故放在共享组件文件里。
 */
internal fun <T> List<T>.indexOfKey(key: T): Int = indexOf(key).coerceAtLeast(0)

/** 同上，但选项以 `(key, value)` 对存放：按 key 匹配而不是按整个 Pair 匹配。 */
internal fun <T> List<Pair<String, T>>.indexOfKey(key: String): Int =
    indexOfFirst { it.first == key }.coerceAtLeast(0)
