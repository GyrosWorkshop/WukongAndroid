package com.senorsen.wukong.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.widget.Button
import com.senorsen.wukong.R
import com.senorsen.wukong.service.WukongService

class MainActivity : AppCompatActivity() {

    lateinit var serviceIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        findViewById(R.id.sign_in).setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        val startServiceButton = findViewById(R.id.start_service) as Button
        startServiceButton.setOnClickListener {
            serviceIntent = Intent(this, WukongService::class.java)
            startService(serviceIntent)
        }

        val stopServiceButton = findViewById(R.id.stop_service) as Button
        stopServiceButton.setOnClickListener {
            stopService(serviceIntent)
        }


    }
}
