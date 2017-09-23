package com.senorsen.wukong.ui

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.senorsen.wukong.R
import kotlin.reflect.full.createInstance

class MainFragment : Fragment() {

    private val TAG = javaClass.simpleName

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        with(view) {
            findViewById<View>(R.id.song_list_shuffle).setOnClickListener {
                val childFragment = childFragmentManager.findFragmentByTag("SONGLIST")
                if (childFragment != null) {
                    val songListFragment = childFragment as SongListFragment
                    songListFragment.shuffleSongList()
                }
            }

            findViewById<View>(R.id.play_switch_button).setOnClickListener {
                val wukongService = (activity as WukongActivity).wukongService
                if (wukongService != null) {
                    if (wukongService.isPaused)
                        wukongService.switchPlay()
                    else
                        wukongService.switchPause()
                }
            }

            findViewById<View>(R.id.downvote_button).setOnClickListener {
                val wukongService = (activity as WukongActivity).wukongService
                val requestSong = wukongService?.currentSong?.toRequestSong()
                if (requestSong != null)
                    wukongService.sendDownvote(requestSong)
            }
        }
        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction().replace(R.id.main_fragment_container, SongListFragment::class.createInstance(), "SONGLIST").commit()
        (activity as WukongActivity).pullChannelInfo()
    }
}
