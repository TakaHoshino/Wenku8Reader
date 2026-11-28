package com.hoshino.wenku8reader.ui.account

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import com.hoshino.wenku8reader.R
import com.hoshino.wenku8reader.Wenku8Application
import com.hoshino.wenku8reader.data.UserAgent
import com.hoshino.wenku8reader.data.Wenku8Hosts
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONTokener

/**
 * 应用内网站登录页。
 *
 * 用户在真实 wenku8 登录表单中完成验证码/风控校验后，这里把 WebView CookieManager
 * 里的会话 Cookie 复制到应用共享的 CookieStore；返回账户页时无需再保存密码。
 */
class WebsiteLoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOGIN_URL = "login_url"
        const val EXTRA_USERNAME = "username"
        private val EXTRACT_USERNAME_JS = """
            (function() {
              const selectors = [
                'a[href*="userdetail"]', 'a[href*="usercp"]',
                '#siteNav a[href*="user"]', '.userinfo a'
              ];
              const ignored = new Set(['登录', '用户中心', '个人中心', '退出登录']);
              for (const selector of selectors) {
                for (const node of document.querySelectorAll(selector)) {
                  const text = (node.textContent || '').trim();
                  if (text && text.length <= 40 && !ignored.has(text)) return text;
                }
              }
              return '';
            })()
        """.trimIndent()

        fun createIntent(context: Context, loginUrl: String): Intent =
            Intent(context, WebsiteLoginActivity::class.java)
                .putExtra(EXTRA_LOGIN_URL, loginUrl)
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var deliveredResult = false
    private var lastPageUrl: String? = null

    private val cookieStore get() = (application as Wenku8Application).container.cookieStore
    private val logger get() = (application as Wenku8Application).container.logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loginUrl = Wenku8Hosts.secureMainSiteUrl(intent.getStringExtra(EXTRA_LOGIN_URL).orEmpty())
        if (runCatching { loginUrl.toHttpUrl() }.isFailure) {
            finish()
            return
        }

        val deviceUa = UserAgent.build(Build.VERSION.RELEASE.orEmpty(), Build.MODEL.orEmpty())
        CookieManager.getInstance().setAcceptCookie(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(buildToolbar())
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        root.addView(
            progress,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)),
        )

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.userAgentString = deviceUa
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val requested = request?.url?.toString() ?: return false
                    val secure = Wenku8Hosts.secureMainSiteUrl(requested)
                    if (secure != requested) {
                        view?.loadUrl(secure)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    this@WebsiteLoginActivity.progress.visibility = View.VISIBLE
                    lastPageUrl = url
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    this@WebsiteLoginActivity.progress.visibility = View.GONE
                    lastPageUrl = url
                    completeIfCookiePresent(url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame != true) return
                    this@WebsiteLoginActivity.progress.visibility = View.GONE
                    Toast.makeText(
                        this@WebsiteLoginActivity,
                        getString(R.string.website_login_load_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            prepareCookies(loginUrl)
            loadUrl(loginUrl)
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finishWithCurrentCookies()
            }
        }
        logger.info("AUTH", "打开网站登录页: ${Wenku8Hosts.secureMainSiteUrl(loginUrl)}")
    }

    private fun buildToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(8), dp(10))
        setBackgroundColor(Color.rgb(250, 247, 246))
        addView(
            TextView(this@WebsiteLoginActivity).apply {
                text = getString(R.string.website_login_title)
                textSize = 19f
                setTextColor(Color.rgb(40, 35, 35))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            Button(this@WebsiteLoginActivity).apply {
                text = getString(R.string.website_login_close)
                setOnClickListener { finishWithCurrentCookies() }
            }
        )
    }

    /**
     * 保留 cf_clearance 等风控 Cookie，但移除内置共享账号的旧 jieqi* 会话；
     * 否则用户还没输入自己的账号，WebView 就会继承内置账号并立即被判为登录成功。
     */
    private fun prepareCookies(url: String?) {
        val secure = url?.let(Wenku8Hosts::secureMainSiteUrl) ?: return
        val httpUrl = runCatching { secure.toHttpUrl() }.getOrNull() ?: return
        val sessionNames = CookieManager.getInstance().getCookie(secure)
            .orEmpty()
            .split(";")
            .map { it.substringBefore('=').trim() }
            .filter { it.startsWith("jieqi", ignoreCase = true) }
            .toMutableSet()
            .apply {
                addAll(cookieStore.loadForRequest(httpUrl).map { it.name }
                    .filter { it.startsWith("jieqi", ignoreCase = true) })
                addAll(listOf("jieqiUserInfo", "jieqiUserPass", "jieqiUserName"))
            }
        sessionNames.forEach { name ->
            @Suppress("DEPRECATION")
            CookieManager.getInstance().setCookie(
                secure,
                "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
            )
        }
        cookieStore.loadForRequest(httpUrl).forEach { cookie ->
            if (cookie.name.startsWith("jieqi", ignoreCase = true)) return@forEach
            @Suppress("DEPRECATION")
            CookieManager.getInstance().setCookie(
                secure,
                "${cookie.name}=${cookie.value}; Path=/",
            )
        }
        CookieManager.getInstance().flush()
    }

    private fun completeIfCookiePresent(url: String?) {
        val secure = url?.let(Wenku8Hosts::secureMainSiteUrl) ?: return
        val cookies = CookieManager.getInstance().getCookie(secure).orEmpty()
        if (!cookies.contains("jieqiUserInfo=") || deliveredResult) return
        cookieStore.saveRaw(secure.toHttpUrl(), cookies)
        CookieManager.getInstance().flush()
        webView.evaluateJavascript(EXTRACT_USERNAME_JS) { value ->
            val username = runCatching {
                JSONTokener(value).nextValue() as? String
            }.getOrNull().orEmpty().trim()
            deliverSuccess(username)
        }
    }

    private fun finishWithCurrentCookies() {
        val secure = lastPageUrl?.let(Wenku8Hosts::secureMainSiteUrl)
        val cookies = secure?.let { CookieManager.getInstance().getCookie(it) }.orEmpty()
        if (cookies.contains("jieqiUserInfo=")) {
            cookieStore.saveRaw(secure!!.toHttpUrl(), cookies)
            CookieManager.getInstance().flush()
            deliverSuccess("")
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun deliverSuccess(username: String) {
        if (deliveredResult) return
        deliveredResult = true
        logger.info("AUTH", "网站登录 Cookie 已保存")
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_USERNAME, username.takeIf { it.length <= 40 }.orEmpty()),
        )
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.clearHistory() }
            runCatching { webView.destroy() }
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

}
