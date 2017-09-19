package com.senorsen.wukong.network.message

import com.senorsen.wukong.model.OtherSiteUser
import com.senorsen.wukong.model.Song

data class SongList(
        var siteId: String? = null,
        var songListId: String? = null,
        var cover: String? = null,
        var createTime: String? = null,
        var creator: OtherSiteUser? = null,
        var description: String? = null,
        var name: String? = null,
        var playCount: Int? = null,
        var songCount: Int? = null,
        var songs: List<Song>
)