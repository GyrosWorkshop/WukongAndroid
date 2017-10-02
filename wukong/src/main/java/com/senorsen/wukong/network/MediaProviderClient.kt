package com.senorsen.wukong.network

import android.util.Log
import com.google.common.net.HttpHeaders
import okhttp3.OkHttpClient
import okhttp3.Request

object MediaProviderClient {

    private val TAG = javaClass.simpleName

    private val client = OkHttpClient()

    fun resolveRedirect(url: String): String {
        if (!url.startsWith("http")) return url
        val request = Request.Builder()
                .head()
                .header(HttpHeaders.USER_AGENT, "")
                .url(url).build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful && response.code() == 200 -> {
                    Log.d(TAG, response.toString())
                    return response.request().url().toString()
                }
                else ->
                    throw HttpClient.InvalidResponseException(response)
            }
        }
    }

    fun getMedia(url: String): ByteArray {
        val request = Request.Builder()
                .header(HttpHeaders.USER_AGENT, "")
                .url(url).build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful ->
                    return response.body()!!.bytes()
                else ->
                    throw HttpClient.InvalidResponseException(response)
            }
        }
    }

}
