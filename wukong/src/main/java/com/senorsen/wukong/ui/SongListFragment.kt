package com.senorsen.wukong.ui

import android.app.ActivityManager
import android.app.Fragment
import android.content.ComponentName
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
import com.senorsen.wukong.network.SongList
import com.senorsen.wukong.service.WukongService


class SongListFragment : Fragment() {

    private val handler = Handler()
    private val adapter = SongListAdapter()
    private var connected = false

    val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {

        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected = true
            fetchSongList((service as WukongService.WukongServiceBinder).getService())
        }
    }

    val bindRunnable = object : Runnable {
        override fun run() {
            if (isServiceStarted()) activity.bindService(Intent(activity, WukongService::class.java), serviceConnection, 0)
            else {
                handler.postDelayed(this, 500)
                Log.d(SongListFragment::class.simpleName, "delayed")
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        val recyclerView = inflater.inflate(R.layout.fragment_song_list, container, false) as RecyclerView
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setHasFixedSize(true)
        return recyclerView
    }

    fun isServiceStarted(): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className.contains(WukongService::class.simpleName!!) }
    }

    fun fetchSongList(service: WukongService) {
        object : AsyncTask<Void, Void, SongList>() {
            override fun doInBackground(vararg params: Void?): SongList {
                val configuration = service.getConfiguration()
                return service.getSongList(configuration.syncPlaylists!!, configuration.cookies!!)
            }

            override fun onPostExecute(result: SongList?) {
                super.onPostExecute(result)
                adapter.list = result
            }
        }.execute()
    }

    override fun onStart() {
        super.onStart()
        bindRunnable.run()
    }

    override fun onStop() {
        super.onStop()
        if (connected) activity.unbindService(serviceConnection)
    }

    private class SongListAdapter : RecyclerView.Adapter<SongListAdapter.ViewHolder>() {

        var list: SongList? = null
            get() = field
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = list?.songs?.get(position) ?: return
            holder.name.text = song.title
            holder.caption.text = "${song.artist}:${song.album}"
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.rootView.context)
            return ViewHolder(inflater.inflate(R.layout.item_song, parent, false))
        }

        override fun getItemCount() = list?.songCount ?: 0

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val icon = view.findViewById(android.R.id.icon) as ImageView
            val name = view.findViewById(android.R.id.text1) as TextView
            val caption = view.findViewById(android.R.id.text2) as TextView
            val upIcon = view.findViewById(R.id.song_list_up) as ImageView
            val removeIcon = view.findViewById(R.id.song_list_remove) as ImageView


        }
    }
}