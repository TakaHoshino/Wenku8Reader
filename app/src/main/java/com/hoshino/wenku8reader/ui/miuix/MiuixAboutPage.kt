package com.hoshino.wenku8reader.ui.miuix

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUIX 关于页——与 Material 版（`ui/about/AboutScreen.kt`）完全独立。
 *
 * 形态：miuix 小标题顶栏 + 应用信息卡片（图标/名称/版本）+ 链接分组（miuix 箭头行）+
 * 介绍卡片；全部组件取自 miuix，文案复用 `strings.xml`。
 */
@Composable
fun MiuixAboutPage(
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val iconBackgroundColor = remember {
        Color(context.getColor(R.color.ic_launcher_background))
    }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }
    val repoUrl = stringResource(R.string.about_repo_url)
    val qqGroupUrl = stringResource(R.string.about_qq_group_url)
    val supportUrl = stringResource(R.string.about_support_url)

    MiuixSubPage(title = stringResource(R.string.about_title), onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            // 应用信息
            MiuixSection(
                title = stringResource(R.string.about_title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(iconBackgroundColor),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_version, version),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
            // 链接
            MiuixSection(
                title = stringResource(R.string.about_links),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                MiuixArrowRow(
                    title = stringResource(R.string.about_repo_title),
                    summary = repoUrl,
                    icon = Icons.Filled.Code,
                    onClick = { runCatching { uriHandler.openUri(repoUrl) } },
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.about_qq_group),
                    summary = stringResource(R.string.about_qq_group_desc),
                    icon = Icons.Filled.Forum,
                    onClick = { runCatching { uriHandler.openUri(qqGroupUrl) } },
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.about_support_title),
                    summary = stringResource(R.string.about_support_desc),
                    icon = Icons.Filled.Coffee,
                    onClick = { runCatching { uriHandler.openUri(supportUrl) } },
                )
            }
            // 应用介绍
            MiuixSection(
                title = stringResource(R.string.about_intro_title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_intro_body),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        textAlign = TextAlign.Justify,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_disclaimer),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
        }
    }
}
