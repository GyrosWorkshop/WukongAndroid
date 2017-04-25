package com.senorsen.wukong

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.senorsen.wukong.network.ApiUrls


class WebViewActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webview)

        webView = findViewById(R.id.webview) as WebView
        webView.settings.javaScriptEnabled = true
        CookieManager.getInstance().removeAllCookies(null)
        webView.loadUrl("${ApiUrls.oAuthRedirectEndpoint}/GitHub")

        webView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith(ApiUrls.base)) {
                    CookieManager.getInstance().setCookie(url, "")
                }
                view.loadUrl(url)
                return false
            }

            override fun onPageFinished(view: WebView?, url: String) {
                if (url.startsWith(ApiUrls.base)) {
                    val cookies = CookieManager.getInstance().getCookie(url)
                    Log.d(TAG, "All the cookies of $url: $cookies")
                    val sharedPref = applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    val editor = sharedPref.edit()
                    editor.putString("cookies", cookies.split(';').map(String::trim).filterNot(String::isNullOrBlank).joinToString("\n"))
                    editor.apply()
                }
            }
        })

    }

}