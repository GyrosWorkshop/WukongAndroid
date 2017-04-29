package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.util.Log
import com.google.gson.Gson
import com.senorsen.wukong.BuildConfig
import com.senorsen.wukong.R
import com.senorsen.wukong.model.RequestSong
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.model.User
import okhttp3.*
import java.net.URLEncoder


class HttpWrapper(private val cookies: String) {

    val userAgent = "WukongAndroid/" + BuildConfig.VERSION_NAME

    class UserUnauthorizedException : Exception {
        constructor(e: Exception) : super(e)
        constructor(message: String) : super(message)
        constructor(message: String, e: Exception) : super(e)
    }

    class InvalidRequestException : Exception {
        constructor(e: Exception) : super(e)
        constructor(message: String) : super(message)
        constructor(message: String, e: Exception) : super(e)
    }

    class InvalidResponseException : Exception {
        constructor(response: Response) : super("Invalid response: success=${response.isSuccessful}, code=${response.code()}")
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

    fun updateNextSong(song: RequestSong? = null) {
        post(ApiUrls.channelUpdateNextSongEndpoint, Gson().toJson(song))
    }

    fun getConfiguration(): Configuration? {
        val ret = get(ApiUrls.userConfigurationUri)
        if (ret.isEmpty())
            return null
        else
            return Gson().fromJson(ret, Configuration::class.java)
    }

    fun getSongListWithUrl(url: String, cookies: String?): SongList {
        val ret = post(ApiUrls.providerSongListWithUrlEndpoint, Gson().toJson(SongListWithUrlRequest(url, cookies)))
        return Gson().fromJson(ret, SongList::class.java)
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

            response.code() == 400 ->
                throw InvalidRequestException(response.body().string())

            else ->
                throw InvalidResponseException(response)
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

            response.code() == 400 ->
                throw InvalidRequestException(response.body().string())

            else ->
                throw InvalidResponseException(response)
        }
    }

    private fun urlEncode(segment: String): String {
        return URLEncoder.encode(segment, "utf-8")
    }
}
