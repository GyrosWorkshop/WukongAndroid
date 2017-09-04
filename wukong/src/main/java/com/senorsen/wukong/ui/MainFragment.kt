package com.senorsen.wukong.ui

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.zhaiyifan.lyric.LyricUtils
import cn.zhaiyifan.lyric.widget.LyricView
import com.senorsen.wukong.R
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlin.reflect.full.createInstance

class MainFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        view.findViewById<View>(R.id.song_list_shuffle).setOnClickListener {
            val childFragment = childFragmentManager.findFragmentByTag("SONGLIST")
            if (childFragment != null) {
                val songListFragment = childFragment as SongListFragment
                songListFragment.shuffleSongList()
            }
        }

        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction().replace(R.id.main_fragment_container, SongListFragment::class.createInstance(), "SONGLIST").commit()
        (activity as MainActivity).pullChannelInfo()
    }
}
