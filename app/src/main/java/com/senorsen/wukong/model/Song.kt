package com.senorsen.wukong.model

object SongQuality {
    val LOSSLESS: String
        get() = "lossless"

    val HIGH: String
        get() = "high"

    val LOW: String
        get() = "low"

    val MEDIUM: String
        get() = "medium"
}

// A RequestSong is an object which contains identified song and credentials, which is supposed to be sent to the server.
data class RequestSong(
        var siteId: String?,
        var songId: String?,

        // The `withCookie` contains a cookie string, which helps music providers to fetch more data,
        // based on user identity of the music site.
        var withCookie: String?
)

// A Song is an object contains all the data of a song.
data class Song(
        var siteId: String? = null,
        var songId: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var artwork: File? = null,
        var title: String? = null,
        var lyrics: List<Lyric>? = null,
        var webUrl: String? = null,
        var mvId: String? = null,
        var mvWebUrl: String? = null,
        var musics: List<File>? = null
) {
    fun toRequestSong(): RequestSong {
        return RequestSong(siteId = siteId, songId = songId, withCookie = null)
    }
}

// A File is an object which contains uri and other required metadata.
data class File(
        var file: String? = null,
        var fileViaCdn: String? = null,
        var format: String? = null,
        var audioQuality: String? = null,
        var audioBitrate: Int? = null
)

// A lovely lyric.
class Lyric(
        var lrc: Boolean? = null,
        var translated: Boolean? = null,
        var data: String? = null
)