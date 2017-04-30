package com.senorsen.wukong.store

import android.content.Context
import com.senorsen.wukong.model.Configuration

class ConfigurationLocalStore(context: Context) : PrefLocalStore(context) {

    fun save(configuration: Configuration?) {
        if (configuration != null) {
            pref.edit()
                    .putString(KEY_PREF_COOKIES, configuration.cookies)
                    .putString(KEY_PREF_SYNC_PLAYLISTS, configuration.syncPlaylists)
                    .apply()
        }
    }

    fun load(): Configuration {
        return Configuration(
                cookies = pref.getString(KEY_PREF_COOKIES, ""),
                syncPlaylists = pref.getString(KEY_PREF_SYNC_PLAYLISTS, ""))
    }

    companion object {
        private val KEY_PREF_COOKIES = "pref_cookies"
        private val KEY_PREF_SYNC_PLAYLISTS = "pref_syncPlaylists"
    }

}