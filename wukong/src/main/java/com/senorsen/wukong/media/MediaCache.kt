package com.senorsen.wukong.media

import android.content.ContentValues
import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import com.senorsen.wukong.network.MediaProviderClient
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.ref.WeakReference

class MediaCache(private val context: WeakReference<Context>) {

    private val TAG = javaClass.simpleName

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
        val maxCacheSize = PreferenceManager.getDefaultSharedPreferences(context.get()).getString(KEY_PREF_MAX_MEDIA_CACHE_SIZE, "2").toLong() * 1024 * 1024 * 1024
        Log.d(TAG, "maxCacheSize: $maxCacheSize")
        mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, maxCacheSize)
        mDiskCacheStarting = false
    }

    private fun getDiskCacheDir(uniqueName: String): File {
        val cachePath = context.get()?.externalCacheDir?.path
        return File(cachePath + File.separator + uniqueName)
    }

    fun getMediaFromDiskCache(key: String): FileInputStream? {
        synchronized(mDiskCacheLock) {
            // Wait while disk cache is started from background thread
            while (mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            val stream = (mDiskLruCache.get(key)?.getInputStream(MEDIA_INDEX)) ?: return null
            Log.d(TAG, "disk cache hit $key")
            return stream as FileInputStream
        }
    }

    fun addMediaToCache(key: String, url: String) {
        if (!url.startsWith("http")) return
        if (getMediaFromDiskCache(key) != null) {
            Log.d(TAG, "media cache $key already exists, skip add")
            return
        }

        var ex: Exception? = null

        Log.d(TAG, "try to preload $key $url")
        Log.d(TAG, "try to preload start $key $url")
        var retryCount = 2
        var success = false
        while (retryCount > 0 && !success) {
            val editor = mDiskLruCache.edit(key)
            try {
                editor.newOutputStream(MEDIA_INDEX).use { out ->
                    val bytes = MediaProviderClient.getMedia(url)
                    out.write(bytes)
                    editor.commit()
                    mDiskLruCache.flush()
                }
                ex = null
                success = true
            } catch (e: IOException) {
                e.printStackTrace()
                ex = e
                retryCount--
                Thread.sleep(5000)
                editor.abort()
            }
        }
        if (ex != null) throw ex as Exception
        Log.d(TAG, "write to disk cache $key")
    }

    companion object {
        private val DISK_CACHE_SUBDIR = "media"
        private val KEY_PREF_MAX_MEDIA_CACHE_SIZE = "pref_maxMediaCacheSize"
        private val MEDIA_INDEX = 0
    }
}
