package com.senorsen.wukong.model

// A RequestSong is an object which contains identified song and credentials, which is supposed to be sent to the server.
class RequestSong(
        var siteId: String?,
        var songId: String?,

        // The `withCookie` contains a cookie string, which helps music providers to fetch more data,
        // based on user identity of the music site.
        var withCookie: String?
)

// A Song is an object contains all the data of a song.
class Song(
        var siteId: String?,
        var songId: String?,
        var artist: String?,
        var album: String?,
        var artwork: File,
        var title: String?,
        var lyrics: List<Lyric>,
        var webUrl: String?,
        var mvId: String?,
        var mvWebUrl: String?
)

// A File is an object which contains uri and other required metadata.
class File(
        var file: String?,
        var fileViaCdn: String?,
        var format: String?,
        var audioQuality: String?,
        var audioBitrate: Int
)

// A lovely lyric.
class Lyric(
        var lrc: Boolean,
        var translated: Boolean,
        var data: String?
)