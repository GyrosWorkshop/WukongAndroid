package com.senorsen.wukong.network

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import org.json.JSONObject


class TokenReceiver : BroadcastReceiver() {
    var authState: AuthState? = null
    var userInfo: JSONObject? = null
    override fun onReceive(context: Context?, intent: Intent?) {
        val extras = (intent?.extras)?.let { it } ?: return
        if (extras.containsKey(KEY_AUTH_STATE)) {
            authState = AuthState.jsonDeserialize(extras.getString(KEY_AUTH_STATE))
        }
        if (extras.containsKey(KEY_USER_INFO)) {
            userInfo = JSONObject(extras.getString(KEY_USER_INFO))
        }
    }

    companion object {
        public val KEY_AUTH_STATE = "authState"
        public val KEY_USER_INFO = "userInfo"
    }
}


fun createPostAuthenticationIntent(context: Context,
                                   request: AuthorizationRequest,
                                   authState: AuthState): PendingIntent {
    val intent = Intent(context, TokenReceiver.javaClass)
    intent.putExtra(TokenReceiver.KEY_AUTH_STATE, authState.jsonSerializeString())
    return PendingIntent.getBroadcast(context, request.hashCode(), intent, 0)
}
