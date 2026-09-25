package com.hoshino.wenku8reader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.ExpressiveSlider
import com.hoshino.wenku8reader.ui.components.ExpressiveSwitch
import com.hoshino.wenku8reader.ui.components.ExpressiveToggleGroup
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior
import com.hoshino.wenku8reader.data.local.isDarkTheme
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.common.copyReaderBackgroundToInternal
import com.hoshino.wenku8reader.ui.common.fontFamilyFor
import com.hoshino.wenku8reader.ui.theme.seedColorOptions
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

private val FONT_OPTIONS = listOf(
    "default" to R.string.settings_font_default,
    "sans" to R.string.settings_font_sans,
    "serif" to R.string.settings_font_serif,
    "mono" to R.string.settings_font_mono,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
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
            // ActivityResult 回调在主线程：选中的原图可能几 MB～几十 MB，
            // 复制与解码压缩放到 IO（见 copyReaderBackgroundToInternal），避免阻塞 UI 线程。
            scope.launch {
                val path = copyReaderBackgroundToInternal(context, uri)
                if (path != null) vm.setBackgroundImage(path)
            }
        }
    }

    val scrollBehavior = rememberExpressiveScrollBehavior()

    ExpressiveScaffold(
        topBar = {
            ExpressiveLargeTopAppBar(
                title = stringResource(R.string.settings_custom),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // 简繁转换
            ListItem(
                leadingContent = { Icon(Icons.Filled.Translate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_simplified_traditional)) },
                        supportingContent = { Text(stringResource(R.string.settings_simplified_traditional_desc)) },
                        trailingContent = {
                    ExpressiveSwitch(
                        checked = rs.traditionalChinese,
                        onCheckedChange = { vm.setTraditionalChinese(it) },
                    )
                },
            )

            // 主题与纸张
            SectionTitle(stringResource(R.string.settings_theme_paper))

            SettingLabel(stringResource(R.string.settings_dark_mode))
            val darkModeOptions = listOf(
                "system" to R.string.settings_dark_system,
                "light" to R.string.settings_dark_light,
                "dark" to R.string.settings_dark_dark,
            )
            ExpressiveToggleGroup(
                labels = darkModeOptions.map { stringResource(it.second) },
                selectedIndex = darkModeOptions.indexOfFirst { it.first == rs.darkMode }.coerceAtLeast(0),
                onSelect = { index -> vm.setDarkMode(darkModeOptions[index].first) },
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )

            // 动态取色 / 手动取色
            ListItem(
                leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                trailingContent = {
                    ExpressiveSwitch(
                        checked = rs.dynamicColor,
                        onCheckedChange = { vm.setDynamicColor(it) },
                    )
                },
            )
            if (!rs.dynamicColor) {
                SettingLabel(stringResource(R.string.settings_manual_color))
                ColorSwatchesRow(seedColorOptions, rs.seedColor) { vm.setSeedColor(it) }
            }

            // 浅色模式配色（默认纯白背景 + 纯黑字体）
            SectionTitle(stringResource(R.string.settings_mode_light))
            SettingLabel(stringResource(R.string.settings_background_color))
            ColorSwatchesRow(LIGHT_PAPER_COLORS, rs.readerBackgroundLight) {
                vm.setReaderBackgroundLight(it)
            }
            SettingLabel(stringResource(R.string.settings_text_color))
            ColorSwatchesRow(LIGHT_TEXT_COLORS, rs.readerTextColorLight) {
                vm.setReaderTextColorLight(it)
            }

            // 深色模式配色（默认纯黑背景 + 纯白字体）
            SectionTitle(stringResource(R.string.settings_mode_dark))
            SettingLabel(stringResource(R.string.settings_background_color))
            ColorSwatchesRow(DARK_PAPER_COLORS, rs.readerBackgroundDark) {
                vm.setReaderBackgroundDark(it)
            }
            SettingLabel(stringResource(R.string.settings_text_color))
            ColorSwatchesRow(DARK_TEXT_COLORS, rs.readerTextColorDark) {
                vm.setReaderTextColorDark(it)
            }

            // 背景图片（浅色/深色模式共用）
            SettingLabel(stringResource(R.string.settings_background_image))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.settings_pick_image))
                }
                if (rs.backgroundImagePath != null) {
                    TextButton(onClick = { vm.setBackgroundImage(null) }) {
                        Text(stringResource(R.string.settings_clear_image))
                    }
                }
            }
            rs.backgroundImagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            // 字体
            SettingLabel(stringResource(R.string.settings_font))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                FONT_OPTIONS.forEach { (key, labelRes) ->
                    FilterChip(
                        selected = rs.fontFamily == key,
                        onClick = { vm.setFontFamily(key) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            // 字体大小
            SettingLabel(
                stringResource(R.string.settings_font_size) + " · " +
                    stringResource(R.string.settings_font_size_value, rs.fontSize)
            )
            ExpressiveSlider(
                value = rs.fontSize.toFloat(),
                onValueChange = { vm.setFontSize(it.roundToInt()) },
                valueRange = 14f..28f,
                steps = 6,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 字重
            SettingLabel(
                stringResource(R.string.settings_font_weight) + " · " +
                    stringResource(R.string.settings_font_weight_value, rs.fontWeight)
            )
            ExpressiveSlider(
                value = rs.fontWeight.toFloat(),
                onValueChange = { vm.setFontWeight(it.roundToInt()) },
                valueRange = 300f..700f,
                steps = 3,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 行间距
            SettingLabel(
                stringResource(R.string.settings_line_spacing) + " · " +
                    stringResource(R.string.settings_line_spacing_value, rs.lineSpacing)
            )
            ExpressiveSlider(
                value = rs.lineSpacing,
                onValueChange = { vm.setLineSpacing((it * 10f).roundToInt() / 10f) },
                valueRange = 1.2f..2.5f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 预览（跟随当前主题模式的阅读器配色）
            SettingLabel(stringResource(R.string.settings_preview))
            val isDarkPreview = rs.isDarkTheme(isSystemInDarkTheme())
            val previewPaper = if (isDarkPreview) Color(rs.readerBackgroundDark) else Color(rs.readerBackgroundLight)
            val previewText = if (isDarkPreview) Color(rs.readerTextColorDark) else Color(rs.readerTextColorLight)
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(previewPaper)
                        .padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_preview_text),
                        color = previewText,
                        fontFamily = fontFamilyFor(rs.fontFamily),
                        fontSize = rs.fontSize.sp,
                        fontWeight = FontWeight(rs.fontWeight),
                        lineHeight = (rs.fontSize * rs.lineSpacing).sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchesRow(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        colors.forEach { c ->
            val isSelected = c == selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(c) },
            )
        }
    }
}

