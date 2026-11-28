package com.hoshino.wenku8reader.ui.miuix

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.ui.AppViewModelProvider
import com.hoshino.wenku8reader.ui.account.AccountViewModel
import com.hoshino.wenku8reader.ui.account.WebsiteLoginActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 账户二级页（MIUIX 版）——与 Material 版（`ui/account/AccountScreen.kt`）零复用，
 * 共用同一个 [AccountViewModel]。登录跳转应用内网站登录页，应用不接触密码。
 */
@Composable
fun MiuixAccountPage(
    onBack: () -> Unit,
    vm: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

    MiuixSubPage(title = stringResource(R.string.settings_account_login), onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            MiuixSection(title = stringResource(R.string.settings_account_login)) {
                if (ui.userLoggedIn) {
                    MiuixRow(
                        title = ui.activeUsername.orEmpty(),
                        summary = stringResource(R.string.settings_account_login),
                        icon = Icons.Filled.AccountCircle,
                    )
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.account_website_login_hint),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        ui.error?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = err.asString(context),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                websiteLoginLauncher.launch(
                                    WebsiteLoginActivity.createIntent(
                                        context,
                                        vm.websiteLoginUrl(),
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.account_website_login_action)) }
                    }
                }
            }
            if (ui.userLoggedIn) {
                Spacer(Modifier.height(13.dp))
                Button(
                    onClick = vm::logout,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.account_logout_action)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
