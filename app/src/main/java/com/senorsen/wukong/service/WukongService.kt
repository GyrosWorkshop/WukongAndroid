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
    lateinit var thread: Thread

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
        Log.d(TAG, "Cookies: " + cookies)
        http = HttpWrapper(cookies)

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

                http.channelJoin("test")

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
                while (true) {
                    Thread.sleep(3000)
                    Toast.makeText(applicationContext, "Reconnection...", Toast.LENGTH_SHORT).show()
                    try {
                        socket = SocketWrapper(ApiUrls.wsEndpoint, cookies, receiver, handler, applicationContext)
                    } catch (e: IOException) {
                        Log.e(TAG, "socket exception: " + e.message)
                    }
                }

            } catch (e: HttpWrapper.UserUnauthorizedException) {
                handler.post {
                    Toast.makeText(applicationContext, "Please sign in to continue.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(applicationContext, "Unknown Exception: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        })
        thread.start()

        return START_STICKY
    }

    override fun onDestroy() {

        Thread(Runnable {
            try {
                socket?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "disconnect exception", e)
            }
        })

        Thread.sleep(2000)

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