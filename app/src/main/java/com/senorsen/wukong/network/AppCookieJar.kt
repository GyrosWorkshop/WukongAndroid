package com.senorsen.wukong.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class AppCookieJar(private val context: Context) : CookieJar {

    override fun saveFromResponse(url: HttpUrl?, cookies: MutableList<Cookie>?) {
        // Do nothing.
    }

    override fun loadForRequest(url: HttpUrl?): MutableList<Cookie> {
        val cookies = readCookieLineFromSharedPreferences().map {
            val line = it.split('=')
            Cookie.Builder()
                    .name(line[0])
                    .value(line[1])
                    .build()
        }
        return cookies as MutableList<Cookie>
    }

    fun readCookieLineFromSharedPreferences(): List<String> {
        return context.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("cookies", "").split('\n')
    }

}