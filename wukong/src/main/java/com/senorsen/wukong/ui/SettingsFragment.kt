package com.senorsen.wukong.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceFragment
import android.util.Log
import android.widget.Toast
import com.senorsen.wukong.R
import com.senorsen.wukong.model.Configuration
import com.senorsen.wukong.network.HttpClient
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val TAG = javaClass.simpleName

    val handler = Handler()
    val http: HttpClient
        get() = (activity as WukongActivity).http

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key in arrayOf(KEY_PREF_COOKIES, KEY_PREF_SYNC_PLAYLISTS)) {
            val configuration = Configuration(
                    cookies = sharedPreferences.getString(KEY_PREF_COOKIES, ""),
                    syncPlaylists = sharedPreferences.getString(KEY_PREF_SYNC_PLAYLISTS, ""))
            thread {
                try {
                    Log.d(TAG, "onSharedPreferenceChanged uploadConfiguration")
                    http.uploadConfiguration(configuration)
                } catch (e: HttpClient.UserUnauthorizedException) {
                    handler.post {
                        Toast.makeText(activity, "Cannot upload configuration: not sign in", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    handler.post {
                        Toast.makeText(activity, "Cannot upload configuration: $e", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        if (key == KEY_PREF_MAX_MEDIA_CACHE_SIZE) {
            Toast.makeText(activity, "Re-entering channel is needed after apply new cache setting.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val KEY_PREF_COOKIES = "pref_cookies"
        private val KEY_PREF_SYNC_PLAYLISTS = "pref_syncPlaylists"
        private val KEY_PREF_MAX_MEDIA_CACHE_SIZE = "pref_maxMediaCacheSize"
    }
}
