package com.senorsen.wukong.service

import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.senorsen.wukong.network.AppCookieJar

class WukongService : Service() {

    lateinit var cookieJar: AppCookieJar

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        cookieJar = AppCookieJar(applicationContext)
        Log.d(TAG, "Cookies: " + cookieJar.readCookieLineFromSharedPreferences().joinToString("; "))

        Log.d(TAG, "onStartCommand WukongService: " + this)
        val name = intent.getStringExtra("name")
        Log.d(TAG, "name: " + name)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }
}