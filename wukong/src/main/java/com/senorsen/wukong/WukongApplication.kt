package com.senorsen.wukong

import android.app.Application
import com.squareup.leakcanary.LeakCanary

class WukongApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (LeakCanary.isInAnalyzerProcess(this))
            return
        LeakCanary.install(this)
    }
}
