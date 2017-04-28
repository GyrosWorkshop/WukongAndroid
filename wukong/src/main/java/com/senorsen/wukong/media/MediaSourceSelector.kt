package com.senorsen.wukong.media

import android.content.ContentValues.TAG
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.senorsen.wukong.R
import com.senorsen.wukong.model.File

class MediaSourceSelector(context: Context) {

    private val KEY_PREF_USE_CDN = "pref_useCdn"
    private val KEY_PREF_QUALITY_DATA = "pref_preferAudioQualityData"

    private var useCdn: Boolean = false
    private var preferAudioQualityData: String = "high"

    private val qualities = arrayOf("lossless", "high", "medium", "low")

    private val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private fun pullSettings() {
        useCdn = sharedPref.getBoolean(KEY_PREF_USE_CDN, useCdn)
        preferAudioQualityData = sharedPref.getString(KEY_PREF_QUALITY_DATA, preferAudioQualityData)
        Log.i(TAG, "useCdn=$useCdn, preferAudioQualityData=$preferAudioQualityData")
    }

    fun selectMediaUrlByCdnSettings(file: File, pullSettings: Boolean = true): String? {
        if (pullSettings) pullSettings()
        return if (file.fileViaCdn == null || !useCdn)
            file.file
        else
            file.fileViaCdn
    }

    fun selectFromMultipleMediaFiles(files: List<File>): Pair<File, String> {
        pullSettings()
        val defaultQualityIndex = qualities.indexOf(preferAudioQualityData)
        val originalFiles = files.sortedByDescending(File::audioBitrate)
        val file = originalFiles.filter { qualities.indexOf(it.audioQuality) >= defaultQualityIndex }.firstOrNull() ?: originalFiles.first()
        return Pair(file, selectMediaUrlByCdnSettings(file, false)!!)
    }
}
