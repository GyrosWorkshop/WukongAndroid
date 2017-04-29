package com.senorsen.wukong.service

import android.app.*
import android.content.*
import android.content.ContentValues.TAG
import android.util.Log
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.support.v7.app.NotificationCompat
import com.senorsen.wukong.R
import com.senorsen.wukong.ui.MainActivity
import com.senorsen.wukong.network.*
import java.io.IOException
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.senorsen.wukong.media.AlbumArtCache
import com.senorsen.wukong.media.MediaSourceSelector
import com.senorsen.wukong.model.*
import com.senorsen.wukong.utils.ResourceHelper
import java.lang.System.currentTimeMillis
import kotlin.concurrent.thread


class WukongService : Service() {

    private val NOTIFICATION_ID = 1

    private lateinit var albumArtCache: AlbumArtCache

    lateinit var mediaSourceSelector: MediaSourceSelector
    lateinit var http: HttpWrapper
    var socket: SocketWrapper? = null
    lateinit var currentUser: User

    val handler = Handler()
    var workThread: Thread? = null

    private lateinit var mAm: AudioManager
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mSessionCompat: MediaSessionCompat
    private lateinit var mSession: MediaSessionCompat.Token

    private lateinit var wifiLock: WifiLock

    var isPaused = false

    var currentSong: Song? = null
    var currentFile: File? = null
    var downvoted = false
    var songStartTime: Long = 0

    private val ACTION_DOWNVOTE = "com.senorsen.wukong.DOWNVOTE"
    private val ACTION_PAUSE = "com.senorsen.wukong.PAUSE"
    private val ACTION_PLAY = "com.senorsen.wukong.PLAY"
    private lateinit var receiver: BroadcastReceiver

