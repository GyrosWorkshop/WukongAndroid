package com.senorsen.wukong.service

import android.app.PendingIntent
import android.app.Service
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
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.senorsen.wukong.R
import com.senorsen.wukong.media.AlbumArtCache
import com.senorsen.wukong.media.MediaCache
import com.senorsen.wukong.media.MediaSourcePreparer
import com.senorsen.wukong.media.MediaSourceSelector
import com.senorsen.wukong.model.*
import com.senorsen.wukong.network.*
import com.senorsen.wukong.store.ConfigurationLocalStore
import com.senorsen.wukong.store.SongListLocalStore
import com.senorsen.wukong.store.UserInfoLocalStore
import com.senorsen.wukong.ui.MainActivity
import com.senorsen.wukong.utils.Debounce
import com.senorsen.wukong.utils.ObjectSerializer
import com.senorsen.wukong.utils.ResourceHelper
import java.io.IOException
import java.io.Serializable
import java.lang.System.currentTimeMillis
import java.util.*
import kotlin.concurrent.thread


class WukongService : Service() {

    private val TAG = javaClass.simpleName

    private val NOTIFICATION_ID = 1

    private lateinit var albumArtCache: AlbumArtCache

    lateinit var mediaSourceSelector: MediaSourceSelector
    lateinit var http: HttpWrapper
    var socket: SocketWrapper? = null
    lateinit var currentUser: User

    val handler = Handler()
    var workThread: Thread? = null

    private lateinit var am: AudioManager
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mSessionCompat: MediaSessionCompat
    private lateinit var mSession: MediaSessionCompat.Token
    private lateinit var mediaPlayerForPreloadVerification: MediaPlayer
    private val debounce = Debounce(5000)

    private lateinit var mediaCache: MediaCache

    private lateinit var wifiLock: WifiLock

    var isPaused = false

    @Volatile var connected = false
    @Volatile var currentSong: Song? = null
    @Volatile var userList: List<User>? = null
    @Volatile var currentPlayUser: User? = null
    @Volatile var currentPlayUserId: String? = null
    @Volatile var downvoted = false
    @Volatile var songStartTime: Long = 0
    @Volatile var userSongList: MutableList<Song> = mutableListOf()
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

    fun onUpdateChannelInfo(connected: Boolean, users: List<User>?, currentPlayUserId: String? = null) {
        val intent = Intent(MainActivity.UPDATE_CHANNEL_INFO_INTENT)
        intent.putExtra("connected", connected)
        intent.putExtra("users", ObjectSerializer.serialize(if (users == null) null else users as Serializable))
        intent.putExtra("currentPlayUserId", currentPlayUserId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun fetchConfiguration(): Configuration? {
        try {
            configuration = http.getConfiguration()
        } catch (e: HttpWrapper.UserUnauthorizedException) {
            Log.d(WukongService::class.simpleName, e.message)
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
        val songLists = urls.split('\n').map { it.trim() }.filter { it.isNotBlank() }.map { url ->
            try {
                http.getSongListWithUrl(url, cookies)
            } catch (e: Exception) {
                Log.e(HttpWrapper::class.simpleName, "getSongLists")
                e.printStackTrace()
                null
            }
        }
        userSongList = songLists.map { it?.songs ?: listOf() }.flatMap { it }.toMutableList()
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

            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY ->
                    if (mediaPlayer.isPlaying) handler.post {
                        switchPause()
                        Toast.makeText(context, "Wukong paused.", Toast.LENGTH_SHORT).show()
                    }
            }
            Log.d(TAG, "noisy receiver: " + intent?.action + ", ${mediaPlayer.isPlaying}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        configurationLocalStore = ConfigurationLocalStore(this)
        songListLocalStore = SongListLocalStore(this)
        userInfoLocalStore = UserInfoLocalStore(this)
        mediaSourceSelector = MediaSourceSelector(this)
        mediaCache = MediaCache(this)

        val filter = IntentFilter()
        filter.addAction(ACTION_DOWNVOTE)
        filter.addAction(ACTION_PAUSE)
        filter.addAction(ACTION_PLAY)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    Log.d(TAG, intent.action)
                    when (intent.action) {
                        ACTION_DOWNVOTE -> sendDownvote(ObjectSerializer.deserialize(intent.getStringExtra("song")) as RequestSong)

                        ACTION_PAUSE -> switchPause()

                        ACTION_PLAY -> switchPlay()

                    }
                }
            }
        }
        registerReceiver(receiver, filter)

        wifiLock = (getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL, getString(R.string.app_name))

        wifiLock.acquire()

        prepareNotification()
        createMedia()
    }

