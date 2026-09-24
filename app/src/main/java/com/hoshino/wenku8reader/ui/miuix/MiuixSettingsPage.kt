package com.hoshino.wenku8reader.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.di.AppContainer
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.settings.SettingsViewModel
import com.hoshino.wenku8reader.ui.update.UpdateDialogHost
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing

/**
 * MIUIX 设置主页——**只放分类入口**（PiliPlus 模式，参考其 `pages/setting/view.dart`：
 * 主页是「分类 + 副标题 + 箭头」的列表，具体设置项都在各分类的二级页）。
 *
 * 与 Material 版设置页完全独立（后者仍是单页内联分组，见 §5.6 的约定）。
 * 逻辑（设置读写、更新检查）复用同一个 [SettingsViewModel] 与 `UpdateCenter`。
 */
@Composable
fun MiuixSettingsPage(
    onOpenAppearance: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val updateCenter = remember(context) { context.appContainer.updateCenter }
    val updateState by updateCenter.state.collectAsStateWithLifecycle()

    MiuixPage(
        title = stringResource(R.string.tab_settings),
        actions = {
            MiuixIconButton(
                icon = Icons.Filled.Download,
                contentDescription = stringResource(R.string.action_downloads),
                onClick = onOpenDownloads,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            MiuixSection(title = stringResource(R.string.settings_section_account)) {
                MiuixRow(
                    title = stringResource(R.string.settings_builtin_account),
                    summary = stringResource(R.string.settings_account_builtin_desc),
                )
            }
            Spacer(Modifier.height(13.dp))
            // 各分类入口（点击进二级页）
            MiuixSection {
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_appearance),
                    summary = stringResource(R.string.settings_category_appearance_summary),
                    icon = Icons.Filled.DarkMode,
                    onClick = onOpenAppearance,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_reading),
                    summary = stringResource(R.string.settings_category_reading_summary),
                    icon = Icons.Filled.Tune,
                    onClick = onOpenReading,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_network),
                    summary = stringResource(R.string.settings_category_network_summary),
                    icon = Icons.Filled.Public,
                    onClick = onOpenNetwork,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_update),
                    summary = stringResource(R.string.settings_category_update_summary),
                    icon = Icons.Filled.SystemUpdate,
                    onClick = onOpenUpdate,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_storage),
                    summary = stringResource(R.string.settings_category_storage_summary),
                    icon = Icons.Filled.Storage,
                    onClick = onOpenStorageSettings,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_experimental),
                    summary = stringResource(R.string.settings_category_experimental_summary),
                    icon = Icons.Filled.Animation,
                    onClick = onOpenExperimental,
                )
                MiuixRowDivider()
                MiuixArrowRow(
                    title = stringResource(R.string.settings_section_about),
                    summary = stringResource(R.string.settings_category_about_summary),
                    icon = Icons.Filled.Info,
                    onClick = onOpenAbout,
                )
            }
            Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
        }
    }

    UpdateDialogHost(
        state = updateState,
        currentVersionName = updateCenter.currentVersionName,
        onUpdate = updateCenter::download,
        onLater = updateCenter::later,
        onSkip = updateCenter::skip,
    )
}

private val Context.appContainer: AppContainer
    get() = (applicationContext as Wenku8Application).container
