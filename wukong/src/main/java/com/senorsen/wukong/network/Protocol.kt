package com.senorsen.wukong.network

import com.senorsen.wukong.model.Song
import com.senorsen.wukong.model.User

data class WebSocketTransmitProtocol(
        val channelId: String,
        val eventName: String
)

data class WebSocketReceiveProtocol(
        val channelId: String? = null,
        val eventName: String? = null,

        val song: Song? = null,
        val downVote: Boolean? = null,
        val elapsed: Float? = null,
        val user: String? = null,

        val users: List<User>? = null,

        val notification: Notification? = null
)

object Protocol {

    val PLAY: String
        get() = "Play"

    val PRELOAD: String
        get() = "Preload"

    val USER_LIST_UPDATE: String
        get() = "UserListUpdated"

    val NOTIFICATION: String
        get() = "Notification"

}

data class Notification(
        var message: String?,
        var timeout: Int
)