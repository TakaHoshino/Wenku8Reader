package com.hoshino.wenku8reader.ui.miuix

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.local.isDarkTheme
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.copyReaderBackgroundToInternal
import com.hoshino.wenku8reader.ui.common.fontFamilyFor
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 阅读设置页——与 Material 版（`ui/settings/CustomizationScreen.kt`）**完全独立**。
 *
 * 只用 miuix 组件（分组卡片 / BasicComponent 系列 / TabRow / Slider / 自定义色块），
 * 配色与字号一律取自 `MiuixTheme`；逻辑（设置读写、背景图落盘）复用 [SettingsViewModel]
 * 与 `ui/common/ReaderBackground.kt`。
 *
 * 与 Material 版的差异（刻意的）：
 * - **不出现**「动态取色 / 手动主题色」这类 MD3 专属项——MIUIX 走自己的色板；
 * - 字体由 HyperOS 的分段控件（miuix `TabRow`）选择，而不是 Material 的 FilterChip；
 * - 色板改为自绘圆点（miuix 没有取色组件，且这里只是固定色板，不是任意取色）。
 */

/** 浅色模式阅读器背景色（首项为默认纯白）。 */
private val LIGHT_PAPER_COLORS = listOf(
    0xFFFFFFFFL, 0xFFFDF6E3L, 0xFFF0F0F0L, 0xFFE8F5E9L, 0xFFEAF2F8L,
)

/** 浅色模式阅读器字体色（首项为默认纯黑）。 */
private val LIGHT_TEXT_COLORS = listOf(
    0xFF000000L, 0xFF1B1B1BL, 0xFF333333L, 0xFF3B5A40L, 0xFF5B4636L,
)

/** 深色模式阅读器背景色（首项为默认纯黑）。 */
private val DARK_PAPER_COLORS = listOf(
    0xFF000000L, 0xFF10141AL, 0xFF1F1F1FL, 0xFF183028L, 0xFF262220L,
)

/** 深色模式阅读器字体色（首项为默认纯白）。 */
private val DARK_TEXT_COLORS = listOf(
    0xFFFFFFFFL, 0xFFECECECL, 0xFFB0B0B0L, 0xFF9FBFA5L, 0xFFC9BFA8L,
)

/** 字体选项（持久化的 key 与文案资源）。 */
private val FONT_OPTIONS = listOf(
    "default" to R.string.settings_font_default,
    "sans" to R.string.settings_font_sans,
    "serif" to R.string.settings_font_serif,
    "mono" to R.string.settings_font_mono,
)

private val DARK_MODE_OPTIONS = listOf(
    "system" to R.string.settings_dark_system,
    "light" to R.string.settings_dark_light,
    "dark" to R.string.settings_dark_dark,
)

