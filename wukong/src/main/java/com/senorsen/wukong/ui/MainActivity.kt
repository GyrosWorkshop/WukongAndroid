package com.senorsen.wukong.ui

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.senorsen.wukong.R
import com.senorsen.wukong.network.SongList
import com.senorsen.wukong.service.WukongService
import android.os.Handler
import android.os.IBinder
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.Log
import android.widget.*
import com.senorsen.wukong.store.SongListLocalStore
import com.senorsen.wukong.store.UserInfoLocalStore
import com.senorsen.wukong.utils.ObjectSerializer


class MainActivity : AppCompatActivity() {

    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mDrawerList: LinearLayout
    private lateinit var mDrawerToggle: ActionBarDrawerToggle

    val handler = Handler()
    var connected = false
    var wukongService: WukongService? = null

    private lateinit var userInfoLocalStore: UserInfoLocalStore

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
            this@MainActivity.wukongService = wukongService
            if (wukongService.connected) {
                wukongService.registerUpdateUserInfo(this@MainActivity::updateUserTextAndAvatar)
            }
        }
    }

    val bindRunnable = object : Runnable {
        override fun run() {
            if (isServiceStarted()) bindService(Intent(this@MainActivity, WukongService::class.java), serviceConnection, 0)
            else {
                handler.postDelayed(this, 1000)
                Log.d(MainActivity::class.simpleName, "delayed")
            }
        }
    }

    // Workaround for type recursive.
    fun bindService() {
        bindRunnable.run()
    }

    fun isServiceStarted(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className.contains(WukongService::class.simpleName!!) }
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        wukongService?.registerUpdateUserInfo(null)
        handler.removeCallbacks(bindRunnable)
        if (connected) unbindService(serviceConnection)
        super.onStop()
    }

    private val REQUEST_COOKIES = 0

    var cookies: String? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        when (requestCode) {
            REQUEST_COOKIES -> {
                cookies = data.getStringExtra("cookies")
                Toast.makeText(this, "Sign in successfully.", Toast.LENGTH_SHORT).show()

                val sharedPref = getSharedPreferences("wukong", Context.MODE_PRIVATE)
                sharedPref.edit().putString("cookies", cookies).apply()
                startWukongService()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        userInfoLocalStore = UserInfoLocalStore(this)

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

        findViewById(R.id.drawer_user).setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            startActivityForResult(Intent(this, WebViewActivity::class.java), REQUEST_COOKIES)
        }
        updateUserTextAndAvatar()

        val drawerChannel = findViewById(R.id.drawer_channel)
        drawerChannel.setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            showChannelDialog()
        }
        updateChannelText()

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

        fragmentManager.beginTransaction().replace(R.id.fragment, MainFragment(), "MAIN").commit()

        mayRequestPermission()

        AppUpdater(this)
                .setButtonDoNotShowAgain("")
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("GyrosWorkshop", "WukongAndroid")
                .start()
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

    fun updateUserTextAndAvatar() {
        val user = userInfoLocalStore.load()
        val userAvatar = userInfoLocalStore.loadUserAvatar()
        if (user?.userName != null) {
            (findViewById(R.id.text_drawer_user) as TextView).text = user.userName
        }
        if (userAvatar != null) {
            (findViewById(R.id.icon_drawer_user) as ImageView).background = BitmapDrawable(userAvatar)
        }
    }

    fun updateChannelText() {
        val channelId = getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", "")
        if (channelId.isNotBlank()) {
            (findViewById(R.id.text_drawer_channel) as TextView).text = "Channel: $channelId"
        }
    }

    fun showChannelDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Channel")
        var channel: String = getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", "")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.text = SpannableStringBuilder(channel)
        builder.setView(input)

        builder.setPositiveButton("OK") { _, _ ->
            channel = input.text.toString()
            getSharedPreferences("wukong", Context.MODE_PRIVATE).edit().putString("channel", channel).apply()
            updateChannelText()
            startWukongService()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    fun startWukongService() {
        val pref = getSharedPreferences("wukong", Context.MODE_PRIVATE)
        val cookies = pref.getString("cookies", "")
        val channel = pref.getString("channel", "")

        if (channel.isBlank())
            return showChannelDialog()

        stopService(Intent(this, WukongService::class.java))

        val serviceIntent = Intent(this, WukongService::class.java)
                .putExtra("cookies", cookies)
                .putExtra("channel", channel)
        startService(serviceIntent)
    }

    companion object {
        val REQUEST_WRITE_EXTERNAL_STORAGE = 0
        val KEY_PREF_USE_LOCAL_MEDIA = "pref_useLocalMedia"
    }
}
