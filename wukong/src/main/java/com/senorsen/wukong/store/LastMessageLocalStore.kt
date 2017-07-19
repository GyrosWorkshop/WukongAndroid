package com.senorsen.wukong.store

import android.content.Context

class LastMessageLocalStore(context: Context) : PrefLocalStore(context, PREF_NAME) {

    fun save(id: Long) {
        save(KEY_PREF_LAST_MESSAGE_ID, id.toString())
    }

    fun load(): Long {
        return load(KEY_PREF_LAST_MESSAGE_ID, "0").toLong()
    }

    companion object {
        private val PREF_NAME = "wukong"
        private val KEY_PREF_LAST_MESSAGE_ID = "last_message_id"
    }

}
