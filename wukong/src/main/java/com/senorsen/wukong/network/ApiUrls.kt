package com.senorsen.wukong.network

object ApiUrls {

    // Dynamic Api base.
    val dynamicApiBaseUrl = "https://redir.senorsen.com/WukongApi?detail=1"

    // Base url, hostname, etc.
    lateinit var base: String

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
    val providerEndpoint: String
        get() = "$base/provider"
    val providerSearchSongsEndpoint: String
        get() = "$providerEndpoint/searchSongs"
    val providerSongInfoEndpoint: String
        get() = "$providerEndpoint/songInfo"
    val providerSongListWithUrlEndpoint: String
        get() = "$providerEndpoint/songListWithUrl"

    // OAuth endpoint.
    val oAuthEndpoint: String
        get() = "$base/oauth/go/OpenIdConnect?redirectUri=/"

}