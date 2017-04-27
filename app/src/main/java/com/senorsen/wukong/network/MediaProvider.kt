package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class MediaProvider {

    val client = OkHttpClient()

    fun resolveRedirect(url: String): String {
        val request = Request.Builder()
                .head()
                .url(url).build()
        val response = client.newCall(request).execute()
        when {
            response.isSuccessful && response.code() == 200 -> {
                Log.d(TAG, response.toString())
                return response.request().url().toString()
            }
            else ->
                throw IllegalStateException()
        }
    }
}