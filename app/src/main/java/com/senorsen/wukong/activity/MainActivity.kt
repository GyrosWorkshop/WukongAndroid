package com.senorsen.wukong.activity

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent.getActivity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
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

        val channelEdit = findViewById(R.id.channel_id) as EditText

        val startServiceButton = findViewById(R.id.start_service) as Button
        startServiceButton.setOnClickListener {
            if (cookies == null) {
                cookies = applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("cookies", "")
            }

            if (channelEdit.text.isNullOrBlank()) {
                channelEdit.error = "required"
                return@setOnClickListener
            }

            applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    .edit()
                    .putString("channel", channelEdit.text.toString()).apply()

            serviceIntent = Intent(this, WukongService::class.java)
                    .putExtra("cookies", cookies)
                    .putExtra("channel", channelEdit.text.toString().trim())
            startService(serviceIntent)
        }

        val stopServiceButton = findViewById(R.id.stop_service) as Button
        stopServiceButton.setOnClickListener {
            stopService(Intent(this, WukongService::class.java))
        }

        channelEdit.text = SpannableStringBuilder(applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", ""))
        channelEdit.setSelection(channelEdit.text.toString().length)

        mayRequestPermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_COOKIES -> {
                cookies = data!!.getStringExtra("cookies")
                Toast.makeText(applicationContext, "Logged in successfully.", Toast.LENGTH_SHORT).show()

                val sharedPref = applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                val editor = sharedPref.edit()
                        .remove("cookies")
                        .putString("cookies", cookies)
                        .apply()
            }
        }
    }

    private fun mayRequestPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (shouldShowRequestPermissionRationale(WRITE_EXTERNAL_STORAGE)) {

        } else {
            requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
        }
        requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
        return false
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mayRequestPermission()
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(R.string.permission_warning)
                        .setTitle(R.string.warning)
                        .setPositiveButton(R.string.ok) { dialog, id -> mayRequestPermission() }
                        .setNegativeButton(R.string.cancel) { dialog, id -> }
                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    companion object {
        private val REQUEST_WRITE_EXTERNAL_STORAGE = 0;

    }
}
