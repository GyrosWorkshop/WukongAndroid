package com.senorsen.wukong.store

import android.content.Context
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.utils.ObjectSerializer

class SongListLocalStore(context: Context) : PrefLocalStore(context, PREF_NAME) {

    fun save(songList: List<Song>?) {
        pref.edit()
                .putString(KEY_PREF_SONGLIST, ObjectSerializer.serialize(songList?.toTypedArray()))
                .apply()

    }

    fun load(): List<Song>? {
        @Suppress("UNCHECKED_CAST")
        return (ObjectSerializer.deserialize(pref.getString(KEY_PREF_SONGLIST, "")) as Array<Song>?)?.toMutableList()
    }

    companion object {
        private val PREF_NAME = "wukong"
        private val KEY_PREF_SONGLIST = "song_list"
    }

}