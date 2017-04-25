package com.senorsen.wukong.network

import android.content.Context

class AppCookies(private val context: Context) {

    fun readCookiesFromSharedPreferences(): String {
        return context.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("cookies", "")
    }

}