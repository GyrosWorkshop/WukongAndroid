package com.senorsen.wukong.network

object ApiUrls {

    // Base url, hostname, etc.
    val hostname = "wukong.azurewebsites.net"
    val base = "https://$hostname"

    // API endpoints.
    val apiEndpoint = "$base/api"
    val wsEndpoint = "wss://$hostname/api/ws"

    val userInfoEndpoint = "$apiEndpoint/user/userinfo"
    val userConfigurationUri = "$apiEndpoint/user/configuration"

    val channelJoinEndpoint = "$apiEndpoint/channel/join"
    val channelReportFinishedEndpoint = "$apiEndpoint/channel/finished"
    val channelUpdateNextSongEndpoint = "$apiEndpoint/channel/updateNextSong"
    val channelVoteDownUri = "$apiEndpoint/channel/downVote"

    // Music Provider API endpoints.
    val providerEndpoint = "$base/provider"
    val providerSearchSongsEndpoint = "$providerEndpoint/searchSongs"
    val providerSongInfoEndpoint = "$providerEndpoint/songInfo"
    val providerSongListWithUrlEndpoint = "$providerEndpoint/songListWithUrl"

    // OAuth endpoints.
    val oAuthEndpoint = "$base/oauth"
    val oAuthMethodsEndpoint = "$oAuthEndpoint/all"
    val oAuthRedirectEndpoint = "$oAuthEndpoint/go"

}