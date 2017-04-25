package com.senorsen.wukong.network

import com.google.gson.Gson
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
        val elapsed: Double? = null,
        val user: String? = null,

        val users: List<User>? = null
) {

}