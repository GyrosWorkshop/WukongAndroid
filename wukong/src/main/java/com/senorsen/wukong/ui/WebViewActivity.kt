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
import com.senorsen.wukong.network.HttpClient
import kotlin.concurrent.thread


class WebViewActivity : AppCompatActivity() {

    private val TAG = javaClass.simpleName

    private lateinit var webView: WebView

    private val handler = Handler()

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        title = "Sign In"
        setSupportActionBar(toolbar)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        CookieManager.getInstance().removeAllCookies(null)
        thread {
            HttpClient()
        }.join()
        webView.loadUrl(ApiUrls.oAuthEndpoint)

        var loggedIn = false

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
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

        }

    }

}