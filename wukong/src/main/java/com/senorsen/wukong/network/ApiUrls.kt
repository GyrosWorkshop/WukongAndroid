package com.senorsen.wukong.network

object ApiUrls {

    // Dynamic Api base.
    fun dynamicBaseUrl(key: String) = "https://redir.senorsen.com/$key?detail=1"

    // Message Api.
    val messageApiUrl = "https://redir.senorsen.com/messages/wukong"

    // Base url, hostname, etc.
    var base: String = "https://api.wukongmusic.us:443" // Default, will be dynamically updated.
    var baseDebug: String = "http://172.22.5.16:5000"

    // API endpoints.
    val apiEndpoint: String
        get() = "$base/api"
    val wsEndpoint: String
        get() = base.replaceFirst("http", "ws") + "/api/ws"

    val userInfoEndpoint: String
        get() = "$apiEndpoint/user/userinfo"
    val userConfigurationUri: String
        get() = "$apiEndpoint/user/configuration"

    val channelJoinEndpoint: String
        get() = "$apiEndpoint/channel/join"
    val channelReportFinishedEndpoint: String
        get() = "$apiEndpoint/channel/finished"
    val channelUpdateNextSongEndpoint: String
        get() = "$apiEndpoint/channel/updateNextSong"
    val channelDownvoteUri: String
        get() = "$apiEndpoint/channel/downVote"

    // Music Provider API endpoints.
    var providerEndpoint = "https://api2.wukongmusic.us/provider" // Default, will be dynamically updated.
    val providerSearchSongsEndpoint: String
        get() = "$providerEndpoint/searchSongs"
    val providerSongInfoEndpoint: String
        get() = "$providerEndpoint/songInfo"
    val providerSongListWithUrlEndpoint: String
        get() = "$providerEndpoint/songListWithUrl"

    // OAuth endpoint.
    val oAuthEndpoint: String
        get() = "$base/oauth/go/Google?redirectUri=/"

}