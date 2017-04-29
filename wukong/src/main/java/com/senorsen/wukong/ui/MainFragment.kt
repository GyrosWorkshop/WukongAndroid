package com.senorsen.wukong.ui

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.senorsen.wukong.R
import com.senorsen.wukong.service.WukongService
import kotlin.reflect.full.createInstance

class MainFragment : Fragment() {

    lateinit var serviceIntent: Intent

    private val REQUEST_COOKIES = 0

    var cookies: String? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        when (requestCode) {
            REQUEST_COOKIES -> {
                cookies = data.getStringExtra("cookies")
                Toast.makeText(activity.applicationContext, "Sign in successfully.", Toast.LENGTH_SHORT).show()

                val sharedPref = activity.applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                sharedPref.edit().putString("cookies", cookies).apply()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        view.findViewById(R.id.sign_in).setOnClickListener {
            startActivityForResult(Intent(activity, WebViewActivity::class.java), REQUEST_COOKIES)
        }

        val channelEdit = view.findViewById(R.id.channel_id) as EditText

        val startServiceButton = view.findViewById(R.id.start_service) as Button
        startServiceButton.setOnClickListener {
            startService(view)
        }

        val stopServiceButton = view.findViewById(R.id.stop_service) as Button
        stopServiceButton.setOnClickListener {
            activity.stopService(Intent(activity, WukongService::class.java))
        }

        channelEdit.text = SpannableStringBuilder(activity.applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", ""))
        channelEdit.setSelection(channelEdit.text.toString().length)

        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction().add(R.id.main_fragment_container, SongListFragment::class.createInstance()).commit()
    }

    fun startService(view: View, restart: Boolean = false) {
        val channelEdit = view.findViewById(R.id.channel_id) as EditText
        if (cookies == null) {
            cookies = activity.applicationContext
                    .getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    .getString("cookies", "")
        }

        if (channelEdit.text.isNullOrBlank()) {
            channelEdit.error = "required"
            return
        }

        activity.applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                .edit()
                .putString("channel", channelEdit.text.toString()).apply()

        activity.stopService(Intent(activity, WukongService::class.java))

        serviceIntent = Intent(activity, WukongService::class.java)
                .putExtra("cookies", cookies)
                .putExtra("channel", channelEdit.text.toString().trim())
        activity.startService(serviceIntent)
    }
}
