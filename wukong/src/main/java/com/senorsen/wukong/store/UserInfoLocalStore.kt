package com.senorsen.wukong.store

import android.content.Context
import android.graphics.Bitmap
import com.senorsen.wukong.media.AlbumArtCache
import com.senorsen.wukong.model.User
import com.senorsen.wukong.utils.ObjectSerializer
import java.io.Serializable

class UserInfoLocalStore(context: Context) : PrefLocalStore(context, PREF_NAME) {

    fun save(avatar: Bitmap?) {
        pref.edit()
                .putString(KEY_PREF_USER_AVATAR, AlbumArtCache.bitMapToString(avatar))
                .apply()
    }

    fun save(user: User?) {
        pref.edit()
                .putString(KEY_PREF_USER, ObjectSerializer.serialize(user))
                .apply()
    }

    fun load(): User? {
        @Suppress("UNCHECKED_CAST")
        return ObjectSerializer.deserialize(pref.getString(KEY_PREF_USER, "")) as User?
    }

    fun loadUserAvatar() : Bitmap? {
        @Suppress("UNCHECKED_CAST")
        return AlbumArtCache.stringToBitMap(pref.getString(KEY_PREF_USER_AVATAR, ""))
    }

    companion object {
        private val PREF_NAME = "wukong"
        private val KEY_PREF_USER = "user"
        private val KEY_PREF_USER_AVATAR = "user_avatar"
    }
}
