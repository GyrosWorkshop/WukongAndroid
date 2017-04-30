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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        val startServiceButton = view.findViewById(R.id.start_service) as Button
        startServiceButton.setOnClickListener {
            (activity as MainActivity).startWukongService()
        }

        val stopServiceButton = view.findViewById(R.id.stop_service) as Button
        stopServiceButton.setOnClickListener {
            activity.stopService(Intent(activity, WukongService::class.java))
        }

        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction().replace(R.id.main_fragment_container, SongListFragment::class.createInstance(), "SONGLIST").commit()
    }
}
