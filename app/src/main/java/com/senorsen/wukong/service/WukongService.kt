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
import com.senorsen.wukong.network.AppCookies
import com.senorsen.wukong.network.HttpWrapper

class WukongService : Service() {

    lateinit var http: HttpWrapper
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
                currentUser = http.getUserInfo()
                Log.d(TAG, "User: " + currentUser.toString())
            } catch (e: HttpWrapper.UserUnauthorizedException) {
                messageHandler.post {
                    Toast.makeText(applicationContext, "Please sign in to continue.", Toast.LENGTH_SHORT).show()
                }
            }
        }).start().run { }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

}