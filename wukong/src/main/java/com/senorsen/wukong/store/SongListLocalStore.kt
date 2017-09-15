package com.senorsen.wukong.store

import android.content.Context
import android.util.Log
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.utils.ObjectSerializer

@Suppress("UNCHECKED_CAST")
class SongListLocalStore(context: Context) : PrefLocalStore(context, PREF_NAME) {
    private val TAG = javaClass.simpleName

    fun save(songList: List<Song>?) {
        save(KEY_PREF_SONGLIST, ObjectSerializer.serialize(songList?.toTypedArray()))
    }

    fun load(): List<Song>? {
        return try {
            (ObjectSerializer.deserialize(load(KEY_PREF_SONGLIST)) as Array<Song>?)?.toList()
        } catch (e: Exception) {
            Log.e(TAG, "load song list error: ", e)
            null
        }
    }

    companion object {
        private val PREF_NAME = "wukong"
        private val KEY_PREF_SONGLIST = "song_list"
    }

}