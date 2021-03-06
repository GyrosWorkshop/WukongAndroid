package com.senorsen.wukong.ui

import android.app.ActivityManager
import android.app.Fragment
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import com.senorsen.wukong.R
import com.senorsen.wukong.model.Configuration
import com.senorsen.wukong.model.Song
import com.senorsen.wukong.network.HttpClient
import com.senorsen.wukong.network.message.SongList
import com.senorsen.wukong.service.WukongService
import com.senorsen.wukong.store.ConfigurationLocalStore
import com.senorsen.wukong.store.SongListLocalStore
import jp.wasabeef.recyclerview.animators.SlideInRightAnimator
import java.lang.ref.WeakReference
import java.util.*
import kotlin.concurrent.thread


class SongListFragment : Fragment() {

    private val handler = Handler()
    val adapter = SongListAdapter(this)
    var connected = false
    var wukongService: WukongService? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var songListLocalStore: SongListLocalStore
    private lateinit var configurationStore: ConfigurationLocalStore
    val http: HttpClient
        get() = (activity as WukongActivity).http


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

            if (adapter.list?.isEmpty() != false) {
                fetchSongList()
            } else {
                // If song list in service is empty, service is not setup.
                // Otherwise, service is set up, and ui list may be old.
                if (wukongService.userSongList.isEmpty()) {
                    wukongService.userSongList = adapter.list!!.toMutableList()
                } else {
                    adapter.list = wukongService.userSongList
                    adapter.reloadFilteredList()
                }
            }
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

    fun bindService() {
        connected = false
        bindRunnable.run()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        recyclerView = inflater.inflate(R.layout.fragment_song_list, container, false) as RecyclerView
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = SlideInRightAnimator()
        recyclerView.addItemDecoration(DividerItemDecoration(activity))

        songListLocalStore = SongListLocalStore(this.activity)

        return recyclerView
    }

