package com.hoshino.wenku8reader.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.components.ExpressiveLargeTopAppBar
import com.hoshino.wenku8reader.ui.components.ExpressiveScaffold
import com.hoshino.wenku8reader.ui.components.TonalCard
import com.hoshino.wenku8reader.ui.components.rememberExpressiveScrollBehavior

/**
 * 账户二级页（Material 版）。
 *
 * 只在「实验性 → 账户登录」开启时可达（设置页账户分区才会变成可点击）。与 MIUIX 版
 * （`ui/miuix/MiuixAccountPage.kt`）零复用，共用同一个 [AccountViewModel]。
 *
 * 登录通过应用内网站登录页完成：应用不接触用户名/密码，只接收站点写入的 Cookie。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    vm: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = rememberExpressiveScrollBehavior()
    val websiteLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.completeWebsiteLogin(
                result.data?.getStringExtra(WebsiteLoginActivity.EXTRA_USERNAME),
            )
        }
    }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(ui.notice) {
        ui.notice?.let { message ->
            snackbarHostState.showSnackbar(message.asString(context))
            vm.dismissNotice()
        }
    }

    ExpressiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveLargeTopAppBar(
                title = stringResource(R.string.settings_account_login),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (ui.userLoggedIn) {
                LoggedInCard(username = ui.activeUsername.orEmpty(), onLogout = vm::logout)
            } else {
                WebsiteLoginCard(
                    errorText = ui.error?.asString(context),
                    onLogin = {
                        websiteLoginLauncher.launch(
                            WebsiteLoginActivity.createIntent(context, vm.websiteLoginUrl())
                        )
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 未登录：跳转真实网站完成登录，返回后只保存 Cookie。 */
@Composable
private fun WebsiteLoginCard(
    errorText: String?,
    onLogin: () -> Unit,
) {
    TonalCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_account_login),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.account_website_login_action)) }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.account_website_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 已登录：当前账户 + 退出登录。 */
@Composable
private fun LoggedInCard(username: String, onLogout: () -> Unit) {
    TonalCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.account_current_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = username, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_logout_action))
            }
        }
    }
}
