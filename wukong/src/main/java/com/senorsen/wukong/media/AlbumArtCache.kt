package com.senorsen.wukong.media

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Environment
import android.os.Environment.isExternalStorageRemovable
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.senorsen.wukong.utils.BitmapHelper
import java.io.*
import java.io.File.separator


/**
 * Implements a basic cache of album arts, with async loading support.
 */
class AlbumArtCache(private val context: Context) {

    private val TAG = javaClass.simpleName

    private lateinit var mDiskLruCache: DiskLruCache
    private var mDiskCacheStarting = false
    private val mDiskCacheLock = Object()
    private val mMemoryCache: LruCache<String, Array<Bitmap>>

    init {
        val cacheDir = getDiskCacheDir(DISK_CACHE_SUBDIR)
        initDiskCache(cacheDir)
        Log.i(TAG, "cacheDir: $cacheDir")
        val memCacheSize = (Runtime.getRuntime().maxMemory() / 10).toInt()
        Log.i(TAG, "memCacheSize=$memCacheSize")
        mMemoryCache = object : LruCache<String, Array<Bitmap>>(memCacheSize) {
            override fun sizeOf(key: String, value: Array<Bitmap>): Int {
                return value[BIG_BITMAP_INDEX].byteCount + value[ICON_BITMAP_INDEX].byteCount
            }
        }
    }

    // Creates a unique subdirectory of the designated app cache directory. Tries to use external
    // but if not mounted, falls back on internal storage.
    fun getDiskCacheDir(uniqueName: String): File {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        val cachePath = if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !isExternalStorageRemovable())
            context.externalCacheDir.path
        else
            context.cacheDir.path

