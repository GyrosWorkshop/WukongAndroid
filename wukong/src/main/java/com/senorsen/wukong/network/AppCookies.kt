package com.senorsen.wukong.network

import android.content.Context
import java.lang.ref.WeakReference

class AppCookies(private val context: WeakReference<Context>) {

    fun readCookiesFromSharedPreferences(): String {
        return context.get()?.getSharedPreferences("wukong", Context.MODE_PRIVATE)?.getString("cookies", "") ?: ""
    }

}