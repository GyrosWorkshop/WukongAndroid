package com.senorsen.wukong.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.webkit.*
import com.senorsen.wukong.R
import com.senorsen.wukong.network.ApiUrls
import com.senorsen.wukong.network.HttpWrapper
import kotlin.concurrent.thread


class WebViewActivity : AppCompatActivity() {

    private val TAG = javaClass.simpleName

    private lateinit var webView: WebView

    private val handler = Handler()

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        title = "Sign In"
        setSupportActionBar(toolbar)

        webView = findViewById(R.id.webview) as WebView
        webView.settings.javaScriptEnabled = true
        CookieManager.getInstance().removeAllCookies(null)
        thread {
            HttpWrapper("")
        }.join()
        webView.loadUrl(ApiUrls.oAuthEndpoint)

        var loggedIn = false

        webView.setWebViewClient(object : WebViewClient() {

            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith(ApiUrls.base)) {
                    CookieManager.getInstance().setCookie(url, "")
                }
                view.loadUrl(url)
                return false
            }

            override fun onPageFinished(view: WebView?, url: String) {
                if (url == ApiUrls.base + "/" && !loggedIn) {
                    val cookies = CookieManager.getInstance().getCookie(url)
                    Log.d(TAG, "All the cookies of $url: $cookies")
                    if (cookies.isNotBlank()) {
                        loggedIn = true

                        handler.post {
                            webView.destroy()
                            setResult(Activity.RESULT_OK, Intent().putExtra("cookies", cookies))
                            finish()
                        }
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError) {
                Log.d(TAG, error.toString())
                super.onReceivedError(view, request, error)
            }

        })

    }

}