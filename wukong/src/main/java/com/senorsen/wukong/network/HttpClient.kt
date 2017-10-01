package com.senorsen.wukong.network

import android.util.Log
import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.senorsen.wukong.BuildConfig
import com.senorsen.wukong.model.*
import com.senorsen.wukong.network.message.SongList
import com.senorsen.wukong.network.message.SongListWithUrlRequest
import okhttp3.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


class HttpClient(var cookies: String = "") {

    private val TAG = javaClass.simpleName

    val userAgent = "WukongAndroid/${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    private val JSON = MediaType.parse("application/json; charset=utf-8")

    private val client = OkHttpClient.Builder()
            .readTimeout(15, TimeUnit.SECONDS).build()!!

    class UserUnauthorizedException : Exception {
        constructor(e: Exception) : super(e)
        constructor(message: String?) : super(message)
        constructor(message: String?, e: Exception) : super(e)
    }

    class InvalidRequestException : Exception {
        constructor(e: Exception) : super(e)
        constructor(message: String?) : super(message)
        constructor(message: String?, e: Exception) : super(e)
    }

    class InvalidResponseException : Exception {
        constructor(response: Response) : super("Invalid response: success=${response.isSuccessful}, code=${response.code()}, body=${response.body()?.string()}")
        constructor(e: Exception) : super(e)
        constructor(message: String?) : super(message)
        constructor(message: String?, e: Exception) : super(e)
    }

    init {
        try {
            ApiUrls.base = fetchBaseUrl("WukongApi")
            ApiUrls.providerEndpoint = fetchBaseUrl("WukongProvider")
        } catch (e: Exception) {
            Log.e(TAG, "fetchApiBaseUrl failed, will use the default one")
        }
    }

    private fun fetchBaseUrl(key: String): String {
        val ret = get(ApiUrls.dynamicBaseUrl(key))
        val detail = Gson().fromJson(ret, ApiBaseUrlDetail::class.java)
        detail.linkUrl.trimEnd('/')
        detail.apply {
            Log.i(TAG, "$key url: $linkUrl, updated at $updatedAt")
        }
        return detail.linkUrl
    }

    inner class ApiBaseUrlDetail(val linkUrl: String,
                                 val updatedAt: String)

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

    fun uploadConfiguration(configuration: Configuration) {
        post(ApiUrls.userConfigurationUri, Gson().toJson(configuration))
    }

    fun getSongListWithUrl(url: String, cookies: String?): SongList {
        val ret = post(ApiUrls.providerSongListWithUrlEndpoint, Gson().toJson(SongListWithUrlRequest(url, cookies)))
        return Gson().fromJson(ret, SongList::class.java)
    }

    fun getSongLists(urls: String, cookies: String?): List<SongList> {
        val songLists = urls.split('\n').map(String::trim).filter(String::isNotBlank).map { url ->
            try {
                val songList = getSongListWithUrl(url, cookies)
                Log.i(TAG, "${songList.songs.size}, ${songList.name}, ${songList.creator?.name} of $url")
                songList
            } catch (e: Exception) {
                Log.e(HttpClient::class.simpleName, "getSongLists")
                e.printStackTrace()
                null
            }
        }
        return songLists.filterNotNull()
    }

    fun getMessage(lastId: Long, user: String?): List<Message> {
        val ret = get(ApiUrls.messageApiUrl + "?last=$lastId&user=$user", false)
        return Gson().fromJson(ret, object : TypeToken<List<Message>>() {}.type)
    }

    private fun request(url: String, sendCookie: Boolean = true, body: String? = null): String {
        val builder = Request.Builder()
                .header(HttpHeaders.COOKIE, if (sendCookie) cookies else "")
                .header(HttpHeaders.USER_AGENT, userAgent)
                .url(url)
        if (body != null)
            builder.post(RequestBody.create(JSON, body))
        client.newCall(builder.build()).execute().use { response ->
            when {
                response.isSuccessful ->
                    return response.body()!!.string()

                response.code() == 401 ->
                    throw UserUnauthorizedException("Unauthorized: " + response.body()!!.string())

                response.code() == 400 ->
                    throw InvalidRequestException("Invalid request: " + response.body()!!.string())

                else ->
                    throw InvalidResponseException(response)
            }
        }
    }

    private fun post(url: String, body: String = "{}")
        = request(url, true, body)
    
    private fun get(url: String, sendCookie: Boolean = true)
        = request(url, sendCookie)

    private fun urlEncode(segment: String): String {
        return URLEncoder.encode(segment, "utf-8")
    }
}
