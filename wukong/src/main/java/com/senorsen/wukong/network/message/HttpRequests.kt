package com.senorsen.wukong.network.message

data class SongListWithUrlRequest(
        val url: String,
        val withCookie: String? = null
)
