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
import android.widget.Toast
import com.senorsen.wukong.R
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.service.WukongService
import com.senorsen.wukong.store.SongListLocalStore
import com.senorsen.wukong.utils.ObjectSerializer
import java.util.*


class SongListFragment : Fragment() {

    private val handler = Handler()
    private val adapter = SongListAdapter(this)
    var connected = false
    var wukongService: WukongService? = null
    private lateinit var songListLocalStore: SongListLocalStore

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
                fetchSongList()
            } else {
                // If song list in service is empty, service is not setup.
                // Otherwise, service is set up, and ui list may be old.
                if (wukongService.userSongList.isEmpty()) {
                    wukongService.userSongList = adapter.list!!.toMutableList()
                } else {
                    adapter.list = wukongService.userSongList
                }
                if (wukongService.connected)
                    wukongService.doUpdateNextSong()
            }
        }
    }

    fun onSongListUpdate(list: List<Song>) {
        handler.post {
            Log.d(SongListFragment::class.simpleName, "onSongListUpdate ${list.firstOrNull()}")
            adapter.list = list.toMutableList()
        }
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

        songListLocalStore = SongListLocalStore(this.activity)

        return recyclerView
    }

    fun isServiceStarted(): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className.contains(WukongService::class.simpleName!!) }
    }

    fun fetchSongList() {
        if (wukongService == null) {
            Toast.makeText(activity, "Wukong: service not start, cannot fetch", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(activity, "Wukong: sync...", Toast.LENGTH_SHORT).show()

        object : AsyncTask<Void, Void, List<Song>>() {
            override fun doInBackground(vararg params: Void?): List<Song>? {
                val configuration = wukongService!!.fetchConfiguration()
                if (configuration != null && configuration.syncPlaylists != null) {
                    return wukongService!!.getSongLists(configuration.syncPlaylists!!, configuration.cookies)
                } else {
                    return listOf()
                }
            }

            override fun onPostExecute(result: List<Song>?) {
                super.onPostExecute(result)
                adapter.list = result?.toMutableList()
                wukongService!!.doUpdateNextSong()
            }
        }.execute()
    }

    fun updateSongListFromService() {
        if (wukongService?.userSongList?.isNotEmpty() ?: false) {
            adapter.list = wukongService!!.userSongList
        }
    }

    fun shuffleSongList() {
        if (adapter.list != null) {
            val list = adapter.list!!
            Collections.shuffle(list, Random(System.nanoTime()))
            adapter.list = list
            wukongService?.userSongList = list.toMutableList()
            wukongService?.doUpdateNextSong()
        }
    }

    fun clearSongList() {
        if (adapter.list != null) {
            adapter.list = mutableListOf()
            wukongService?.userSongList = adapter.list!!.toMutableList()
            wukongService?.doUpdateNextSong()
        }
    }

    override fun onStart() {
        super.onStart()

        // Load local list first.
        val localList = songListLocalStore.load()
        if (localList != null)
            adapter.list = localList.toMutableList()

        bindService()
    }

    override fun onStop() {
        wukongService?.songListUpdateCallback = null
        handler.removeCallbacks(bindRunnable)
        if (connected) activity.unbindService(serviceConnection)
        super.onStop()
    }

    private class SongListAdapter(val fragment: SongListFragment) : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

        var list: List<Song>? = null
            get() = field
            set(value) {
                field = value
                notifyDataSetChanged()
                fragment.songListLocalStore.save(value)
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
                    val tempList = list!!.toMutableList()
                    val song = tempList[id]
                    tempList.remove(song)
                    tempList.add(0, song)
                    list = tempList

                    fragment.wukongService?.userSongList = tempList
                    fragment.wukongService?.doUpdateNextSong()
                }

                removeIcon.setOnClickListener {
                    Log.d(TAG, "remove $id")
                    val tempList = list!!.toMutableList()
                    val song = tempList[id]
                    tempList.remove(song)
                    list = tempList

                    fragment.wukongService?.userSongList = tempList
                    fragment.wukongService?.doUpdateNextSong()
                }
            }

        }
    }
}