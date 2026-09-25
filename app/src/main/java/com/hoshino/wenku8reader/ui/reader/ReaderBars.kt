package com.hoshino.wenku8reader.ui.reader

import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoshino.wenku8reader.R
import java.util.Date
import kotlinx.coroutines.delay

/**
 * 阅读器的顶栏 / 底栏 / 沉浸状态指示条。
 *
 * 从 `ReaderScreen.kt` 拆出（该文件此前 1300+ 行）：这三块只依赖阅读器设置与回调，
 * 与正文渲染、分页、手势无关，拆开后正文逻辑可以单独阅读与修改。
 */

/** 状态指示器（电量/时钟）刷新间隔：30 秒足够，且避免过频唤醒。 */
private const val BATTERY_CLOCK_REFRESH_MS = 30_000L

/** 电量读取失败时的兜底百分比（沿用旧逻辑：读不到按满电显示）。 */
private const val BATTERY_FALLBACK_PERCENT = 100

@Composable
internal fun ReaderTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back))
            }
        },
    )
}

@Composable
internal fun ReaderBottomBar(
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    autoTurn: Boolean,
    onToggleAutoTurn: () -> Unit,
    onOpenToc: () -> Unit,
    onSettings: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterNavButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.reader_prev_chapter),
                prevEnabled,
                onPrev,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleAutoTurn) {
                Icon(
                    if (autoTurn) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.reader_auto_turn),
                    tint = if (autoTurn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenToc) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_toc))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.reader_settings))
            }
            Spacer(Modifier.weight(1f))
            ChapterNavButton(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.reader_next_chapter),
                nextEnabled,
                onNext,
            )
        }
    }
}

@Composable
private fun ChapterNavButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * 状态指示器（沉浸时屏幕最底部的电量/时间/章节名/进度）。
 * 电池与时钟状态只在本组件内维护，避免每 30 秒触发整个阅读器重组。
 */
@Composable
internal fun IndicatorBar(
    title: String,
    progressPercent: Int,
    color: Color,
) {
    val context = LocalContext.current
    var batteryPercent by remember { mutableIntStateOf(readBattery(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryPercent = readBattery(context)
            now = System.currentTimeMillis()
            delay(BATTERY_CLOCK_REFRESH_MS)
        }
    }
    val timeText = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val batteryIcon = when {
            batteryPercent >= 80 -> Icons.Filled.BatteryFull
            batteryPercent >= 50 -> Icons.Filled.BatteryStd
            batteryPercent >= 20 -> Icons.Filled.Battery4Bar
            else -> Icons.Filled.BatteryAlert
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_battery, batteryPercent),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
            Spacer(Modifier.width(4.dp))
            Icon(batteryIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Text(timeText, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.reader_progress_percent, progressPercent),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

private fun readBattery(context: Context): Int {
    // 改用 BatteryManager.getIntProperty 直接读取系统服务中缓存的电量属性（无广播 IPC），
    // 替代原先 registerReceiver(null, ACTION_BATTERY_CHANGED) 的同步粘性广播读取，
    // 避免在 remember 初值与每 30 秒轮询时于主线程做一次进程间通信。
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val capacity = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    return if (capacity in 0..100) capacity else BATTERY_FALLBACK_PERCENT
}
