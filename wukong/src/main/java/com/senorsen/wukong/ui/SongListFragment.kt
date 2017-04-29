package com.senorsen.wukong.ui

import android.app.ActivityManager
import android.app.Fragment
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.senorsen.wukong.R
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.network.SongList
import com.senorsen.wukong.service.WukongService
import com.senorsen.wukong.utils.ObjectSerializer
import kotlin.concurrent.thread


class SongListFragment : Fragment() {

    private val handler = Handler()
    private val adapter = SongListAdapter(this)
    private var connected = false
    private var wukongService: WukongService? = null

    val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
            wukongService = null
            bindService()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected = true
            val wukongService = (service as WukongService.WukongServiceBinder).getService()
            this@SongListFragment.wukongService = wukongService
            wukongService.songListUpdateCallback = this@SongListFragment::onSongListUpdate

            if (adapter.list == null) {
                fetchSongList(wukongService)
            } else {
                wukongService.userSongList = adapter.list!!
                if (wukongService.connected)
                    wukongService.doUpdateNextSong()
            }
        }
    }

    fun onSongListUpdate(list: List<Song>) {
        Log.d(SongListFragment::class.simpleName, "onSongListUpdate ${list.firstOrNull()}")
        adapter.list = list.toMutableList()
    }

    val bindRunnable = object : Runnable {
        override fun run() {
            if (isServiceStarted()) activity.bindService(Intent(activity, WukongService::class.java), serviceConnection, 0)
            else {
                handler.postDelayed(this, 1000)
                Log.d(SongListFragment::class.simpleName, "delayed")
            }
        }
    }

    // Workaround for type recursive.
    fun bindService() {
        bindRunnable.run()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        val recyclerView = inflater.inflate(R.layout.fragment_song_list, container, false) as RecyclerView
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setHasFixedSize(true)

        @Suppress("UNCHECKED_CAST")
        val localList = (ObjectSerializer.deserialize(activity.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("song_list", "")) as Array<Song>?)?.toMutableList()
        if (localList != null)
            adapter.list = localList
        Log.d(SongListFragment::class.simpleName, "localList $localList")

        return recyclerView
    }

    fun isServiceStarted(): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className.contains(WukongService::class.simpleName!!) }
    }

    fun fetchSongList(service: WukongService) {
        object : AsyncTask<Void, Void, List<Song>>() {
            override fun doInBackground(vararg params: Void?): List<Song>? {
                val configuration = service.fetchConfiguration()
                if (configuration != null && configuration.syncPlaylists != null) {
                    return service.getSongLists(configuration.syncPlaylists!!, configuration.cookies)
                } else {
                    return listOf()
                }
            }

            override fun onPostExecute(result: List<Song>?) {
                super.onPostExecute(result)
                adapter.list = result?.toMutableList()
                service.doUpdateNextSong()
            }
        }.execute()
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        super.onStop()
        if (connected) activity.unbindService(serviceConnection)
    }

    private class SongListAdapter(val fragment: SongListFragment) : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

        var list: MutableList<Song>? = null
            get() = field
            set(value) {
                field = value
                saveSongList(value)
                notifyDataSetChanged()
            }

        private fun saveSongList(songList: List<Song>?) {
            fragment.activity.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    .edit()
                    .putString("song_list", ObjectSerializer.serialize(songList?.toTypedArray()))
                    .apply()
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = list?.get(position) ?: return
            holder.name.text = song.title
            holder.caption.text = "${song.artist} - ${song.album}"
            holder.id = position
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.rootView.context)
            return ViewHolder(inflater.inflate(R.layout.item_song, parent, false))
        }

        override fun getItemCount() = list?.size ?: 0

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val icon = view.findViewById(android.R.id.icon) as ImageView
            val name = view.findViewById(android.R.id.text1) as TextView
            val caption = view.findViewById(android.R.id.text2) as TextView
            val upIcon = view.findViewById(R.id.song_list_up) as ImageView
            val removeIcon = view.findViewById(R.id.song_list_remove) as ImageView
            var id: Int = -1

            init {
                upIcon.setOnClickListener {
                    Log.d(TAG, "up $id")
                    val tempList = list!!
                    val song = tempList[id]
                    tempList.remove(song)
                    tempList.add(0, song)
                    list = tempList

                    fragment.wukongService?.userSongList = list!!
                    fragment.wukongService?.doUpdateNextSong()
                }

                removeIcon.setOnClickListener {
                    Log.d(TAG, "remove $id")
                    val tempList = list!!
                    val song = tempList[id]
                    tempList.remove(song)
                    list = tempList

                    fragment.wukongService?.userSongList = list!!
                    fragment.wukongService?.doUpdateNextSong()
                }
            }

        }
    }
}