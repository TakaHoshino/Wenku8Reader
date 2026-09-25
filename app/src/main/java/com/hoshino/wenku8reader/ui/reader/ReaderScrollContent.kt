package com.hoshino.wenku8reader.ui.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.data.ChapterContent
import com.hoshino.wenku8reader.data.local.ReaderSettingsState
import com.hoshino.wenku8reader.ui.common.fontFamilyFor

/**
 * 滚动模式正文：**按段落懒加载**（不要退回"整章一个巨型 Text"）。
 *
 * 旧实现在一个 `verticalScroll` 的 Column 里渲染整章文本：章节越长，单次文本测量越大，
 * 且所有内容一次性组合。现在按段落切成 `LazyColumn` 的 item，只有视口附近的段落参与
 * 组合与测量，长章节的首帧与滚动都更稳。
 *
 * 段落列表由调用方用 [splitReaderParagraphs] 算好（纯函数、有单测），这里只负责渲染；
 * 文本整体套 [SelectionContainer]，让滚动模式也能长按选词/复制。插图走 [ReaderIllustration]，
 * 长按回调里把 URL 与序号交给页面去开全屏预览。
 */
@Composable
internal fun ScrollContent(
    chapter: ChapterContent,
    paragraphs: List<String>,
    rs: ReaderSettingsState,
    textColor: Color,
    listState: LazyListState,
    paddingValues: PaddingValues,
    positionText: String,
    onImageClick: () -> Unit,
    onImageLongPress: (index: Int, url: String) -> Unit,
) {
    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = paddingValues,
        ) {
            item(key = "title") {
                Text(
                    text = chapter.title,
                    color = textColor,
                    fontFamily = fontFamilyFor(rs.fontFamily),
                    fontSize = (rs.fontSize + 4).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = ((rs.fontSize + 4) * rs.lineSpacing).sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            itemsIndexed(paragraphs, key = { index, _ -> "p$index" }) { _, paragraph ->
                Text(
                    text = paragraph,
                    color = textColor,
                    fontFamily = fontFamilyFor(rs.fontFamily),
                    fontSize = rs.fontSize.sp,
                    fontWeight = FontWeight(rs.fontWeight),
                    lineHeight = (rs.fontSize * rs.lineSpacing).sp,
                )
            }
            if (chapter.images.isNotEmpty()) {
                item(key = "images_gap") { Spacer(Modifier.height(16.dp)) }
                itemsIndexed(chapter.images, key = { index, _ -> "img$index" }) { index, url ->
                    ReaderIllustration(
                        url = url,
                        onClick = onImageClick,
                        onLongPress = { onImageLongPress(index, url) },
                    )
                }
            }
            if (paragraphs.isEmpty() && chapter.images.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.reader_no_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            item(key = "position") {
                Text(
                    text = positionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            item(key = "tail") { Spacer(Modifier.height(32.dp)) }
        }
    }
}