    private fun createMedia() {
        mediaPlayer = MediaPlayer()
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

        mediaPlayerForPreloadVerification = MediaPlayer()
        mediaPlayerForPreloadVerification.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
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

        albumArtCache = AlbumArtCache(this)
    }


    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS ->
                switchPause()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                switchPause()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                mediaPlayer.setVolume(0.1f, 0.1f)

            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer.setVolume(1.0f, 1.0f)
                switchPlay()
            }
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
        if (workThread != null) {
            Log.i(TAG, "socket disconnect")
            socket?.disconnect()
            workThread?.interrupt()
            connected = false
        }

        currentSong = null
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.reset()
        mediaPlayerForPreloadVerification.reset()

        mSessionCompat.isActive = false
    }

    var onUserInfoUpdate: ((user: User?, avatar: Bitmap?) -> Unit)? = null

    fun registerUpdateUserInfo(callback: ((user: User?, avatar: Bitmap?) -> Unit)?) {
        onUserInfoUpdate = callback
    }

    private fun startConnect(intent: Intent) {
        val cookies = intent.getStringExtra("cookies")
        val channelId = intent.getStringExtra("channel")
        Log.d(TAG, "Channel: " + channelId)
        Log.d(TAG, "Cookies: " + cookies)
        http = HttpWrapper(cookies)

        stopPrevConnect()

        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG)
        applicationContext.registerReceiver(mNoisyReceiver, intentFilter)

        downvoted = false

        workThread = Thread(Runnable {

            try {

                currentUser = http.getUserInfo()
                Log.d(TAG, "User: " + currentUser.toString())
                userInfoLocalStore.save(currentUser)
                try {
                    onUserInfoUpdate?.invoke(currentUser, null)
                } catch (e: Exception) {

                }
                // FIXME: !!
                if (currentUser.avatar != null) {
                    albumArtCache.fetch(currentUser.avatar!!, object : AlbumArtCache.FetchListener() {
                        override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                            Log.d(TAG, "onFetched avatar $artUrl")
                            userInfoLocalStore.save(avatar = bigImage)
                            try {
                                onUserInfoUpdate?.invoke(currentUser, bigImage)
                            } catch (e: Exception) {

                            }
                        }
                    })
                }

                mediaPlayer.setOnCompletionListener {
                    Log.d(TAG, "finished")
                    thread {
                        try {
                            http.reportFinish(currentSong!!.toRequestSong())
                        } catch (e: HttpWrapper.InvalidRequestException) {
                            Log.e(TAG, "reportFinish invalid request, means data not sync. But server will save us.")
                            e.printStackTrace()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                val receiver = object : SocketWrapper.SocketReceiver {
                    override fun onEventMessage(protocol: WebSocketReceiveProtocol) {
                        // FIXME: 该分层了。。。

                        when (protocol.eventName) {

                            Protocol.USER_LIST_UPDATE -> {
                                userList = protocol.users!! as ArrayList<User>
                                currentPlayUser = User.getUserFromList(userList, currentPlayUserId) ?: currentPlayUser
                                handler.post {
                                    Toast.makeText(applicationContext, "Wukong $channelId: " + userList!!.map { it.userName }.joinToString(), Toast.LENGTH_SHORT).show()
                                }
                                onUpdateChannelInfo(connected, userList, currentPlayUserId)
                            }

                            Protocol.PRELOAD -> {

                                Log.d(TAG, "preload ${protocol.song?.songKey} image " + protocol.song)

                                // Preload artwork image.
                                if (protocol.song?.artwork != null)
                                    albumArtCache.fetch(protocol.song.artwork?.file!!, null, protocol.song.songKey)

                                val song = protocol.song!!

                                debounce.run {
                                    try {
                                        val out = mediaCache.getMediaFromDiskCache(song.songKey)
                                        if (out != null) {
                                            Log.d(TAG, "cache exists, skip preload ${song.songKey}")
                                        } else {
                                            val (files, mediaSources) = mediaSourceSelector.selectFromMultipleMediaFiles(song)
                                            Log.d(TAG, "preload media sources: $mediaSources")

                                            val source = MediaSourcePreparer.setMediaSources(mediaPlayerForPreloadVerification, mediaSources)
                                            if (source.startsWith("http")) {
                                                // Should make cache.
                                                Log.d(TAG, "preload: ${song.songKey}, $source")
                                                mediaCache.addMediaToCache(song.songKey, source)
                                            } else {
                                                Log.d(TAG, "no need to preload $source")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        handler.post {
                                            Toast.makeText(applicationContext, "Wukong: preload error", Toast.LENGTH_LONG).show()
                                        }
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

                                onUpdateChannelInfo(connected, userList, currentPlayUserId)

                                if (currentUser.id == protocol.user) {
                                    mayLoopSongList(song)
                                    doUpdateNextSong()
                                }

                                currentSong = song
                                downvoted = protocol.downVote ?: false
                                songStartTime = currentTimeMillis() - (protocol.elapsed!! * 1000).toLong()

                                handler.post {
                                    setNotification()
                                }

                                try {
                                    val out = mediaCache.getMediaFromDiskCache(song.songKey)
                                    if (out != null) {
                                        Log.i(TAG, "using cache: $out")
                                        MediaSourcePreparer.setMediaSource(mediaPlayer, out, protocol.elapsed)
                                    } else {
                                        val (files, mediaSources) = mediaSourceSelector.selectFromMultipleMediaFiles(song)
                                        Log.d(TAG, "play media sources: $mediaSources")
                                        MediaSourcePreparer.setMediaSources(mediaPlayer, mediaSources, protocol.elapsed)
                                    }
                                    if (!isPaused) mediaPlayer.start()
                                } catch (e: Exception) {
                                    Log.e(TAG, "play", e)
                                    handler.post {
                                        Toast.makeText(applicationContext, "Wukong: playback error, all media sources tried. See log for details.", Toast.LENGTH_LONG).show()
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

                var reconnectCallback: Any? = null

                val doConnect = fun() {
                    http.channelJoin(channelId)
                    fetchConfiguration()
                    socket = SocketWrapper(ApiUrls.wsEndpoint, cookies, channelId, reconnectCallback as SocketWrapper.Callback, receiver, handler, applicationContext)
                    connected = true
                    doUpdateNextSong()
                }

                reconnectCallback = object : SocketWrapper.Callback {
                    override fun call() {
                        var retry = true
                        while (retry) {
                            try {
                                Thread.sleep(1000)
                                handler.post {
                                    setNotification("Reconnecting")
                                    Toast.makeText(applicationContext, "Wukong: Reconnecting...", Toast.LENGTH_SHORT).show()
                                }
                                doConnect()
                                retry = false
                            } catch (e: IOException) {
                                retry = true
                            }
                        }
                    }
                }

                try {
                    doConnect()
                    setNotification("Channel $channelId idle.")
                } catch (e: IOException) {
                    Log.e(TAG, "socket exception: " + e.message)
                }

            } catch (e: HttpWrapper.UserUnauthorizedException) {
                handler.post {
                    Toast.makeText(applicationContext, "Please sign in to continue.", Toast.LENGTH_SHORT).show()
                    userInfoLocalStore.save(user = null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    Toast.makeText(applicationContext, "Unknown Exception: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }

        })
        workThread!!.start()
    }

    private fun sendDownvote(song: RequestSong) {
        Log.d(TAG, currentSong!!.toRequestSong().toString())
        Log.d(TAG, song.toString())
        if (currentSong != null && currentSong!!.toRequestSong().toString() == song.toString()) {
            Log.d(TAG, "Downvote: $song")
            thread {
                try {
                    http.downvote(song)
                } catch (e: Exception) {
                    Log.e(TAG, "sendDownvote")
                    e.printStackTrace()
                }
            }
            downvoted = true
            setNotification()
        }
    }

    private fun switchPause() {
        try {
            mediaPlayer.pause()
            isPaused = true
            setNotification()
            updateMediaSessionState()
        } catch (e: Exception) {
        }
    }

    private fun switchPlay() {
        try {
            requestAudioFocus()
            mediaPlayer.seekTo((currentTimeMillis() - songStartTime).toInt())
            mediaPlayer.start()
            isPaused = false
            setNotification()
            updateMediaSessionState()
        } catch (e: Exception) {
        }
    }

    private fun shuffleSongList() {
        Collections.shuffle(userSongList, Random(System.nanoTime()))
        doUpdateNextSong()
        val intent = Intent(MainActivity.UPDATE_SONG_LIST_INTENT)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent != null) {
            startConnect(intent)
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {

        stopPrevConnect()

        mediaPlayer.release()
        wifiLock.release()

        onUpdateChannelInfo(connected, null, null)

        super.onDestroy()
        unregisterReceiver(receiver)
        Log.d(TAG, "onDestroy")
    }

    private lateinit var mNotificationManager: NotificationManagerCompat
    private var mNotificationColor: Int = 0

    private fun prepareNotification() {
        mNotificationManager = NotificationManagerCompat.from(this)
        mNotificationColor = ResourceHelper.getThemeColor(this, R.attr.colorPrimary, Color.DKGRAY)
    }

    private fun makeNotificationBuilder(nContent: String?): NotificationCompat.Builder {
        var title: String = "Wukong"
        var content = nContent
        if (nContent == null && currentSong != null) {
            title = currentSong?.title!!
            content = "(${currentPlayUser?.displayName ?: currentPlayUser?.userName}) ${currentSong?.artist} - ${currentSong?.album}"
        }

        val intent = Intent(this, MainActivity::class.java)
        val contextIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val notificationBuilder = NotificationCompat.Builder(this)
                .setStyle(NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView()
                        .setMediaSession(mSession))
                .setColor(mNotificationColor)
                .setContentIntent(contextIntent)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(title)
                .setContentText(content) as android.support.v7.app.NotificationCompat.Builder

        if (!isPaused) {
            val serviceIntent = Intent(ACTION_PAUSE).setPackage(packageName)
            val pauseIntent = PendingIntent.getBroadcast(this, 100, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val action = android.support.v4.app.NotificationCompat.Action(R.drawable.ic_pause, "Pause", pauseIntent)
            notificationBuilder.addAction(action)
        } else {
            val serviceIntent = Intent(ACTION_PLAY).setPackage(packageName)
            val playIntent = PendingIntent.getBroadcast(this, 100, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val action = android.support.v4.app.NotificationCompat.Action(R.drawable.ic_play, "Play", playIntent)
            notificationBuilder.addAction(action)
        }

        if (currentSong != null && !downvoted) {
            val serviceIntent = Intent(ACTION_DOWNVOTE).setPackage(packageName)
            serviceIntent.putExtra("song", ObjectSerializer.serialize(currentSong!!.toRequestSong()))
            val downvoteIntent = PendingIntent.getBroadcast(this, 100, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val action = android.support.v4.app.NotificationCompat.Action(R.drawable.ic_downvote, "Downvote", downvoteIntent)
            notificationBuilder.addAction(action)
        }

        var art: Bitmap? = null
        var fetchArtUrl: String? = null
        if (currentSong != null) {
            fetchArtUrl = currentSong!!.artwork?.file
            if (fetchArtUrl != null) {
                art = albumArtCache.getBigImage(fetchArtUrl, currentSong!!.songKey)
            }
        }

        if (art == null) {
            // use a placeholder art while the remote art is being downloaded
            art = BitmapFactory.decodeResource(resources,
                    R.mipmap.ic_default_art)

            if (fetchArtUrl != null) {
                fetchBitmapFromURLAsync(fetchArtUrl, currentSong!!, notificationBuilder)
            }
        } else {
            updateMediaSessionMeta(art)
        }

        notificationBuilder.setLargeIcon(art)
        return notificationBuilder
    }

    private fun setNotification() {
        val notification = makeNotificationBuilder(null)
                .setWhen(songStartTime).build()

        notification.flags = NotificationCompat.FLAG_ONGOING_EVENT
        mNotificationManager.notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setNotification(content: String?) {
        val notification = makeNotificationBuilder(content).build()

        notification.flags = NotificationCompat.FLAG_ONGOING_EVENT
        mNotificationManager.notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setNotification(builder: NotificationCompat.Builder) {
        val notification = builder.build()

        notification.flags = NotificationCompat.FLAG_ONGOING_EVENT
        mNotificationManager.notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateMediaSessionMeta(bitmap: Bitmap) {
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
        mSessionCompat.isActive = true
    }

    private fun fetchBitmapFromURLAsync(bitmapUrl: String, song: Song, builder: NotificationCompat.Builder) {
        albumArtCache.fetch(bitmapUrl, object : AlbumArtCache.FetchListener() {
            override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                if (currentSong == song) {
                    // If the media is still the same, update the notification:
                    Log.d(TAG, "fetchBitmapFromURLAsync: set bitmap to " + artUrl)
                    builder.setLargeIcon(bigImage)
                    mNotificationManager.notify(NOTIFICATION_ID, builder.build())

                    updateMediaSessionMeta(bigImage)
                }
            }
        }, song.songKey)
    }

}
