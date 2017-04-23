package com.senorsen.wukong.model

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.gson.Gson

// A RequestSong is an object which contains identified song and credentials, which is supposed to be sent to the server.
data class RequestSong(
        var siteId: String?,
        var songId: String?,

        // The `withCookie` contains a cookie string, which helps music providers to fetch more data,
        // based on user identity of the music site.
        var withCookie: String?
)

// A Song is an object contains all the data of a song.
class Song {
    var siteId: String? = null
    var songId: String? = null
    var artist: String? = null
    var album: String? = null
    lateinit var artwork: File
    var title: String? = null
    lateinit var lyrics: List<Lyric>
    var webUrl: String? = null
    var mvId: String? = null
    var mvWebUrl: String? = null

    fun Song() {}

    class Deserializer : ResponseDeserializable<Song> {
        override fun deserialize(content: String) = Gson().fromJson(content, Song::class.java)
    }
}

// A File is an object which contains uri and other required metadata.
class File {
    var file: String? = null
    var fileViaCdn: String? = null
    var format: String? = null
    var audioQuality: String? = null
    var audioBitrate: Int? = null

    class Deserializer : ResponseDeserializable<File> {
        override fun deserialize(content: String) = Gson().fromJson(content, File::class.java)
    }
}

// A lovely lyric.
class Lyric {
    var lrc: Boolean? = null
    var translated: Boolean? = null
    var data: String? = null

    class Deserializer : ResponseDeserializable<Lyric> {
        override fun deserialize(content: String) = Gson().fromJson(content, Lyric::class.java)
    }
}