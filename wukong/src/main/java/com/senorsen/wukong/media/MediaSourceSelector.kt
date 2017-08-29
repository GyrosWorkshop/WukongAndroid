package com.senorsen.wukong.media

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import com.senorsen.wukong.model.File
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.ui.MainActivity

class MediaSourceSelector(private val context: Context) {

    private val TAG = javaClass.simpleName

    private val KEY_PREF_USE_CDN = "pref_useCdn"
    private val KEY_PREF_USE_LOCAL_MEDIA = "pref_useLocalMedia"
    private val KEY_PREF_QUALITY_DATA = "pref_preferAudioQualityData"

    private var useCdn: Boolean = false
    private var useLocalMedia: Boolean = true
    private var preferAudioQualityData: String = "high"

    private val qualities = arrayOf("lossless", "high", "medium", "low")

    private val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private fun pullSettings() {
        useCdn = sharedPref.getBoolean(KEY_PREF_USE_CDN, useCdn)
        useLocalMedia = sharedPref.getBoolean(KEY_PREF_USE_LOCAL_MEDIA, useLocalMedia)
        preferAudioQualityData = sharedPref.getString(KEY_PREF_QUALITY_DATA, preferAudioQualityData)
        Log.i(TAG, "useCdn=$useCdn, useLocalMedia=$useLocalMedia, preferAudioQualityData=$preferAudioQualityData")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            useLocalMedia = false
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(MainActivity.KEY_PREF_USE_LOCAL_MEDIA, false).apply()
        }
    }

    fun selectMediaUrlByCdnSettings(files: List<File>, pullSettings: Boolean = true): List<String> {
        if (pullSettings) pullSettings()
        return files.map { file ->
            if (file.fileViaCdn == null || !useCdn)
                file.file
            else
                file.fileViaCdn
        }.filterNotNull()
    }

    fun selectFromMultipleMediaFiles(song: Song, acceptUnavailable: Boolean = true): Pair<List<File>, List<String>> {
        pullSettings()
        val defaultQualityIndex = qualities.indexOf(preferAudioQualityData)
        val files = if (acceptUnavailable) song.musics else song.musics?.filterNot { it.unavailable == true}
        val originalFiles = files?.sortedByDescending(File::audioBitrate) ?:
                return Pair(listOf(), listOf())

        val resultFiles = originalFiles.filter { qualities.indexOf(it.audioQuality) >= defaultQualityIndex }.toMutableList()
        resultFiles.addAll(originalFiles.filter { qualities.indexOf(it.audioQuality) < defaultQualityIndex })
        val urls = selectMediaUrlByCdnSettings(resultFiles, false).toMutableList()
        val localFile = getValidLocalMedia(song)
        if (localFile != null) {
            urls.add(0, localFile)
        }
        return Pair(resultFiles, urls)
    }

    private val mediaDirectoryPrefixes = arrayOf(
            "/netease/cloudmusic/Music",
            "/qqmusic/song").map { Environment.getExternalStorageDirectory().absolutePath + it }

    private fun allPossibleFiles(song: Song): List<String> {
        return mediaDirectoryPrefixes.map { path ->
            arrayOf(".flac", " [mqms2].flac", ".mp3", " [mqms2].mp3").map { "$path/${song.artist?.replace(" / ", " ")} - ${song.title}$it" }
        }.flatMap { it }
    }

    fun getValidLocalMedia(song: Song): String? {
        pullSettings()
        if (!useLocalMedia) return null
        return allPossibleFiles(song).find {
            Log.d(TAG, "check file: $it, ${java.io.File(it).exists()}")
            java.io.File(it).exists()
        }
    }
}
