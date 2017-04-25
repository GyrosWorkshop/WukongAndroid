package com.senorsen.wukong.service

import android.app.IntentService
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.AsyncTask
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.senorsen.wukong.model.User
import com.senorsen.wukong.network.ApiUrls
import com.senorsen.wukong.network.AppCookies
import com.senorsen.wukong.network.HttpWrapper
import com.senorsen.wukong.network.SocketWrapper

class WukongService : Service() {

    lateinit var http: HttpWrapper
    var socket: SocketWrapper? = null
    lateinit var currentUser: User

    val messageHandler = Handler()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val cookies = AppCookies(applicationContext).readCookiesFromSharedPreferences()
        Log.d(TAG, "Cookies: " + cookies)
        http = HttpWrapper(cookies)

        Thread(Runnable {

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
                socket = SocketWrapper(ApiUrls.wsEndpoint, cookies, messageHandler, applicationContext)

            } catch (e: HttpWrapper.UserUnauthorizedException) {
                messageHandler.post {
                    Toast.makeText(applicationContext, "Please sign in to continue.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                messageHandler.post {
                    Toast.makeText(applicationContext, "Unknown Exception: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }).start().run { }

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

}