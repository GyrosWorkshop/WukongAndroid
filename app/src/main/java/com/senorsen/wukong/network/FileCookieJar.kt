package com.senorsen.wukong.network

import android.content.Context
import android.os.Environment
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class FileCookieJar : CookieJar {

    lateinit var context: Context

    override fun saveFromResponse(url: HttpUrl?, cookies: MutableList<Cookie>?) {
        // Do nothing.
    }

    override fun loadForRequest(url: HttpUrl?): MutableList<Cookie> {
        val cookies = readCookieLineFromFile(context).map {
            val line = it.split('=')
            Cookie.Builder()
                    .name(line[0])
                    .value(line[1])
                    .build()
        }
        return cookies as MutableList<Cookie>
    }
}

fun readCookieLineFromFile(context: Context): List<String> {
    val cookieLine = ArrayList<String>()
    val file = File(context.filesDir, "wukong-cookies.txt")

    try {
        val br = BufferedReader(FileReader(file))
        var line: String? = br.readLine()
        while (line != null) {
            cookieLine.add(line)
            line = br.readLine()
        }
        br.close()
    } catch (e: IOException) {
        // Create file first.
    }

    return cookieLine
}