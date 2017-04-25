package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.util.Log
import com.google.gson.Gson
import com.senorsen.wukong.model.User
import okhttp3.CookieJar
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody


class HttpWrapper(private val cookies: String) {

    class UserUnauthorizedException : Exception {
        constructor(e: Exception) : super(e)
        constructor(message: String) : super(message)
        constructor(message: String, e: Exception) : super(e)
    }

    private val JSON = MediaType.parse("application/json; charset=utf-8")

    var client = OkHttpClient()

    fun getUserInfo(): User {
        val ret = get(ApiUrls.userInfoEndpoint)
        Log.d(TAG, "Return body: " + ret)
        return Gson().fromJson(ret, User::class.java)
    }

    private fun get(url: String): String {
        val request = Request.Builder()
                .addHeader("Cookie", cookies)
                .url(url).build()
        val response = client.newCall(request).execute()
        when {
            response.isSuccessful ->
                return response.body().string()

            response.code() == 401 ->
                throw UserUnauthorizedException(response.body().string())

            else ->
                throw IllegalStateException()
        }
    }

    private fun post(url: String, json: String): String {
        val body = RequestBody.create(JSON, json)
        val request = Request.Builder()
                .url(url)
                .post(body).build()
        val response = client.newCall(request).execute()
        return response.body().string()
    }
}
