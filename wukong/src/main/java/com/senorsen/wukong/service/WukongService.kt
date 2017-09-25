package com.senorsen.wukong.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.*
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import com.google.common.base.Charsets
import com.senorsen.wukong.R
import com.senorsen.wukong.media.AlbumArtCache
import com.senorsen.wukong.media.MediaCache
import com.senorsen.wukong.media.MediaSourcePreparer
import com.senorsen.wukong.media.MediaSourceSelector
import com.senorsen.wukong.model.*
import com.senorsen.wukong.network.ApiUrls
import com.senorsen.wukong.network.HttpClient
import com.senorsen.wukong.network.SocketClient
import com.senorsen.wukong.network.message.Protocol
import com.senorsen.wukong.network.message.SongList
import com.senorsen.wukong.network.message.WebSocketReceiveProtocol
import com.senorsen.wukong.store.ConfigurationLocalStore
import com.senorsen.wukong.store.SongListLocalStore
import com.senorsen.wukong.store.UserInfoLocalStore
import com.senorsen.wukong.ui.WukongActivity
import com.senorsen.wukong.utils.Debounce
import com.senorsen.wukong.utils.ResourceHelper
import java.io.IOException
import java.io.Serializable
import java.lang.System.currentTimeMillis
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.concurrent.thread


class WukongService : Service() {

    private val TAG = javaClass.simpleName

    enum class ConnectStatus {
        DISCONNECTED, CONNECTING, RECONNECTING, CONNECTED
    }

    var connectStatus: ConnectStatus = ConnectStatus.DISCONNECTED
        get() {
            return field
        }
        private set(value) {
            field = value
        }

    private val MEDIA_NOTIFICATION_ID = 1001
    private val MESSAGE_NOTIFICATION_ID = 1002

    private lateinit var albumArtCache: AlbumArtCache

    lateinit var mediaSourceSelector: MediaSourceSelector
    lateinit var http: HttpClient
    private var socket: SocketClient? = null
    lateinit var currentUser: User

    private val handler = Handler()
    private var workThread: Thread? = null

    private lateinit var am: AudioManager
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mSessionCompat: MediaSessionCompat
    private lateinit var mSession: MediaSessionCompat.Token
    private lateinit var mediaPlayerForPreloadVerification: MediaPlayer
    private lateinit var mediaCache: MediaCache
    private val debounce = Debounce(5000)

    private lateinit var wifiLock: WifiLock

    var isPaused = false
    var connected = false
    var currentSong: Song? = null
    var userList: List<User>? = null
    var currentPlayUser: User? = null
    var currentPlayUserId: String? = null
    var downvoted = false
    var songStartTime: Long = 0
    var userSongList: MutableList<Song> = mutableListOf()
    var configuration: Configuration? = null

    lateinit var configurationLocalStore: ConfigurationLocalStore
    lateinit var songListLocalStore: SongListLocalStore
    lateinit var userInfoLocalStore: UserInfoLocalStore

    var songListUpdateCallback: ((List<Song>) -> Unit)? = null

    private val ACTION_DOWNVOTE = "com.senorsen.wukong.DOWNVOTE"
    private val ACTION_PAUSE = "com.senorsen.wukong.PAUSE"
    private val ACTION_PLAY = "com.senorsen.wukong.PLAY"
    private lateinit var receiver: BroadcastReceiver

    inner class WukongServiceBinder : Binder() {
        fun getService(): WukongService {
            return this@WukongService
        }
    }

