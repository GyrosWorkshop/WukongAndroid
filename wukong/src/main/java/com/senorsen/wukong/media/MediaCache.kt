package com.senorsen.wukong.media

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import java.io.File

class MediaCache(private val context: Context) {
    private lateinit var mDiskLruCache: DiskLruCache
    private var mDiskCacheStarting = false
    private val mDiskCacheLock = Object()
    private var enableCache = true

    init {
        val cacheDir = getDiskCacheDir(DISK_CACHE_SUBDIR)
        initDiskCache(cacheDir)
        Log.i(ContentValues.TAG, "cacheDir: $cacheDir")
    }

    private fun initDiskCache(cacheDir: File) {
        val maxCacheSize = PreferenceManager.getDefaultSharedPreferences(context).getLong(KEY_PREF_MAX_MEDIA_CACHE_SIZE, 1 * 1024 * 1024)
        Log.d(TAG, "maxCacheSize: $maxCacheSize")
        mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, maxCacheSize)
        mDiskCacheStarting = false
    }

    fun getDiskCacheDir(uniqueName: String): File {
        val cachePath = context.externalCacheDir.path
        return File(cachePath + File.separator + uniqueName)
    }

    companion object {
        private val DISK_CACHE_SUBDIR = "media"
        private val KEY_PREF_MAX_MEDIA_CACHE_SIZE = "pref_maxMediaCacheSize"
    }
}
