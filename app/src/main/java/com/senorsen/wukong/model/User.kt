package com.senorsen.wukong.model

import com.google.gson.Gson

data class User(
        var id: String? = null,
        var userName: String? = null,
        var displayName: String? = null,
        var avatar: String? = null,
        var fromSite: String? = null,
        var siteUserId: String? = null,
        var url: String? = null
) {

}