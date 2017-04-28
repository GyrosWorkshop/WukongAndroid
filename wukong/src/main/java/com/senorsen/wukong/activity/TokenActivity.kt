package com.senorsen.wukong.activity

import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService




class TokenActivity: AppCompatActivity {
    const val
    val authService = AuthorizationService(this)
    var authState: AuthState? = null

    public override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        if (savedInstanceState?.containsKey())
    }
}