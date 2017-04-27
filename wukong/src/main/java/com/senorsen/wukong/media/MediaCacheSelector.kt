package com.senorsen.wukong.media

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Environment
import android.util.Log
import com.senorsen.wukong.model.Song
import java.io.File

class MediaCacheSelector {

    private val mediaDirectoryPrefixes = arrayOf(
            "/netease/cloudmusic/Music",
            "/qqmusic/song").map { Environment.getExternalStorageDirectory().absolutePath + it }

    private fun allPossibleFiles(song: Song): List<String> {
        return mediaDirectoryPrefixes.map { path ->
            arrayOf(".flac", " [mqms2].flac", ".mp3", " [mqms2].mp3").map { "$path/${song.artist?.replace(" / ", " ")} - ${song.title}$it" }
        }.flatMap { it }
    }

    fun getValidMedia(song: Song): String? {
        return allPossibleFiles(song).find {
            Log.d(TAG, "check file: $it, ${File(it).exists()}")
            File(it).exists()
        }
    }
}
