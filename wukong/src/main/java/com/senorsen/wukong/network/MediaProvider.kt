package com.senorsen.wukong.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

object MediaProvider {

    private val TAG = javaClass.simpleName

    val client = OkHttpClient()

    fun resolveRedirect(url: String): String {
        if (!url.startsWith("http")) return url
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
                throw HttpWrapper.InvalidResponseException(response)
        }
    }

}