@Composable
fun MiuixCustomizationPage(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rs by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // ActivityResult 回调在主线程：原图可能几 MB～几十 MB，
            // 复制与解码压缩在 IO 线程完成（copyReaderBackgroundToInternal 内部切线程）。
            scope.launch {
                val path = copyReaderBackgroundToInternal(context, uri)
                if (path != null) vm.setBackgroundImage(path)
            }
        }
    }

    MiuixSubPage(title = stringResource(R.string.settings_section_reading), onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                // 阅读习惯：简繁转换 + 深色模式
                // （MIUIX 不使用动态取色，故「动态取色 / 手动主题色 / 纯黑模式」不在此出现）
                MiuixSection(title = stringResource(R.string.settings_reading_habit)) {
                    MiuixSwitchRow(
                        title = stringResource(R.string.settings_simplified_traditional),
                        summary = stringResource(R.string.settings_simplified_traditional_desc),
                        icon = Icons.Filled.Translate,
                        checked = rs.traditionalChinese,
                        onCheckedChange = vm::setTraditionalChinese,
                    )
                    MiuixRowDivider()
                    MiuixDropdownRow(
                        title = stringResource(R.string.settings_dark_mode),
                        summary = stringResource(R.string.settings_dark_mode_summary),
                        icon = Icons.Filled.DarkMode,
                        items = DARK_MODE_OPTIONS.map { stringResource(it.second) },
                        selectedIndex = DARK_MODE_OPTIONS
                            .indexOfFirst { it.first == rs.darkMode }
                            .coerceAtLeast(0),
                        onSelected = { index -> vm.setDarkMode(DARK_MODE_OPTIONS[index].first) },
                    )
                }

                Spacer(Modifier.height(13.dp))
                MiuixSection(title = stringResource(R.string.settings_mode_light)) {
                    MiuixColorRow(
                        title = stringResource(R.string.settings_background_color),
                        colors = LIGHT_PAPER_COLORS,
                        selected = rs.readerBackgroundLight,
                        onSelect = vm::setReaderBackgroundLight,
                    )
                    MiuixRowDivider()
                    MiuixColorRow(
                        title = stringResource(R.string.settings_text_color),
                        colors = LIGHT_TEXT_COLORS,
                        selected = rs.readerTextColorLight,
                        onSelect = vm::setReaderTextColorLight,
                    )
                }

                Spacer(Modifier.height(13.dp))
                MiuixSection(title = stringResource(R.string.settings_mode_dark)) {
                    MiuixColorRow(
                        title = stringResource(R.string.settings_background_color),
                        colors = DARK_PAPER_COLORS,
                        selected = rs.readerBackgroundDark,
                        onSelect = vm::setReaderBackgroundDark,
                    )
                    MiuixRowDivider()
                    MiuixColorRow(
                        title = stringResource(R.string.settings_text_color),
                        colors = DARK_TEXT_COLORS,
                        selected = rs.readerTextColorDark,
                        onSelect = vm::setReaderTextColorDark,
                    )
                }

                Spacer(Modifier.height(13.dp))
                // 背景图（浅色/深色共用同一张）
                MiuixSection(title = stringResource(R.string.settings_background_image)) {
                    MiuixRow(
                        title = stringResource(R.string.settings_pick_image),
                        summary = stringResource(R.string.settings_background_image_desc),
                        icon = Icons.Filled.Image,
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                    )
                    if (rs.backgroundImagePath != null) {
                        MiuixRowDivider()
                        MiuixRow(
                            title = stringResource(R.string.settings_clear_image),
                            onClick = { vm.setBackgroundImage(null) },
                        )
                    }
                    rs.backgroundImagePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                Spacer(Modifier.height(13.dp))
                // 字体：HyperOS 的分段控件
                MiuixSection(title = stringResource(R.string.settings_font)) {
                    TabRow(
                        tabs = FONT_OPTIONS.map { stringResource(it.second) },
                        selectedTabIndex = FONT_OPTIONS
                            .indexOfFirst { it.first == rs.fontFamily }
                            .coerceAtLeast(0),
                        onTabSelected = { index -> vm.setFontFamily(FONT_OPTIONS[index].first) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                Spacer(Modifier.height(13.dp))
                // 排版：字号 / 字重 / 行距
                MiuixSection(title = stringResource(R.string.settings_typography)) {
                    MiuixSliderRow(
                        title = stringResource(R.string.settings_font_size),
                        valueText = stringResource(R.string.settings_font_size_value, rs.fontSize),
                        value = rs.fontSize.toFloat(),
                        onValueChange = { vm.setFontSize(it.roundToInt()) },
                        valueRange = 14f..28f,
                        steps = 6,
                    )
                    MiuixRowDivider()
                    MiuixSliderRow(
                        title = stringResource(R.string.settings_font_weight),
                        valueText = stringResource(R.string.settings_font_weight_value, rs.fontWeight),
                        value = rs.fontWeight.toFloat(),
                        onValueChange = { vm.setFontWeight(it.roundToInt()) },
                        valueRange = 300f..700f,
                        steps = 3,
                    )
                    MiuixRowDivider()
                    MiuixSliderRow(
                        title = stringResource(R.string.settings_line_spacing),
                        valueText = stringResource(
                            R.string.settings_line_spacing_value,
                            rs.lineSpacing,
                        ),
                        value = rs.lineSpacing,
                        onValueChange = { vm.setLineSpacing((it * 10f).roundToInt() / 10f) },
                        valueRange = 1.2f..2.5f,
                    )
                }

                Spacer(Modifier.height(13.dp))
                // 预览（跟随当前主题模式的阅读器配色）
                MiuixSection(title = stringResource(R.string.settings_preview)) {
                    val isDarkPreview = rs.isDarkTheme(isSystemInDarkTheme())
                    val previewPaper =
                        if (isDarkPreview) Color(rs.readerBackgroundDark) else Color(rs.readerBackgroundLight)
                    val previewText =
                        if (isDarkPreview) Color(rs.readerTextColorDark) else Color(rs.readerTextColorLight)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(previewPaper)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_preview_text),
                            color = previewText,
                            fontFamily = fontFamilyFor(rs.fontFamily),
                            fontSize = rs.fontSize.sp,
                            fontWeight = FontWeight(rs.fontWeight),
                            lineHeight = (rs.fontSize * rs.lineSpacing).sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
        }
    }
}

/**
 * 一行固定色板（标题 + 可点选的圆形色块）。
 * 选中态用主题色描边 + 间隙，未选中态用 `outline` 细描边（纯黑/纯白在两套主题下都能看出边界）。
 */
@Composable
private fun MiuixColorRow(
    title: String,
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { value ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        )
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}
