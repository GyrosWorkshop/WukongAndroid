package com.senorsen.wukong.ui

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GravityCompat
import android.support.v4.view.MenuItemCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.InputType
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.senorsen.wukong.R
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.model.User
import com.senorsen.wukong.network.HttpClient
import com.senorsen.wukong.service.WukongService
import com.senorsen.wukong.store.LastMessageLocalStore
import com.senorsen.wukong.store.UserInfoLocalStore
import me.zhengken.lyricview.LyricView
import java.util.*
import kotlin.concurrent.thread


class WukongActivity : AppCompatActivity() {

    private val TAG = javaClass.simpleName

    private lateinit var navigationView: NavigationView
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mDrawerView: NavigationView
    private lateinit var mDrawerToggle: ActionBarDrawerToggle
    private lateinit var headerLayout: View

    lateinit var http: HttpClient

    val handler = Handler()
    var connected = false
    var wukongService: WukongService? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    private lateinit var userInfoLocalStore: UserInfoLocalStore

    val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
            wukongService = null
            updateChannelInfo(WukongService.ConnectStatus.DISCONNECTED, null, null)
            bindService()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected = true
            val wukongService = (service as WukongService.WukongServiceBinder).getService()
            this@WukongActivity.wukongService = wukongService
            if (wukongService.connected) {
                pullChannelInfo()
            } else {
                onServiceStopped()
            }
        }
    }

    fun pullChannelInfo() {
        if (wukongService != null) {
            updateUserTextAndAvatar(userInfoLocalStore.load(), userInfoLocalStore.loadUserAvatar())
            updateChannelInfo(wukongService!!.connectStatus, wukongService!!.userList, wukongService!!.currentPlayUserId)
            setLyric(wukongService!!.currentSong?.lyrics?.find { it.lrc == true && it.translated != true && !it.data.isNullOrBlank() }?.data)
            updateSongInfo(wukongService!!.currentSong, wukongService!!.isPaused)
            updateAlbumArtwork(wukongService!!.getSongArtwork(wukongService!!.currentSong, null, false))
        }
    }

    val bindRunnable = object : Runnable {
        override fun run() {
            if (isServiceStarted()) bindService(Intent(this@WukongActivity, WukongService::class.java), serviceConnection, 0)
            else {
                handler.postDelayed(this, 1000)
                Log.d(WukongActivity::class.simpleName, "delayed")
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
        updateChannelInfo()
        bindService()
        if (mTimer == null) {
            mTimer = Timer()
            mLrcTask = LrcTask()
            mTimer!!.scheduleAtFixedRate(mLrcTask, 1000, 500)
        }
    }

    override fun onStop() {
        handler.removeCallbacks(bindRunnable)
        if (connected) unbindService(serviceConnection)
        mTimer?.cancel()
        mTimer = null
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
                val username = "xxx"
                Snackbar.make(findViewById(R.id.mainCoordinatorLayout), "Logged in as $username.", Snackbar.LENGTH_SHORT).show()

                val sharedPref = getSharedPreferences("wukong", Context.MODE_PRIVATE)
                sharedPref.edit().putString("cookies", cookies).apply()
                startWukongService()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        mDrawerLayout = findViewById(R.id.main)
        mDrawerView = findViewById(R.id.left_drawer)

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

        mDrawerToggle = object : ActionBarDrawerToggle(
                this, /* host Activity */
                mDrawerLayout, /* DrawerLayout object */
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
        mDrawerToggle.isDrawerIndicatorEnabled = true
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        navigationView = findViewById(R.id.left_drawer)

        navigationView.setNavigationItemSelectedListener { item ->
            mDrawerLayout.closeDrawer(GravityCompat.START)
            onOptionsItemSelected(item)
        }

        headerLayout = navigationView.inflateHeaderView(R.layout.nav_header)
        headerLayout.findViewById<RelativeLayout>(R.id.drawer_user).setOnClickListener {
            mDrawerLayout.closeDrawer(GravityCompat.START)
            startActivityForResult(Intent(this, WebViewActivity::class.java), REQUEST_COOKIES)
        }

        userInfoLocalStore = UserInfoLocalStore(this)
        updateUserTextAndAvatar(userInfoLocalStore.load(), userInfoLocalStore.loadUserAvatar())
        updateChannelText()
        fragmentManager.beginTransaction().replace(R.id.fragment, MainFragment(), "MAIN").commit()

        mayRequestPermission()
        thread {
            http = HttpClient(getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    .getString("cookies", ""))
            try {
                val lastMessageLocalStore = LastMessageLocalStore(this)
                val last = lastMessageLocalStore.load()
                val messages = http.getMessage(last, userInfoLocalStore.load()?.userName)
                Log.d(TAG, "fetch message gt $last")
                if (messages.isNotEmpty()) {
                    handler.post {
                        fun showNext(index: Int) {
                            if (index >= messages.count()) return
                            val message = messages[index]
                            AlertDialog.Builder(this)
                                    .setTitle("Message ${index + 1}/${messages.count()}")
                                    .setMessage(message.message + "\n\n" + message.createdAt.replace(Regex("T|Z|\\.000"), " "))
                                    .setPositiveButton(if (index < messages.count() - 1) "Next" else "Ok") { _, _ ->
                                        lastMessageLocalStore.save(message.messageId)
                                        showNext(index + 1)
                                    }
                                    .show()
                        }
                        showNext(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetch online message error", e)
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val search = menu.findItem(R.id.search)
        val searchView = MenuItemCompat.getActionView(search) as SearchView
        search(searchView)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected $item")
        when (item.itemId) {
            android.R.id.home ->
                mDrawerLayout.openDrawer(GravityCompat.START)

            R.id.nav_channel ->
                showChannelDialog()

            R.id.nav_settings -> {
                val settingsFragment = getSettingsFragment()
                if (settingsFragment == null || !settingsFragment.isVisible) {
                    fragmentManager.beginTransaction()
                            .hide(getMainFragment())
                            .replace(R.id.fragment, SettingsFragment(), "SETTINGS")
                            .addToBackStack("tag")
                            .commit()
                }
            }

            R.id.nav_start_service ->
                startWukongService()

            R.id.nav_stop_service ->
                stopService(Intent(this, WukongService::class.java))

            R.id.nav_sync_playlist -> {
                getSongListFragment()?.fetchSongList()
            }

            R.id.nav_clear_playlist -> {
                getSongListFragment()?.clearSongList()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getMainFragment() =
            fragmentManager.findFragmentByTag("MAIN") as MainFragment?

    private fun getSettingsFragment() =
            fragmentManager.findFragmentByTag("SETTINGS") as SettingsFragment?

    private fun getLyricView() =
            findViewById<LyricView>(R.id.custom_lyric_view)

    private fun getSongListFragment() =
            getMainFragment()?.childFragmentManager?.findFragmentByTag("SONGLIST") as SongListFragment?


    private fun search(searchView: SearchView) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                Log.i(TAG, "search $newText")
                getSongListFragment()?.adapter?.filter?.filter(newText)
                return true
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (broadcastReceiver == null) broadcastReceiver = ChannelUpdateBroadcastReceiver()
        val intentFilter = IntentFilter(UPDATE_CHANNEL_INFO_INTENT)
        intentFilter.addAction(UPDATE_SONG_INFO_INTENT)
        intentFilter.addAction(UPDATE_SONG_ARTWORK_INTENT)
        intentFilter.addAction(UPDATE_SONG_LIST_INTENT)
        intentFilter.addAction(UPDATE_USER_INFO_INTENT)
        intentFilter.addAction(DISCONNECT_INTENT)
        intentFilter.addAction(SERVICE_STOPPED_INTENT)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        if (broadcastReceiver != null) LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    @Suppress("UNCHECKED_CAST")
    inner class ChannelUpdateBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UPDATE_CHANNEL_INFO_INTENT -> {
                    updateChannelInfo(intent.getSerializableExtra("connectStatus") as WukongService.ConnectStatus,
                            intent.getSerializableExtra("users") as List<User>?,
                            intent.getStringExtra("currentPlayUserId"))
                }

                UPDATE_SONG_INFO_INTENT -> {
                    val song = intent.getSerializableExtra("song") as Song?
                    val isPaused = intent.getBooleanExtra("isPaused", false)
                    setLyric(song?.lyrics?.find { it.lrc == true && it.translated != true && !it.data.isNullOrBlank() }?.data)
                    updateSongInfo(song, isPaused)
                    updateAlbumArtwork(wukongService?.getSongArtwork(song, null, false))
                }


                UPDATE_SONG_ARTWORK_INTENT ->
                    updateAlbumArtwork(intent.getParcelableExtra<Bitmap>("artwork"))

                UPDATE_SONG_LIST_INTENT ->
                    updateSongList()

                UPDATE_USER_INFO_INTENT ->
                    updateUserTextAndAvatar(intent.getSerializableExtra("user") as User?, intent.getParcelableExtra<Bitmap?>("avatar"))

                DISCONNECT_INTENT -> {
                    stopService(Intent(this@WukongActivity, WukongService::class.java))
                    val cause = intent.getStringExtra("cause")
                    AlertDialog.Builder(this@WukongActivity).setMessage(getString(R.string.disconnect_notification, cause))
                            .setTitle(R.string.disconnected)
                            .setPositiveButton(R.string.reconnect) { _, _ -> startWukongService() }
                            .setNegativeButton(R.string.exit) { _, _ -> }
                            .show()
                }

                SERVICE_STOPPED_INTENT ->
                    onServiceStopped()
            }
        }
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

    fun updateChannelInfo(connectStatus: WukongService.ConnectStatus = WukongService.ConnectStatus.DISCONNECTED, users: List<User>? = null, currentPlayUserId: String? = null) {
        Log.d(TAG, "updateChannelInfo $connected, $currentPlayUserId")
        val channelId = currentChannel()
        val connectStatusText = when (connectStatus) {
            WukongService.ConnectStatus.DISCONNECTED -> resources.getString(R.string.disconnected)
            WukongService.ConnectStatus.CONNECTING -> resources.getString(R.string.connecting)
            WukongService.ConnectStatus.RECONNECTING -> resources.getString(R.string.reconnecting)
            WukongService.ConnectStatus.CONNECTED -> ""
        } + " #$channelId "
        Log.d(TAG, connectStatusText)
        (findViewById<TextView>(R.id.channel_info))?.text = if (users != null)
            Html.fromHtml(connectStatusText + String.format(resources.getQuantityString(R.plurals.numberOfPlayers, users.size), users.size)
                    + ": " + users.joinToString {
                val escapedName = Html.escapeHtml(it.displayName ?: it.userName)
                if (it.id == currentPlayUserId) "<b>$escapedName</b>" else escapedName
            })
        else
            Html.fromHtml(connectStatusText)
    }

    fun updateSongList() {
        val currentFragment = fragmentManager.findFragmentByTag("MAIN")
        if (currentFragment != null) {
            val fragment = currentFragment as MainFragment
            val childFragment = fragment.childFragmentManager.findFragmentByTag("SONGLIST")
            if (childFragment != null) {
                val songListFragment = childFragment as SongListFragment
                songListFragment.updateSongListFromService()
            }
        }
    }

    fun updateUserTextAndAvatar(user: User?, avatar: Bitmap?) {
        handler.post {
            Log.d(TAG, "$user, $avatar")
            if (user?.userName != null) {
                (headerLayout.findViewById<TextView>(R.id.text_drawer_user))?.text = user.userName
            }
            if (avatar != null) {
                (headerLayout.findViewById<ImageView>(R.id.icon_drawer_user))?.setImageBitmap(avatar)
            }
        }
    }

    fun onServiceStopped() {
        updateChannelInfo()
        handler.post {
            updateAlbumArtwork(wukongArtwork)
            findViewById<TextView>(R.id.song_footer_line_one)?.setText(R.string.app_name)
            findViewById<TextView>(R.id.song_footer_line_two)?.setText(R.string.flows_from_heaven_to_the_soul)
            findViewById<View>(R.id.button_area)?.visibility = View.INVISIBLE
        }
    }

    fun updateSongInfo(song: Song?, isPaused: Boolean) {
        handler.post {
            if (song != null) {
                findViewById<TextView>(R.id.song_footer_line_one)?.setText(song.title)
                if (!withLyric) findViewById<TextView>(R.id.song_footer_line_two)?.setText(song.artist ?: "")
                findViewById<View>(R.id.button_area)?.visibility = View.VISIBLE
                findViewById<ImageView>(R.id.play_switch_button)?.setImageResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause)
            } else {
                findViewById<View>(R.id.button_area)?.visibility = View.INVISIBLE
            }
        }
    }

    private val defaultArtwork = R.mipmap.ic_default_art
    private val wukongArtwork = R.mipmap.ic_launcher

    fun updateAlbumArtwork(resourceId: Int) {
        handler.post {
            findViewById<ImageView>(R.id.artwork_thumbnail)?.setImageResource(resourceId)
        }
    }

    fun updateAlbumArtwork(bitmap: Bitmap?) {
        if (bitmap == null)
            updateAlbumArtwork(defaultArtwork)
        else
            handler.post {
                Log.d(TAG, "updateAlbumArtwork " + bitmap.toString())
                findViewById<ImageView>(R.id.artwork_thumbnail)?.setImageBitmap(bitmap)
            }
    }

    private fun updateChannelText() {
        handler.post {
            val channelId = currentChannel()
            if (channelId.isNotBlank()) {
                (findViewById<NavigationView>(R.id.left_drawer))?.menu?.findItem(R.id.nav_channel)?.title = Html.fromHtml(String.format(resources.getString(R.string.channel_id), channelId))
            }
        }
    }

    private fun currentChannel() = getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", "")

    private var withLyric = false

    private fun setLyric(lyric: String?) {
        val lyricView = getLyricView() ?: return
        if (lyric == null) {
            lyricView.reset()
            return
        }
        lyricView.setLyric(lyric)
    }

    private var mTimer: Timer? = null
    private var mLrcTask: LrcTask? = null

    private inner class LrcTask : TimerTask() {
        override fun run() {
            try {
                updateLyric(wukongService?.timePassed)
            } catch (e: Exception) {
            }
        }
    }

    private fun updateLyric(time: Long?) {
        if (time == null) return
        handler.post {
            val lyricView = getLyricView() ?: return@post
            lyricView.setCurrentTimeMillis(time)
            val current = lyricView.currentLine
            if (current != null && time >= 3000) {
                withLyric = true
                findViewById<TextView>(R.id.song_footer_line_two)?.setText(current)
            } else {
                withLyric = false
            }
        }
    }

    private fun showChannelDialog() {
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

    private fun startWukongService() {
        val pref = getSharedPreferences("wukong", Context.MODE_PRIVATE)
        val cookies = pref.getString("cookies", "")
        val channel = pref.getString("channel", "")

        if (channel.isBlank())
            return showChannelDialog()

        stopService(Intent(this, WukongService::class.java))

        val serviceIntent = Intent(this, WukongService::class.java)
                .putExtra("cookies", cookies)
                .putExtra("channel", channel)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startService(serviceIntent)
        } else {
            startForegroundService(serviceIntent)
        }
    }

    companion object {
        val REQUEST_WRITE_EXTERNAL_STORAGE = 0
        val KEY_PREF_USE_LOCAL_MEDIA = "pref_useLocalMedia"
        val UPDATE_CHANNEL_INFO_INTENT = "UPDATE_CHANNEL_INFO"
        val UPDATE_SONG_INFO_INTENT = "UPDATE_SONG_INFO"
        val UPDATE_SONG_ARTWORK_INTENT = "UPDATE_SONG_ARTWORK"
        val UPDATE_USER_INFO_INTENT = "UPDATE_USER_INFO"
        val UPDATE_SONG_LIST_INTENT = "UPDATE_SONG_LIST"
        val DISCONNECT_INTENT = "DISCONNECT"
        val SERVICE_STOPPED_INTENT = "SERVICE_STOPPED"
    }
}
