package com.senorsen.wukong.store

import android.content.Context
import android.graphics.Bitmap
import com.senorsen.wukong.media.AlbumArtCache
import com.senorsen.wukong.model.User

class UserInfoLocalStore(context: Context) : PrefLocalStore(context, PREF_NAME) {

    private val TAG = javaClass.simpleName

    fun save(avatar: Bitmap?) {
        pref.edit()
                .putString(KEY_PREF_USER_AVATAR, AlbumArtCache.bitMapToString(avatar))
                .apply()
    }

    fun save(user: User?) {
        saveToJson(KEY_PREF_USER, user)
    }

    fun load(): User? {
        return loadFromJson(KEY_PREF_USER, User::class.java)
    }

    fun loadUserAvatar() : Bitmap? {
        return AlbumArtCache.stringToBitMap(pref.getString(KEY_PREF_USER_AVATAR, ""))
    }

    companion object {
        private val PREF_NAME = "wukong"
        private val KEY_PREF_USER = "user"
        private val KEY_PREF_USER_AVATAR = "user_avatar"
    }
}
