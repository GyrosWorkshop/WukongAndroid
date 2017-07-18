package com.senorsen.wukong.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.google.gson.Gson
import java.lang.reflect.Type

abstract class PrefLocalStore {

    private val TAG = javaClass.simpleName
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

    fun <T> loadFromJson(key: String, classOfT: Class<T>): T? {
        try {
            return Gson().fromJson(pref.getString(key, "null"), classOfT)
        } catch (e: Exception) {
            Log.e(TAG, "load")
            e.printStackTrace()
            return null
        }
    }

    fun <T> loadFromJson(key: String, type: Type): T? {
        try {
            return Gson().fromJson(pref.getString(key, "null"), type)
        } catch (e: Exception) {
            Log.e(TAG, "load")
            e.printStackTrace()
            return null
        }
    }

    fun save(key: String, value: String) {
        pref.edit().putString(key, value).apply()
    }

    fun saveToJson(key: String, obj: Any?) {
        pref.edit().putString(key, Gson().toJson(obj)).apply()
    }

}