    val binder = WukongServiceBinder()

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    fun onUpdateChannelInfo(users: List<User>? = null, currentPlayUserId: String? = null) {
        val intent = Intent(WukongActivity.UPDATE_CHANNEL_INFO_INTENT)
        intent.putExtra("connectStatus", connectStatus)
        intent.putExtra("users", if (users == null) null else users as Serializable)
        intent.putExtra("currentPlayUserId", currentPlayUserId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun onUpdateSongInfo(song: Song? = currentSong) {
        val intent = Intent(WukongActivity.UPDATE_SONG_INFO_INTENT)
        intent.putExtra("song", song)
        intent.putExtra("isPaused", isPaused)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun onUpdateSongArtwork(artwork: Bitmap) {
        Log.d(TAG, "onUpdateSongArtwork " + artwork)
        val intent = Intent(WukongActivity.UPDATE_SONG_ARTWORK_INTENT)
        intent.putExtra("artwork", artwork)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun onUserInfoUpdate(user: User?, avatar: Bitmap?) {
        val intent = Intent(WukongActivity.UPDATE_USER_INFO_INTENT)
        intent.putExtra("user", user)
        intent.putExtra("avatar", avatar)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun onDisconnectNotification(cause: String) {
        Log.d(TAG, "onDisconnectNotification, cause=" + cause)
        val notificationBuilder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            NotificationCompat.Builder(this)
        else {
            createMessageChannel()
            NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
        }
        val intent = Intent(this, WukongActivity::class.java)
        val contextIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val text = getString(R.string.disconnect_notification, cause)
        notificationBuilder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(contextIntent)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        mNotificationManager!!.notify(MESSAGE_NOTIFICATION_ID, notificationBuilder.build())

        val sendIntent = Intent(WukongActivity.DISCONNECT_INTENT)
        sendIntent.putExtra("cause", cause)
        LocalBroadcastManager.getInstance(this).sendBroadcast(sendIntent)
    }

    private fun onServiceStopped() {
        val intent = Intent(WukongActivity.SERVICE_STOPPED_INTENT)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun fetchConfiguration(http: HttpClient = this.http): Configuration? {
        try {
            configuration = http.getConfiguration()
        } catch (e: HttpClient.UserUnauthorizedException) {
            Log.d(TAG, e.message)
        }
        if (configuration != null) {
            configurationLocalStore.save(configuration)
        }
        return configuration
    }

    fun uploadConfiguration(configuration: Configuration) {
        this.configuration = configuration
        Log.i(WukongService::class.simpleName, "uploadConfiguration $configuration")
        thread {
            http.uploadConfiguration(configuration)
        }
    }

    fun getSongLists(urls: String, cookies: String?): List<Song> {
        userSongList = http.getSongLists(urls, cookies).flatMap(SongList::songs).toMutableList()
        return userSongList
    }

    fun mayLoopSongList(song: Song) {
        if (userSongList.isNotEmpty()) {
            val first = userSongList.first()
            if (first.siteId == song.siteId && first.songId == song.songId) {
                userSongList.remove(first)
                userSongList.add(first)
                handler.post {
                    try {
                        songListUpdateCallback?.invoke(userSongList)
                    } catch (e: Exception) {
                        // Activity may exited.
                        songListLocalStore.save(userSongList)
                    }
                }
            }
        }
    }

    fun doUpdateNextSong() {
        val song = userSongList.firstOrNull()?.toRequestSong(configuration?.cookies) ?: RequestSong(null, null, null)
        thread {
            try {
                http.updateNextSong(song)
            } catch (e: Exception) {
                Log.e(WukongService::class.simpleName, "doUpdateNextSong")
                e.printStackTrace()
            }
        }
        Log.d(WukongService::class.simpleName, "updateNextSong $song")
    }

    private val mNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY ->
                        if (mediaPlayer.isPlaying) handler.post {
                            switchPause()
                            Toast.makeText(context, "Wukong paused.", Toast.LENGTH_SHORT).show()
                        }
                }
                Log.d(TAG, "noisy receiver: " + intent?.action + ", ${mediaPlayer.isPlaying}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        configurationLocalStore = ConfigurationLocalStore(this)
        songListLocalStore = SongListLocalStore(this)
        userInfoLocalStore = UserInfoLocalStore(this)
        mediaSourceSelector = MediaSourceSelector(WeakReference(this))

        // Create media cache later (on start).

        val filter = IntentFilter()
        filter.addAction(ACTION_DOWNVOTE)
        filter.addAction(ACTION_PAUSE)
        filter.addAction(ACTION_PLAY)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    Log.d(TAG, intent.action)
                    when (intent.action) {
                        ACTION_DOWNVOTE -> sendDownvote(intent.getStringExtra("song").toRequestSong())

                        ACTION_PAUSE -> switchPause()

                        ACTION_PLAY -> switchPlay()

                    }
                }
            }
        }
        registerReceiver(receiver, filter)

        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL, getString(R.string.app_name))

        wifiLock.acquire()

        prepareNotification()
        createMedia()

        defaultArtwork = BitmapFactory.decodeResource(resources, R.mipmap.ic_default_art)
    }

    private var mTimer: Timer? = null
    private var mTask: LrcTask? = null

    private fun createMediaPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mediaPlayer.setOnPreparedListener {
            if (mTimer == null) {
                mTimer = Timer()
                mTask = LrcTask()
                mTimer!!.scheduleAtFixedRate(mTask, 0, 500)
            }
        }
    }