    fun isServiceStarted(): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className.contains(WukongService::class.simpleName!!) }
    }

    fun fetchConfiguration(): Configuration? {
        val configuration = try {
            http.getConfiguration()
        } catch (e: HttpClient.UserUnauthorizedException) {
            Log.d(TAG, e.message)
            null
        }
        if (configuration != null) {
            configurationStore.save(configuration)
        }
        return configuration
    }

    fun fetchSongList() {
        val view = activity.findViewById<CoordinatorLayout>(R.id.mainCoordinatorLayout)
        var configuration: Configuration? = configurationStore.load()
        thread {
            try {
                if (configuration?.syncPlaylists.isNullOrBlank()) {
                    configuration = fetchConfiguration() ?: throw Exception(getString(R.string.sync_playlists_configuration_error))
                }
                val list = http.getSongLists(configuration!!.syncPlaylists!!, configuration!!.cookies)
                handler.post {
                    if (list.isEmpty()) {
                        Snackbar.make(view, R.string.sync_playlists_empty_or_error, Snackbar.LENGTH_SHORT).show()
                    } else {
                        val listCount = list.size
                        val songCount = list.map { it.songCount!! }.reduce { a, b -> a + b }
                        Snackbar.make(view, getString(R.string.sync_playlists_result, listCount, songCount), Snackbar.LENGTH_SHORT).show()
                    }
                    adapter.list = list.flatMap { it.songs }
                    adapter.reloadFilteredList()
                }
                try {
                    wukongService?.userSongList = list.flatMap(SongList::songs).toMutableList()
                    wukongService?.doUpdateNextSong()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    Snackbar.make(view, getString(R.string.sync_playlists_error, e.message), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateSongListFromService() {
        if (wukongService?.userSongList?.isNotEmpty() ?: false) {
            adapter.list = wukongService!!.userSongList
            adapter.reloadFilteredList()
        }
    }

    fun shuffleSongList() {
        if (adapter.list != null) {
            val list = adapter.list!!
            Collections.shuffle(list, Random(System.nanoTime()))
            adapter.list = list
            adapter.reloadFilteredList()
            wukongService?.userSongList = list.toMutableList()
            wukongService?.doUpdateNextSong()
        }
    }

    fun clearSongList() {
        if (adapter.list != null) {
            adapter.list = mutableListOf()
            adapter.reloadFilteredList()
            wukongService?.userSongList = adapter.list!!.toMutableList()
            wukongService?.doUpdateNextSong()
        }
    }

    override fun onStart() {
        super.onStart()

        // Load local list first.
        val localList = songListLocalStore.load()
        if (localList != null) {
            adapter.list = localList.toMutableList()
            adapter.reloadFilteredList()
        }
        configurationStore = ConfigurationLocalStore(activity)
        bindService()
    }

    override fun onStop() {
        handler.removeCallbacks(bindRunnable)
        if (connected) activity.unbindService(serviceConnection)
        super.onStop()
    }

    inner class SongListAdapter(val fragment: SongListFragment) : RecyclerView.Adapter<SongListAdapter.ViewHolder>(), Filterable {

        var lastNeedle: String? = null

        var list: List<Song>? = null
            get() = field
            set(value) {
                field = value
                fragment.songListLocalStore.save(value)
            }

        private var filteredList: List<Song>? = null
            get() = field
            set(value) {
                field = value
            }

        fun reloadFilteredList() {
            filter.filter(lastNeedle ?: "")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.rootView.context)
            return ViewHolder(inflater.inflate(R.layout.item_song, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = filteredList!![position]
            holder.icon.text = list!!.indexOf(song).toString()
            holder.name.text = song.title
            holder.caption.text = "${song.artist} - ${song.album}"
            holder.songKey = song.songKey
        }

        override fun getItemCount() = filteredList?.size ?: 0

        override fun getFilter(): Filter {
            return object : Filter() {

                override fun performFiltering(constraint: CharSequence): FilterResults {
                    val needle = constraint.toString().toLowerCase()
                    lastNeedle = needle
                    val resultList = if (needle.isBlank()) {
                        list
                    } else {
                        list?.filter {
                            it.title?.toLowerCase()?.contains(needle) ?: false
                                    || it.album?.toLowerCase()?.contains(needle) ?: false
                                    || it.artist?.toLowerCase()?.contains(needle) ?: false
                                    || it.songId == needle
                        }
                    }
                    val filterResults = FilterResults()
                    filterResults.values = resultList
                    return filterResults
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence, results: FilterResults) {
                    filteredList = results.values as List<Song>?
                    notifyDataSetChanged()
                }
            }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val icon = view.findViewById<TextView>(android.R.id.icon)
            val name = view.findViewById<TextView>(android.R.id.text1)
            val caption = view.findViewById<TextView>(android.R.id.text2)
            val upIcon = view.findViewById<ImageView>(R.id.song_list_up)
            val removeIcon = view.findViewById<ImageView>(R.id.song_list_remove)
            var songKey: String = ""

            init {
                upIcon.setOnClickListener {
                    Log.d(TAG, "up $songKey")

                    val tempList = list!!.toMutableList()
                    val song = filteredList!!.find { it.songKey == songKey }!!

                    val index = adapterPosition
                    notifyItemMoved(index, 0)
                    notifyItemRangeChanged(0, index + 1, Object())
                    recyclerView.layoutManager.scrollToPosition(0)

                    tempList.remove(song)
                    tempList.add(0, song)
                    list = tempList
                    val tempFilteredList = filteredList!!.toMutableList()
                    tempFilteredList.remove(song)
                    tempFilteredList.add(0, song)
                    filteredList = tempFilteredList

                    fragment.wukongService?.userSongList = tempList
                    fragment.wukongService?.doUpdateNextSong()
                }

                removeIcon.setOnClickListener {
                    Log.d(TAG, "remove $songKey")
                    val tempList = list!!.toMutableList()
                    val song = filteredList!!.find { it.songKey == songKey }

                    val index = adapterPosition
                    notifyItemRemoved(index)
                    // If there are more elements after this, should update their index number.
                    if (filteredList!!.size > index) {
                        notifyItemRangeChanged(index, filteredList!!.size - index, Object())
                    }

                    tempList.remove(song)
                    list = tempList
                    val tempFilteredList = filteredList!!.toMutableList()
                    tempFilteredList.remove(song)
                    filteredList = tempFilteredList

                    fragment.wukongService?.userSongList = tempList
                    fragment.wukongService?.doUpdateNextSong()
                }
            }
        }
    }
}
