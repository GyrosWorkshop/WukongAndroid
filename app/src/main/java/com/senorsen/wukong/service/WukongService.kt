package com.senorsen.wukong.service

import android.app.*
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
import android.support.v7.app.NotificationCompat
import com.senorsen.wukong.R
import com.senorsen.wukong.activity.MainActivity
import com.senorsen.wukong.network.*
import java.io.IOException


class WukongService : Service() {

    lateinit var http: HttpWrapper
    var socket: SocketWrapper? = null
    lateinit var currentUser: User

    val handler = Handler()
    var thread: Thread? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    private fun startConnect() {

    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val cookies = intent.getStringExtra("cookies")
        val channelId = intent.getStringExtra("channel")
        Log.d(TAG, "Channel: " + channelId)
        Log.d(TAG, "Cookies: " + cookies)
        http = HttpWrapper(cookies)

        if (thread != null && thread!!.isAlive) {
            Log.d(TAG, "Thread alive, interrupt")
            thread?.interrupt()
        }

        thread = Thread(Runnable {

            try {
                // Terminate previous connection.
                socket?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "disconnect exception", e)
            }

            try {
                currentUser = http.getUserInfo()
                Log.d(TAG, "User: " + currentUser.toString())

                http.channelJoin(channelId)

                val receiver = object : SocketWrapper.SocketReceiver {
                    override fun onEventMessage(protocol: WebSocketReceiveProtocol) {
                        when {
                            protocol.eventName == "Play" -> {
                                val song = protocol.song!!
                                handler.post {
                                    setNotification(song.title!!, "${song.artist} - ${song.album}")
                                }
                            }
                        }
                    }
                }
                try {
                    socket = SocketWrapper(ApiUrls.wsEndpoint, cookies, channelId, receiver, handler, applicationContext)
                } catch (e: IOException) {
                    Log.e(TAG, "socket exception: " + e.message)
                }

            } catch (e: HttpWrapper.UserUnauthorizedException) {
                handler.post {
                    Toast.makeText(applicationContext, "Please sign in to continue.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                handler.post {
                    e.printStackTrace()
                    Toast.makeText(applicationContext, "Unknown Exception: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        })
        thread!!.start()

        return START_STICKY
    }

    override fun onDestroy() {

        if (thread != null && thread!!.isAlive) {
            thread!!.interrupt()
        }

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
                .setContentTitle(title)
                .setContentText(content).build()
        notification.flags = Notification.FLAG_ONGOING_EVENT
        notificationManager.notify(1, notification)
        startForeground(1, notification)
    }

}