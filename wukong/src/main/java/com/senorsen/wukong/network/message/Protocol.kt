package com.senorsen.wukong.network.message

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
        val downvote: Boolean? = null,
        val elapsed: Float,
        val user: String? = null,

        val users: List<User>? = null,

        val notification: Notification? = null,

        val cause: String? = null
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

    val DISCONNECT: String
        get() = "Disconnect"

}

data class Notification(
        var message: String?,
        var timeout: Int
)