package com.senorsen.wukong.media

import android.util.Log
import android.media.MediaPlayer
import com.senorsen.wukong.network.MediaProvider
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object MediaSourcePreparer {

    private val TAG = javaClass.simpleName

    fun setMediaSource(mp: MediaPlayer, source: String, elapsed: Float = 0f): String {
        val resolvedMediaSource = MediaProvider.resolveRedirect(source)
        mp.reset()
        mp.setDataSource(resolvedMediaSource)
        mp.prepare()
        mp.seekTo((elapsed * 1000).toInt())
        return resolvedMediaSource
    }

    fun setMediaSource(mp: MediaPlayer, source: FileInputStream, elapsed: Float = 0f): FileInputStream {
        mp.reset()
        mp.setDataSource(source.fd)
        mp.prepare()
        mp.seekTo((elapsed * 1000).toInt())
        return source
    }

    fun setMediaSources(mp: MediaPlayer, sources: List<String>, elapsed: Float = 0f): String {
        var mediaSourceIndex = 0
        var succeed = false
        var lastException: Exception? = null
        while (!succeed && mediaSourceIndex < sources.size) {
            val mediaSource = sources[mediaSourceIndex]
            Log.i(TAG, "try media: $mediaSource")
            try {
                val resolvedMediaSource = setMediaSource(mp, mediaSource, elapsed)
                Log.i(TAG, "resolved media: $resolvedMediaSource")
                succeed = true
                return resolvedMediaSource
            } catch (e: Exception) {
                Log.e(TAG, "setMediaSources")
                e.printStackTrace()
                succeed = false
                mediaSourceIndex++
                lastException = e
            }
        }
        if (lastException != null)
            throw lastException
        else
            throw Exception("setMediaSources: list empty")
    }

}