    private fun createMedia() {
        createMediaPlayer()

        mediaPlayerForPreloadVerification = MediaPlayer()
        mediaPlayerForPreloadVerification.setAudioStreamType(AudioManager.STREAM_MUSIC)

        mSessionCompat = MediaSessionCompat(this, "WukongMusicService")
        mSession = mSessionCompat.sessionToken
        mSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPause() {
                Log.d(TAG, "onPause")
                switchPause()
            }

            override fun onPlay() {
                Log.d(TAG, "onPlay")
                switchPlay()
            }

            override fun onSkipToNext() {
                Log.d(TAG, "onSkipToNext")
                shuffleSongList()
            }

            override fun onSkipToPrevious() {
                Log.d(TAG, "onSkipToPrevious")
                if (currentSong != null) {
                    sendDownvote(currentSong!!.toRequestSong())
                }
            }

            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                Log.d(TAG, "onMediaButtonEvent called: " + mediaButtonIntent)
                return super.onMediaButtonEvent(mediaButtonIntent)
            }
        })
        mSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        am = getSystemService(AUDIO_SERVICE) as AudioManager
        requestAudioFocus()

        albumArtCache = AlbumArtCache(WeakReference(this))
    }

    var timePassed: Long = 0

    private inner class LrcTask : TimerTask() {
        override fun run() {
            try {
                timePassed = mediaPlayer.currentPosition.toLong()
            } catch (e: Exception) {
                timePassed = 0
            }
        }
    }

    private fun stopLrc() {
        mTimer?.cancel()
        mTimer = null
    }

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        try {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> switchPause()

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> switchPause()

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer.setVolume(0.4f, 0.4f)

                AudioManager.AUDIOFOCUS_GAIN -> switchPlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "afChangeListener $focusChange error", e)
        }
    }

    private fun requestAudioFocus() {
        am.abandonAudioFocus(afChangeListener)
        val result = am.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "requestAudioFocus granted")
        } else {
            Log.e(TAG, "requestAudioFocus failed")
        }
    }

    private fun stopPrevConnect() {
        connected = false
        connectStatus = ConnectStatus.DISCONNECTED
        Log.i(TAG, "socket disconnect")
        socket?.disconnect()
        workThread?.interrupt()

        currentSong = null
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.reset()
        mediaPlayerForPreloadVerification.reset()
        am.abandonAudioFocus(afChangeListener)

        mSessionCompat.isActive = false
        mSessionCompat.release()

        onServiceStopped()
        stopForeground(true)
        mNotificationManager?.cancelAll()
    }

    private fun startConnect() {
        val pref = getSharedPreferences("wukong", Context.MODE_PRIVATE)
        val cookies = pref.getString("cookies", "")
        val channelId = pref.getString("channel", "")
        Log.d(TAG, "Channel: " + channelId)
        Log.d(TAG, "Cookies: " + cookies)

        stopPrevConnect()

        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG)
        applicationContext.registerReceiver(mNoisyReceiver, intentFilter)

        downvoted = false
        currentSong = null
        setNotification(resources.getString(R.string.connecting))
        connectStatus = ConnectStatus.CONNECTING
        onUpdateChannelInfo()
        onUpdateSongInfo()

        workThread = thread {

            mediaCache = MediaCache(WeakReference(this))

            try {
                http = HttpClient(cookies)

                currentUser = http.getUserInfo()
                Log.d(TAG, "User: " + currentUser.toString())
                userInfoLocalStore.save(currentUser)
                // FIXME: Use glide!!
                if (currentUser.avatar != null) {
                    albumArtCache.fetch(currentUser.avatar!!, object : AlbumArtCache.FetchListener() {
                        override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                            Log.d(TAG, "onFetched avatar $artUrl")
                            userInfoLocalStore.save(avatar = bigImage)
                            onUserInfoUpdate(currentUser, bigImage)
                        }
                    })
                } else {
                    onUserInfoUpdate(currentUser, null)
                }

                mediaPlayer.setOnCompletionListener {
                    stopLrc()
                    if (currentSong == null) return@setOnCompletionListener
                    Log.d(TAG, "finished")
                    thread {
                        try {
                            http.reportFinish(currentSong!!.toRequestSong())
                        } catch (e: HttpClient.InvalidRequestException) {
                            Log.e(TAG, "reportFinish invalid request, means data not sync. But server will save us.")
                            e.printStackTrace()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                val receiver = object : SocketClient.SocketReceiver {
                    override fun onEventMessage(protocol: WebSocketReceiveProtocol) {

                        when (protocol.eventName) {

                            Protocol.USER_LIST_UPDATE -> {
                                userList = protocol.users!! as ArrayList<User>
                                currentPlayUser = User.getUserFromList(userList, currentPlayUserId) ?: currentPlayUser
                                onUpdateChannelInfo(userList, currentPlayUserId)
                            }

                            Protocol.PRELOAD -> {
                                val song = protocol.song!!
                                debounce.run {
                                    try {
                                        // Preload artwork image.
                                        getSongArtwork(song)
                                        // Preload media.
                                        val out = mediaCache.getMediaFromDiskCache(song.songKey)
                                        if (out != null) {
                                            Log.d(TAG, "cache exists, skip preload ${song.songKey}")
                                        } else {
                                            val (files, mediaSources) = mediaSourceSelector.selectFromMultipleMediaFiles(song, false)
                                            Log.d(TAG, "preload media sources: $mediaSources")

                                            val source = MediaSourcePreparer.setMediaSources(mediaPlayerForPreloadVerification, mediaSources)
                                            mediaPlayerForPreloadVerification.reset()   // Stop load, save data usage.
                                            if (source.startsWith("http")) {
                                                // Should make cache.
                                                Log.d(TAG, "preload: ${song.songKey}, $source")
                                                mediaCache.addMediaToCache(song.songKey, source)
                                            } else {
                                                Log.d(TAG, "no need to preload $source")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // No notification any more, because no user want to see errors.
                                        Log.e(TAG, "preload error", e)
                                    }
                                }
                            }

                            Protocol.PLAY -> {
                                if (protocol.song == null) {
                                    setNotification("Channel $channelId: play null")
                                    return
                                }

                                val song = protocol.song

                                currentPlayUserId = protocol.user
                                currentPlayUser = User.getUserFromList(userList, currentPlayUserId) ?: currentPlayUser

                                downvoted = protocol.downvote ?: false
                                val oldStartTime = songStartTime
                                songStartTime = currentTimeMillis() - (protocol.elapsed!! * 1000).toLong()

                                // Reduce noise or glitch.
                                if (currentSong?.songKey == song.songKey && Math.abs(songStartTime - oldStartTime) < 5000) {
                                    Log.i(TAG, "server may send exactly the same song ${song.songKey}, skipping")
                                    return
                                }

                                setNotification()
                                onUpdateChannelInfo(userList, currentPlayUserId)
                                onUpdateSongInfo(song)

                                if (currentUser.id == protocol.user) {
                                    mayLoopSongList(song)
                                    doUpdateNextSong()
                                }

                                try {
                                    val out = mediaCache.getMediaFromDiskCache(song.songKey)
                                    // Stop playing first.
                                    if (out != null) {
                                        Log.i(TAG, "media cache HIT: $out")
                                        MediaSourcePreparer.setMediaSource(mediaPlayer, out, protocol.elapsed)
                                    } else {
                                        Log.i(TAG, "media cache MISS")
                                        val (files, mediaSources) = mediaSourceSelector.selectFromMultipleMediaFiles(song)
                                        Log.d(TAG, "play media sources: $mediaSources")
                                        currentSong = song
                                        MediaSourcePreparer.setMediaSources(mediaPlayer, mediaSources, protocol.elapsed)
                                    }
                                    if (!isPaused) mediaPlayer.start()
                                } catch (e: Exception) {
                                    Log.e(TAG, "play", e)
                                    handler.post {
                                        Toast.makeText(applicationContext, "Wukong: playback error", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                            Protocol.NOTIFICATION -> {
                                handler.post {
                                    Toast.makeText(applicationContext, "Wukong: " + protocol.notification?.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                var channelListener: SocketClient.ChannelListener? = null

                var executor: ScheduledThreadPoolExecutor? = null

                val doConnectWebSocket = fun() {
                    http.channelJoin(channelId)
                    fetchConfiguration()
                    if (socket == null) {
                        socket = SocketClient(ApiUrls.wsEndpoint + "?deviceId=" + URLEncoder.encode("${http.userAgent} ${Build.MANUFACTURER} ${Build.MODEL} ${Build.PRODUCT}", Charsets.UTF_8.name()), cookies, http.userAgent, channelId, channelListener!!, receiver)
                    }
                    socket!!.connect()
                    connected = true
                    connectStatus = ConnectStatus.CONNECTED
                    doUpdateNextSong()
                }

                channelListener = object : SocketClient.ChannelListener {
                    override fun error() {
                        var retry = true
                        connectStatus = ConnectStatus.RECONNECTING
                        onUpdateChannelInfo()
                        while (retry && connected) {
                            try {
                                Thread.sleep(3000)
                                doConnectWebSocket()
                                retry = false
                            } catch (e: IOException) {
                                e.printStackTrace()
                                retry = true
                            }
                        }
                    }

                    override fun disconnect(cause: String) {
                        handler.post {
                            stopPrevConnect()
                            onDisconnectNotification(cause)
                        }
                    }
                }

                try {
                    doConnectWebSocket()
                    setNotification(String.format(resources.getString(R.string.channel_id_idle), channelId))
                } catch (e: IOException) {
                    Log.e(TAG, "socket exception: " + e.message)
                }

            } catch (e: HttpClient.UserUnauthorizedException) {
                handler.post {
                    Toast.makeText(applicationContext, "Please sign in to continue.", Toast.LENGTH_SHORT).show()
                    userInfoLocalStore.save(user = null)
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(applicationContext, "Error: " + e.message, Toast.LENGTH_LONG).show()
                    stopService(Intent(this, WukongService::class.java))
                }
            }

        }
    }

    private fun sendSongUpdateRequest() {
        Log.d(TAG, "song update request (dummy downvote)")
        thread {
            try {
                http.downvote(RequestSong("dummy", "dummy", null))
            } catch (e: HttpClient.InvalidRequestException) {
                Log.i(TAG, "song update - server should respond")
            } catch (e: Exception) {
                Log.e(TAG, "song update request failed", e)
            }
        }
    }

    fun sendDownvote(song: RequestSong) {
        Log.d(TAG, currentSong!!.toRequestSong().toString())
        Log.d(TAG, song.toString())
        if (currentSong != null && currentSong!!.toRequestSong().toString() == song.toString()) {
            Log.d(TAG, "Downvote: $song")
            thread {
                try {
                    http.downvote(song)
                } catch (e: Exception) {
                    Log.e(TAG, "sendDownvote", e)
                    handler.post {
                        Toast.makeText(applicationContext, "Downvote failed: " + e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            downvoted = true
            setNotification()
        }
    }

    fun switchPause() {
        try {
            Log.d(TAG, "switchPause")
            mediaPlayer.pause()
            isPaused = true
            setNotification()
            updateMediaSessionState()
            onUpdateSongInfo()
        } catch (e: Exception) {
            Log.e(TAG, "switchPause", e)
        }
    }

    fun switchPlay() {
        try {
            Log.d(TAG, "switchPlay")
            mediaPlayer.seekTo((currentTimeMillis() - songStartTime).toInt())
            mediaPlayer.start()
            mediaPlayer.setVolume(1.0f, 1.0f)
            isPaused = false
            setNotification()
            updateMediaSessionState()
            onUpdateSongInfo()
        } catch (e: Exception) {
            Log.e(TAG, "switchPlay", e)
        }
    }

    private fun shuffleSongList() {
        Collections.shuffle(userSongList, Random(System.nanoTime()))
        doUpdateNextSong()
        val intent = Intent(WukongActivity.UPDATE_SONG_LIST_INTENT)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent != null) {
            startConnect()
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {

        stopPrevConnect()
        onUpdateChannelInfo(null, null)
        unregisterReceiver(receiver)

        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private var mNotificationManager: NotificationManagerCompat? = null
    private var mNotificationColor: Int = 0

    private fun prepareNotification() {
        if (mNotificationManager == null)
            mNotificationManager = NotificationManagerCompat.from(this)
        mNotificationColor = ResourceHelper.getThemeColor(this, R.attr.colorPrimary, Color.DKGRAY)
    }

    private val MEDIA_CHANNEL_ID = "wukong_media_playback_channel"
    private val MESSAGE_CHANNEL_ID = "wukong_message_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createMediaChannel() {
        val mNotificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = MEDIA_CHANNEL_ID
        val name = "Media playback"
        val description = "Media playback controls"
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel(id, name, importance)
        mChannel.setDescription(description)
        mChannel.setShowBadge(false)
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
        mNotificationManager.createNotificationChannel(mChannel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createMessageChannel() {
        val mNotificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = MESSAGE_CHANNEL_ID
        val name = "System message"
        val description = "System messages and alerts"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val mChannel = NotificationChannel(id, name, importance)
        mChannel.setDescription(description)
        mChannel.setShowBadge(false)
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
        mNotificationManager.createNotificationChannel(mChannel)
    }

    private fun makeMediaNotificationBuilder(nContent: String?): NotificationCompat.Builder {
        var title = resources.getString(R.string.app_name)
        var content = nContent
        if (nContent == null && currentSong != null) {
            title = currentSong?.title!!
            content = "(${currentPlayUser?.displayName ?: currentPlayUser?.userName}) ${currentSong?.artist} - ${currentSong?.album}"
        }

        val intent = Intent(this, WukongActivity::class.java)
        val contextIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val notificationBuilder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            NotificationCompat.Builder(this)
        else {
            createMediaChannel()
            NotificationCompat.Builder(this, MEDIA_CHANNEL_ID)
        }
        notificationBuilder
                .setStyle(MediaStyle()
                        .setShowActionsInCompactView()
                        .setMediaSession(mSession))
                .setColor(mNotificationColor)
                .setContentIntent(contextIntent)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)

        val (playButtonAction, playButtonIcon, playButtonTitle) = if (isPaused) {
            Triple(ACTION_PLAY, R.drawable.ic_play, resources.getString(R.string.play))
        } else {
            Triple(ACTION_PAUSE, R.drawable.ic_pause, resources.getString(R.string.pause))
        }
        notificationBuilder.addAction(NotificationCompat.Action(playButtonIcon, playButtonTitle,
                PendingIntent.getBroadcast(this, 100,
                        Intent(playButtonAction).setPackage(packageName), PendingIntent.FLAG_CANCEL_CURRENT)))

        if (currentSong != null && !downvoted) {
            notificationBuilder.addAction(NotificationCompat.Action(R.drawable.ic_downvote, "Downvote",
                    PendingIntent.getBroadcast(this, 100,
                            Intent(ACTION_DOWNVOTE).setPackage(packageName)
                                    .putExtra("song", currentSong!!.toRequestSong().toString()),
                            PendingIntent.FLAG_CANCEL_CURRENT)))
        }

        val art = getSongArtwork(currentSong, notificationBuilder)
        if (currentSong != null)
            updateMediaSessionMeta(art)
        notificationBuilder.setLargeIcon(art ?: defaultArtwork)

        return notificationBuilder
    }

    private fun isUIThread() = Thread.currentThread() == Looper.getMainLooper().thread

    private fun setNotification(content: String? = null) {
        handler.post {
            Log.d(TAG, "setNotification $content")
            val builder = makeMediaNotificationBuilder(content)
            if (content == null)
                builder.setWhen(songStartTime)
            val notification = builder.build()

            notification.flags = NotificationCompat.FLAG_ONGOING_EVENT
            mNotificationManager!!.notify(MEDIA_NOTIFICATION_ID, notification)
            startForeground(MEDIA_NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaSessionMeta(bitmap: Bitmap?) {
        mSessionCompat.setMetadata(currentSong!!.toMediaMetaData(bitmap))
        mSessionCompat.isActive = true
        updateMediaSessionState()
    }

    private fun updateMediaSessionState() {
        val stateBuilder = PlaybackStateCompat.Builder()
        stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        stateBuilder.setState(if (isPaused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)

        mSessionCompat.setPlaybackState(stateBuilder.build())
        mSessionCompat.isActive = !isPaused
    }

    private lateinit var defaultArtwork: Bitmap

    fun getSongArtwork(song: Song?, builder: NotificationCompat.Builder? = null, fetch: Boolean = true): Bitmap? {
        return if (song?.artwork == null) {
            null
        } else {
            val cache = albumArtCache.getBigImage(song)
            if (cache == null && fetch) {
                fetchBitmapFromURLAsync(song, builder)
            }
            cache
        }
    }

    private fun fetchBitmapFromURLAsync(song: Song, builder: NotificationCompat.Builder? = null) {
        albumArtCache.fetch(song, object : AlbumArtCache.FetchListener() {
            override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                if (currentSong == song) {
                    // If the media is still the same, update the notification:
                    Log.d(TAG, "fetchBitmapFromURLAsync: set bitmap to " + artUrl)
                    updateMediaSessionMeta(bigImage)
                    if (builder != null) {
                        builder.setLargeIcon(bigImage)
                        mNotificationManager!!.notify(MEDIA_NOTIFICATION_ID, builder.build())
                    }
                    // Update UI
                    onUpdateSongArtwork(bigImage)
                }
            }
        })
    }

}
