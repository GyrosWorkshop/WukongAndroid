package com.senorsen.wukong.store

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.senorsen.wukong.model.Song

@Suppress("UNCHECKED_CAST")
class SongListLocalStore(context: Context) : PrefLocalStore(context, PREF_NAME) {

    fun save(songList: List<Song>?) {
        saveToJson(KEY_PREF_SONGLIST, songList)
    }

    fun load(): List<Song>? {
        return loadFromJson(KEY_PREF_SONGLIST, object : TypeToken<List<Song>?>() {}.type)
    }

    companion object {
        private val PREF_NAME = "wukong"
        private val KEY_PREF_SONGLIST = "song_list"
    }

}