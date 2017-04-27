package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.util.Log
import com.google.gson.Gson
import com.senorsen.wukong.BuildConfig
import com.senorsen.wukong.R
import com.senorsen.wukong.model.RequestSong
import com.senorsen.wukong.model.User
import okhttp3.CookieJar
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.URLEncoder


class HttpWrapper(private val cookies: String) {

    val userAgent = "WukongAndroid/" + BuildConfig.VERSION_NAME

    class UserUnauthorizedException : Exception {
        constructor(e: Exception) : super(e)
        constructor(message: String) : super(message)
        constructor(message: String, e: Exception) : super(e)
    }

    private val JSON = MediaType.parse("application/json; charset=utf-8")

    val client = OkHttpClient()

    fun getUserInfo(): User {
        val ret = get(ApiUrls.userInfoEndpoint)
        Log.d(TAG, "Return body: " + ret)
        return Gson().fromJson(ret, User::class.java)
    }

    fun channelJoin(channelId: String) {
        post(ApiUrls.channelJoinEndpoint + "/" + urlEncode(channelId))
    }

    fun downvote(song: RequestSong) {
        post(ApiUrls.channelDownvoteUri, Gson().toJson(song))
    }

    fun reportFinish(song: RequestSong) {
        post(ApiUrls.channelReportFinishedEndpoint, Gson().toJson(song))
    }

    private fun get(url: String): String {
        val request = Request.Builder()
                .header("Cookie", cookies)
                .header("User-Agent", userAgent)
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

    private fun post(url: String, json: String = "{}"): String {
        val body = RequestBody.create(JSON, json)
        Log.d(TAG, "")
        val request = Request.Builder()
                .header("Cookie", cookies)
                .header("User-Agent", userAgent)
                .url(url)
                .post(body).build()
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

    private fun urlEncode(segment: String): String {
        return URLEncoder.encode(segment, "utf-8")
    }
}
