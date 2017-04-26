package com.senorsen.wukong.service

import android.app.*
import android.app.Notification
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.AsyncTask
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.senorsen.wukong.model.User
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Looper
import android.support.v4.app.NotificationCompat
import com.senorsen.wukong.R
import com.senorsen.wukong.activity.MainActivity
import com.senorsen.wukong.network.*
import java.io.IOException
import javax.security.auth.callback.Callback


class WukongService : Service() {

    lateinit var http: HttpWrapper
    var socket: SocketWrapper? = null
    lateinit var currentUser: User

    val handler = Handler()
    var thread: Thread? = null
    lateinit var threadHandler: Handler

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
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

                http.channelJoin(channelId)

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
                                setNotification(song.title!!, "${song.artist} - ${song.album}\nby ${userList?.filter { it.id == protocol.user }?.first()?.userName}")
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
        notificationManager.notify(1, notification)
        startForeground(1, notification)
    }

}