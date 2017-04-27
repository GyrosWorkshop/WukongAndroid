package com.senorsen.wukong.service

import android.app.*
import android.app.Notification
import android.content.ContentValues.TAG
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.senorsen.wukong.model.User
import android.content.Context
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.support.v4.app.NotificationCompat
import com.senorsen.wukong.R
import com.senorsen.wukong.activity.MainActivity
import com.senorsen.wukong.network.*
import java.io.IOException
import javax.security.auth.callback.Callback
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import com.senorsen.wukong.model.getUserFromList


class WukongService : Service() {

    private val NOTIFICATION_ID = 1

    lateinit var http: HttpWrapper
    var socket: SocketWrapper? = null
    lateinit var currentUser: User

    val handler = Handler()
    var thread: Thread? = null
    lateinit var threadHandler: Handler
    lateinit var mediaPlayer: MediaPlayer
    lateinit var wifiLock: WifiLock

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        mediaPlayer = MediaPlayer()
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

        wifiLock = (getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL, getString(R.string.app_name))

        wifiLock.acquire()

    }

    private fun stopPrevConnect() {
        if (thread != null) {
            threadHandler.post {
                Log.i(TAG, "socket disconnect")
                socket?.disconnect()
            }
            Thread.sleep(1000)
            thread?.interrupt()
        }


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

        thread = Thread(Runnable {

            Looper.prepare()

            threadHandler = Handler()

            try {
                currentUser = http.getUserInfo()
                Log.d(TAG, "User: " + currentUser.toString())

                var userList: ArrayList<User>? = null

                val receiver = object : SocketWrapper.SocketReceiver {
                    override fun onEventMessage(protocol: WebSocketReceiveProtocol) {
                        when (protocol.eventName) {

                            Protocol.USER_LIST_UPDATE -> {
                                userList = protocol.users!! as ArrayList<User>
                            }

                            Protocol.PRELOAD -> {
                            }

                            Protocol.PLAY -> {
                                val song = protocol.song!!
                                setNotification(song.title!!, "${song.artist} - ${song.album}\nby ${getUserFromList(userList, protocol.user)?.userName}")
                                val originalUrl = song.musics!!.sortedByDescending { it.audioBitrate }.first().file!!
                                Log.d(TAG, "originalUrl: " + originalUrl)
                                val mediaUrl = MediaProvider().resolveRedirect(originalUrl)
                                Log.d(TAG, "mediaUrl: " + mediaUrl)
                                handler.post {
                                    mediaPlayer.setDataSource(mediaUrl)
                                    mediaPlayer.prepare()
                                    mediaPlayer.seekTo((protocol.elapsed!! * 1000).toInt())
                                    mediaPlayer.start()
                                    mediaPlayer.setOnCompletionListener {
                                        Log.d(TAG, "finished")
                                        threadHandler.post {
                                            http.reportFinish(song.toRequestSong())
                                        }
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
                    socket = SocketWrapper(ApiUrls.wsEndpoint, cookies, channelId, reconnectCallback as SocketWrapper.Callback, receiver, handler, applicationContext)
                }

                reconnectCallback = object : SocketWrapper.Callback {
                    override fun call() {
                        Thread.sleep(3000)
                        handler.post {
                            Toast.makeText(applicationContext, "Wukong: Reconnecting...", Toast.LENGTH_SHORT).show()
                        }
                        doConnect()
                    }
                }

                try {
                    doConnect()
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
        thread!!.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        this.startConnect(intent)

        return START_STICKY
    }

    override fun onDestroy() {

        stopPrevConnect()

        mediaPlayer.release()
        wifiLock.release()

        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    fun setNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java)
        val contextIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this)
                .setContentIntent(contextIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentTitle(title)
                .setContentText(content).build()
        notification.flags = NotificationCompat.FLAG_ONGOING_EVENT
        notificationManager.notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

}