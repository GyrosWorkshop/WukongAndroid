package com.senorsen.wukong.network

data class SongListWithUrlRequest(
        val url: String,
        val withCookie: String? = null
)
