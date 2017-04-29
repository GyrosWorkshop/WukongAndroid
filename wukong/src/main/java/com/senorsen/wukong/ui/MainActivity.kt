package com.senorsen.wukong.ui

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.LinearLayout
import com.senorsen.wukong.R
import com.senorsen.wukong.network.SongList


class MainActivity : AppCompatActivity(){

    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mDrawerList: LinearLayout
    private lateinit var mDrawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        mDrawerLayout = findViewById(R.id.main) as DrawerLayout
        mDrawerList = findViewById(R.id.left_drawer) as LinearLayout

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        mDrawerToggle = object : ActionBarDrawerToggle(
                this, /* host Activity */
                mDrawerLayout, /* DrawerLayout object */
                toolbar, /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open, /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
        ) {
            override fun onDrawerClosed(view: View?) {
                invalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
            }

            override fun onDrawerOpened(drawerView: View?) {
                invalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
            }
        }
        mDrawerLayout.addDrawerListener(mDrawerToggle)

        findViewById(R.id.drawer_settings).setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            val currentFragment = fragmentManager.findFragmentByTag("SETTINGS")
            if (currentFragment == null || !currentFragment.isVisible) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment, SettingsFragment(), "SETTINGS")
                        .addToBackStack("tag")
                        .commit()
            }
        }

        findViewById(R.id.drawer_sync_playlist).setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            val currentFragment = fragmentManager.findFragmentByTag("MAIN")
            if (currentFragment != null) {
                val fragment = currentFragment as MainFragment
                val childFragment = fragment.childFragmentManager.findFragmentByTag("SONGLIST")
                if (childFragment != null) {
                    val songListFragment = childFragment as SongListFragment
                    songListFragment.fetchSongList()
                }
            }
        }

        findViewById(R.id.drawer_shuffle_playlist).setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            val currentFragment = fragmentManager.findFragmentByTag("MAIN")
            if (currentFragment != null) {
                val fragment = currentFragment as MainFragment
                val childFragment = fragment.childFragmentManager.findFragmentByTag("SONGLIST")
                if (childFragment != null) {
                    val songListFragment = childFragment as SongListFragment
                    songListFragment.shuffleSongList()
                }
            }
        }

        findViewById(R.id.drawer_clear_playlist).setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            val currentFragment = fragmentManager.findFragmentByTag("MAIN")
            if (currentFragment != null) {
                val fragment = currentFragment as MainFragment
                val childFragment = fragment.childFragmentManager.findFragmentByTag("SONGLIST")
                if (childFragment != null) {
                    val songListFragment = childFragment as SongListFragment
                    songListFragment.clearSongList()
                }
            }
        }

        fragmentManager.beginTransaction().add(R.id.fragment, MainFragment(), "MAIN").commit()

        mayRequestPermission()
    }

    private fun mayRequestPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean(KEY_PREF_USE_LOCAL_MEDIA, true).apply()
            return true
        }
        if (shouldShowRequestPermissionRationale(WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(this).setMessage(R.string.permission_request_description)
                    .setTitle(R.string.permission_request)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean(KEY_PREF_USE_LOCAL_MEDIA, false).apply()
                    }
                    .show()
        } else {
            requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
        }
        return false
    }

    companion object {
        val REQUEST_WRITE_EXTERNAL_STORAGE = 0
        val KEY_PREF_USE_LOCAL_MEDIA = "pref_useLocalMedia"
    }
}
