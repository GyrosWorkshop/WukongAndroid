package com.senorsen.wukong.utils;

import kotlin.concurrent.thread

class Debounce(val delay: Long) {
    private var posted = false
    private var action: (() -> Unit)? = null
    val runnable = Runnable {
        Thread.sleep(delay)
        posted = false
        action?.invoke()
    }

    fun run(action: (() -> Unit)?) {
        this.action = action
        if (!posted) {
            posted = true
            thread { runnable.run() }
        }
    }
}