        return File(cachePath + separator + uniqueName)
    }

    private fun initDiskCache(cacheDir: File) {
        mDiskLruCache = DiskLruCache.open(cacheDir, 1, 2, MAX_ALBUM_ART_CACHE_SIZE)
        mDiskCacheStarting = false // Finished initialization
    }

    fun getBitmapFromDiskCache(key: String): Array<Bitmap>? {
        synchronized(mDiskCacheLock) {
            // Wait while disk cache is started from background thread
            while (mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait()
                } catch (e: InterruptedException) {
                }

            }
            val snapshot = mDiskLruCache.get(key) ?: return null
            Log.d(TAG, "disk cache hit $key")
            return arrayOf(BIG_BITMAP_INDEX, ICON_BITMAP_INDEX).map {
                val inputStream = snapshot.getInputStream(it)
                readInputStreamToBitmap(inputStream)
            }.toTypedArray()
        }
    }

    private fun readInputStreamToBitmap(inputStream: InputStream): Bitmap {
        val fd = (inputStream as FileInputStream).fd
        return ImageResizer.decodeSampledBitmapFromDescriptor(
                fd, Integer.MAX_VALUE, Integer.MAX_VALUE)
    }

    fun addBitmapToCache(key: String, bitmaps: Array<Bitmap>) {
        // Add to memory cache as before
        addBitmapToMemoryCache(key, bitmaps)

        // Also add to disk cache
        synchronized(mDiskCacheLock) {
            val editor = mDiskLruCache.edit(key)
            val outs = arrayOf(BIG_BITMAP_INDEX, ICON_BITMAP_INDEX).map {
                val out = editor.newOutputStream(it)
                writeOutputStreamFromBitmap(bitmaps[it], out)
                out
            }
            editor.commit()
            outs.forEach { it.close() }
        }
        Log.d(TAG, "write to disk cache $key")
    }

    private fun writeOutputStreamFromBitmap(bitmap: Bitmap, out: OutputStream) {
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
    }

    private fun getBitmapFromCache(key: String): Array<Bitmap>? {
        return getBitmapFromMemCache(key) ?: getBitmapFromDiskCache(key)
    }

    private fun addBitmapToMemoryCache(key: String, bitmaps: Array<Bitmap>) {
        Log.d(TAG, "put memory cache $key ${bitmaps.map { it.byteCount }.joinToString()}")
        mMemoryCache.put(key, bitmaps)
    }

    private fun getBitmapFromMemCache(key: String): Array<Bitmap>? {
        return mMemoryCache.get(key)
    }

    fun getBigImage(artUrl: String, key: String = artUrl): Bitmap? {
        val result = getBitmapFromCache(key)
        return if (result == null) null else result[BIG_BITMAP_INDEX]
    }

    fun getIconImage(artUrl: String, key: String = artUrl): Bitmap? {
        val result = getBitmapFromCache(key)
        return if (result == null) null else result[ICON_BITMAP_INDEX]
    }

    fun fetch(artUrl: String, listener: FetchListener?, key: String = artUrl.hashCode().toString()) {
        // WARNING: for the sake of simplicity, simultaneous multi-workThread fetch requests
        // are not handled properly: they may cause redundant costly operations, like HTTP
        // requests and bitmap rescales. For production-level apps, we recommend you use
        // a proper image loading library, like Glide.
        val bitmap = getBitmapFromCache(key)
        if (bitmap != null) {
            Log.d(TAG, "getOrFetch: album art $key is in cache, using it $artUrl")
            listener?.onFetched(artUrl, bitmap[BIG_BITMAP_INDEX], bitmap[ICON_BITMAP_INDEX])
            return
        }
        Log.d(TAG, "getOrFetch: starting asynctask to fetch " + artUrl)

        object : AsyncTask<Void, Void, Array<Bitmap>>() {
            override fun doInBackground(objects: Array<Void>): Array<Bitmap>? {
                val bitmaps: Array<Bitmap>
                try {
                    val bitmap = BitmapHelper.fetchAndRescaleBitmap(artUrl,
                            MAX_ART_WIDTH, MAX_ART_HEIGHT)
                    val icon = BitmapHelper.scaleBitmap(bitmap,
                            MAX_ART_WIDTH_ICON, MAX_ART_HEIGHT_ICON)
                    bitmaps = arrayOf<Bitmap>(bitmap, icon)
                    addBitmapToCache(key, bitmaps)
                } catch (e: IOException) {
                    e.printStackTrace()
                    return null
                }

                Log.d(TAG, "doInBackground: putting bitmap in cache. mem cache size=" + mMemoryCache.size())
                return bitmaps
            }

            override fun onPostExecute(bitmaps: Array<Bitmap>?) {
                if (bitmaps == null) {
                    listener?.onError(artUrl, IllegalArgumentException("got null bitmaps"))
                } else {
                    listener?.onFetched(artUrl,
                            bitmaps[BIG_BITMAP_INDEX], bitmaps[ICON_BITMAP_INDEX])
                }
            }
        }.execute()
    }

    abstract class FetchListener {
        private val TAG = javaClass.simpleName
        abstract fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap)
        fun onError(artUrl: String, e: Exception) {
            Log.e(TAG, "AlbumArtFetchListener: error while downloading " + artUrl)
            e.printStackTrace()
        }
    }

    companion object {

        private val DISK_CACHE_SUBDIR = "music_artwork"
        private val MAX_ALBUM_ART_CACHE_SIZE: Long = 50 * 1024 * 1024  // 50 MB
        private val MAX_ART_WIDTH = 1000  // pixels
        private val MAX_ART_HEIGHT = 1000  // pixels

        // Resolution reasonable for carrying around as an icon (generally in
        // MediaDescription.getIconBitmap). This should not be bigger than necessary, because
        // the MediaDescription object should be lightweight. If you set it too high and try to
        // serialize the MediaDescription, you may get FAILED BINDER TRANSACTION errors.
        private val MAX_ART_WIDTH_ICON = 128  // pixels
        private val MAX_ART_HEIGHT_ICON = 128  // pixels

        private val BIG_BITMAP_INDEX = 0
        private val ICON_BITMAP_INDEX = 1

        fun stringToBitMap(encodedString: String): Bitmap? {
            try {
                val encodeByte = Base64.decode(encodedString, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(encodeByte, 0,
                        encodeByte.size)
                return bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun bitMapToString(bitmap: Bitmap?): String {
            if (bitmap == null) return ""

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val b = baos.toByteArray()
            val temp = Base64.encodeToString(b, Base64.DEFAULT)
            return temp
        }

    }
}