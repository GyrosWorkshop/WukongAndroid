package com.senorsen.wukong.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.SpannableStringBuilder
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.senorsen.wukong.R
import com.senorsen.wukong.service.WukongService

class MainActivity : AppCompatActivity() {

    lateinit var serviceIntent: Intent

    private val REQUEST_COOKIES = 0

    var cookies: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        findViewById(R.id.sign_in).setOnClickListener {
            startActivityForResult(Intent(this, WebViewActivity::class.java), REQUEST_COOKIES)
        }

        val channelText = findViewById(R.id.channel_id) as EditText

        val startServiceButton = findViewById(R.id.start_service) as Button
        startServiceButton.setOnClickListener {
            if (cookies == null) {
                cookies = applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("cookies", "")
            }

            if (channelText.text.isNullOrBlank()) {
                channelText.error = "required"
                return@setOnClickListener
            }

            applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    .edit()
                    .putString("channel", channelText.text.toString()).apply()

            serviceIntent = Intent(this, WukongService::class.java)
                    .putExtra("cookies", cookies)
                    .putExtra("channel", channelText.text.toString().trim())
            startService(serviceIntent)
        }

        val stopServiceButton = findViewById(R.id.stop_service) as Button
        stopServiceButton.setOnClickListener {
            stopService(serviceIntent)
        }

        channelText.text = SpannableStringBuilder(applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", ""))

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_COOKIES -> {
                cookies =  data!!.getStringExtra("cookies")
                Toast.makeText(applicationContext, "Logged in successfully.", Toast.LENGTH_SHORT).show()

                val sharedPref = applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                val editor = sharedPref.edit()
                        .remove("cookies")
                        .putString("cookies", cookies)
                        .apply()
            }
        }
    }
}
