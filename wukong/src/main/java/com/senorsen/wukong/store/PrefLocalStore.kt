package com.senorsen.wukong.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.lang.reflect.Type

abstract class PrefLocalStore {
    protected val pref: SharedPreferences

    constructor(context: Context) {
        pref = PreferenceManager.getDefaultSharedPreferences(context)
    }

    constructor(context: Context, name: String) {
        pref = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun load(key: String, default: String = ""): String {
        return pref.getString(key, default)
    }

    fun save(key: String, value: String) {
        pref.edit().putString(key, value).apply()
    }
}