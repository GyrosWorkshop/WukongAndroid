package com.senorsen.wukong.model

import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import java.io.Serializable

interface SongIdentifier {
    var siteId: String?
    var songId: String?
}

// A RequestSong is an object which contains identified song and credentials, which is supposed to be sent to the server.
data class RequestSong(
        override var siteId: String?,
        override var songId: String?,

        // The `withCookie` contains a cookie string, which helps music providers to fetch more data,
        // based on user identity of the music site.
        var withCookie: String?
) : SongIdentifier {
    override fun toString(): String {
        return "$siteId.$songId"
    }
}

fun String.toRequestSong(withCookie: String? = null)
        = RequestSong(this.split('.')[0], this.split('.')[1], withCookie)

// A Song is an object contains all the data of a song.
data class Song(
        override var siteId: String? = null,
        override var songId: String? = null,
        var artist: String? = null,
        var album: String? = null,
        var artwork: File? = null,
        var title: String? = null,
        var lyrics: List<Lyric>? = null,
        var webUrl: String? = null,
        var mvId: String? = null,
        var mvWebUrl: String? = null,
        var musics: List<File>? = null
) : SongIdentifier, Serializable {
    fun toRequestSong(): RequestSong {
        return RequestSong(siteId = siteId, songId = songId, withCookie = null)
    }

    fun toRequestSong(withCookie: String?): RequestSong {
        return RequestSong(siteId = siteId, songId = songId, withCookie = withCookie)
    }

    val songKey: String
        get() = "$siteId.$songId"

    fun toMediaMetaData(bitmap: Bitmap?): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
        return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "$artist - $album")
                .build()
    }
}

// A File is an object which contains uri and other required metadata.
data class File(
        var unavailable: Boolean? = null,
        var file: String? = null,
        var fileViaCdn: String? = null,
        var format: String? = null,
        var audioQuality: String? = null,
        var audioBitrate: Int? = null
): Serializable

// A lovely lyric.
class Lyric(
        var lrc: Boolean? = null,
        var translated: Boolean? = null,
        var data: String? = null
): Serializable