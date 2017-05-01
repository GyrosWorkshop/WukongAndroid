package com.senorsen.wukong.model

import java.io.Serializable

data class User(
        var id: String? = null,
        var userName: String? = null,
        var displayName: String? = null,
        var avatar: String? = null,
        var fromSite: String? = null,
        var siteUserId: String? = null,
        var url: String? = null
) : Serializable {
    companion object {
        fun getUserFromList(userList: List<User>?, id: String?): User? {
            return userList?.find { it.id == id }
        }
    }
}


data class OtherSiteUser(
        var siteId: String? = null,
        var userId: String? = null,
        var name: String? = null,
        var signature: String? = null,
        var gender: Int? = null,
        var avatar: String? = null
)

data class Configuration(
        var cookies: String? = null,
        var syncPlaylists: String? = null
) : Serializable