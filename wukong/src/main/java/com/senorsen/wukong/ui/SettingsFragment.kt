package com.senorsen.wukong.ui

import android.app.ActivityManager
import android.app.Fragment
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceFragment
import android.util.Log
import android.widget.Toast
import com.senorsen.wukong.R
import com.senorsen.wukong.model.Configuration
import com.senorsen.wukong.network.HttpWrapper
import com.senorsen.wukong.service.WukongService

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [SettingsFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [SettingsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    val handler = Handler()
    var connected = false
    var wukongService: WukongService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)
    }

    // FIXME: reuse module
    val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
            wukongService = null
            bindService()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected = true
            val wukongService = (service as WukongService.WukongServiceBinder).getService()
            this@SettingsFragment.wukongService = wukongService
        }
    }

    val bindRunnable = object : Runnable {
        override fun run() {
            if (isServiceStarted()) activity.bindService(Intent(activity, WukongService::class.java), serviceConnection, 0)
            else {
                handler.postDelayed(this, 1000)
                Log.d(SettingsFragment::class.simpleName, "delayed")
            }
        }
    }

    // Workaround for type recursive.
    fun bindService() {
        bindRunnable.run()
    }

    fun isServiceStarted(): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className.contains(WukongService::class.simpleName!!) }
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        handler.removeCallbacks(bindRunnable)
        if (connected) activity.unbindService(serviceConnection)
        super.onStop()
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
            try {
                wukongService?.uploadConfiguration(configuration) ?:
                        Toast.makeText(activity, "Cannot upload configuration: not connected?", Toast.LENGTH_LONG).show()
            } catch (e: HttpWrapper.UserUnauthorizedException) {
                Toast.makeText(activity, "Cannot upload configuration: not sign in", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(activity, "Cannot upload configuration: $e", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private val KEY_PREF_COOKIES = "pref_cookies"
        private val KEY_PREF_SYNC_PLAYLISTS = "pref_syncPlaylists"
    }
}