    inner class WukongServiceBinder : Binder() {
        fun getService(): WukongService {
            return this@WukongService
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        mediaSourceSelector = MediaSourceSelector(applicationContext)

        val filter = IntentFilter()
        filter.addAction(ACTION_DOWNVOTE)
        filter.addAction(ACTION_PAUSE)
        filter.addAction(ACTION_PLAY)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    Log.d(TAG, intent.action)
                    when (intent.action) {
                        ACTION_DOWNVOTE -> sendDownvote(intent)

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

        mSessionCompat = MediaSessionCompat(this, "WukongMusicService")
        mSession = mSessionCompat.sessionToken

        mAm = getSystemService(AUDIO_SERVICE) as AudioManager

        albumArtCache = AlbumArtCache(this)
    }


    private val mNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY ->
                    if (mediaPlayer.isPlaying) handler.post {
                        isPaused = true
                        mediaPlayer.pause()
                        Toast.makeText(context, "Wukong paused.", Toast.LENGTH_SHORT).show()
                    }
            }
            Log.d(TAG, "noisy receiver: " + intent?.action + ", ${mediaPlayer.isPlaying}")
        }
    }

    private fun stopPrevConnect() {
        if (workThread != null) {
            Log.i(TAG, "socket disconnect")
            socket?.disconnect()
            workThread?.interrupt()
        }

        currentSong = null
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.reset()
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

        isPaused = false
        downvoted = false

        workThread = Thread(Runnable {

            try {

                currentUser = http.getUserInfo()
                Log.d(TAG, "User: " + currentUser.toString())

                var userList: ArrayList<User>? = null

                val receiver = object : SocketWrapper.SocketReceiver {
                    override fun onEventMessage(protocol: WebSocketReceiveProtocol) {
                        // FIXME: 该分层了。。。

                        when (protocol.eventName) {

                            Protocol.USER_LIST_UPDATE -> {
                                userList = protocol.users!! as ArrayList<User>
                                handler.post {
                                    Toast.makeText(applicationContext, "Wukong $channelId: " + userList!!.map { it.userName }.joinToString(), Toast.LENGTH_SHORT).show()
                                }
                            }

                            Protocol.PRELOAD -> {

                                Log.d(TAG, "preload ${protocol.song?.songKey} image " + protocol.song)

                                // Preload artwork image.
                                if (protocol.song?.artwork != null)
                                    albumArtCache.fetch(protocol.song.artwork?.file!!, null, protocol.song.songKey)

                            }

                            Protocol.PLAY -> {

                                if (protocol.song == null) {
                                    setNotification("Channel $channelId: play null")
                                    return
                                }

                                val song = protocol.song!!
                                currentSong = song
                                downvoted = protocol.downVote ?: false
                                songStartTime = currentTimeMillis() - (protocol.elapsed!! * 1000).toLong()

                                setNotification(songStartTime)

                                var mediaSrc: String? = mediaSourceSelector.getValidLocalMedia(song)
                                if (mediaSrc == null) {
                                    val (currentFile, originalUrl) = mediaSourceSelector.selectFromMultipleMediaFiles(song.musics!!)
                                    Log.i(TAG, "file audio quality: ${currentFile.audioQuality}")
                                    Log.i(TAG, "originalUrl: $originalUrl")
                                    mediaSrc = MediaProvider().resolveRedirect(originalUrl)
                                    Log.i(TAG, "resolved media url: $mediaSrc")
                                } else {
                                    Log.i(TAG, "use local media: $mediaSrc")
                                }
                                mediaPlayer.reset()
                                try {
                                    mediaPlayer.setDataSource(mediaSrc)
                                    mediaPlayer.prepare()
                                    mediaPlayer.seekTo((protocol.elapsed!! * 1000).toInt())

                                    if (!isPaused)
                                        mediaPlayer.start()

                                    mediaPlayer.setOnCompletionListener {
                                        Log.d(TAG, "finished")
                                        thread {
                                            try {
                                                http.reportFinish(song.toRequestSong())
                                            } catch (e: HttpWrapper.InvalidRequestException) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    handler.post {
                                        Toast.makeText(applicationContext, "Wukong play error: " + e.message, Toast.LENGTH_LONG).show()
                                    }
                                    Log.e(TAG, "play")
                                    e.printStackTrace()
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
                    socket = SocketWrapper(ApiUrls.wsEndpoint, cookies, channelId, reconnectCallback as SocketWrapper.Callback, receiver, handler, applicationContext)
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

    private fun sendDownvote(intent: Intent) {
        Log.d(TAG, currentSong!!.toRequestSong().toString())
        Log.d(TAG, intent.getStringExtra("song"))
        if (currentSong != null && currentSong!!.toRequestSong().toString() == intent.getStringExtra("song")) {
            Log.d(TAG, "Downvote: $currentSong")
            thread {
                http.downvote(currentSong!!.toRequestSong())
            }
            downvoted = true
            setNotification(songStartTime)
        }
    }

    private fun switchPause() {
        try {
            mediaPlayer.pause()
            isPaused = true
            setNotification(songStartTime)
        } catch (e: Exception) {
        }
    }

    private fun switchPlay() {
        try {
            mediaPlayer.start()
            isPaused = false
            setNotification(songStartTime)
        } catch (e: Exception) {
        }
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
            content = "${currentSong?.artist} - ${currentSong?.album}"
        }

        val intent = Intent(this, MainActivity::class.java)
        val contextIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val playPauseButtonPosition = 0

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

        if (currentSong != null) {
            val serviceIntent = Intent(ACTION_DOWNVOTE).setPackage(packageName)
            serviceIntent.putExtra("song", currentSong!!.toRequestSong().toString())
            val downvoteIntent = PendingIntent.getBroadcast(this, 100, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val action = android.support.v4.app.NotificationCompat.Action(R.drawable.ic_downvote, "Downvote", downvoteIntent)
            if (downvoted) {
                val f = action.javaClass.getDeclaredField("mAllowGeneratedReplies")
                f.isAccessible = true
                f.set(action, false)
            }
            notificationBuilder.addAction(action)
        }

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

        var art: Bitmap? = null
        var fetchArtUrl: String? = null
        if (currentSong != null) {
//            fetchArtUrl = mediaSourceSelector.selectMediaUrlByCdnSettings(currentSong?.artwork!!)
            fetchArtUrl = currentSong!!.artwork?.file
            if (fetchArtUrl != null) {
                // This sample assumes the iconUri will be a valid URL formatted String, but
                // it can actually be any valid Android Uri formatted String.
                // async fetch the album art icon
                art = albumArtCache.getBigImage(fetchArtUrl, currentSong!!.songKey)
            }
        }

        if (art == null) {
            // use a placeholder art while the remote art is being downloaded
            art = BitmapFactory.decodeResource(resources,
                    R.mipmap.ic_default_art)
        }

        notificationBuilder.setLargeIcon(art)

        if (fetchArtUrl != null) {
            fetchBitmapFromURLAsync(fetchArtUrl, currentSong!!, notificationBuilder)
        }
        return notificationBuilder
    }

    private fun setNotification(songStartTime: Long) {
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

    private fun fetchBitmapFromURLAsync(bitmapUrl: String, song: Song, builder: NotificationCompat.Builder) {
        albumArtCache.fetch(bitmapUrl, object : AlbumArtCache.FetchListener() {
            override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                if (currentSong == song) {
                    // If the media is still the same, update the notification:
                    Log.d(TAG, "fetchBitmapFromURLAsync: set bitmap to " + artUrl)
                    builder.setLargeIcon(bigImage)
                    mNotificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            }
        }, song.songKey)
    }

}
