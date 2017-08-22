package com.senorsen.wukong.network

import android.content.Context
import android.os.Handler
import android.util.Log
import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.senorsen.wukong.BuildConfig
import com.senorsen.wukong.network.message.WebSocketReceiveProtocol
import okhttp3.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class SocketClient(
        private val wsUrl: String,
        val cookies: String,
        private val channelId: String,
        val reconnectCallback: Callback,
        val socketReceiver: SocketReceiver,
        val handler: Handler,
        val applicationContext: Context
) {

    private val TAG = javaClass.simpleName

    private val userAgent = "WukongAndroid/${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    var ws: WebSocket? = null

    companion object {
        val CLOSE_NORMAL_CLOSURE = 1000
        val CLOSE_GOING_AWAY = 1001
    }


    val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()

    val request = Request.Builder()
            .header(HttpHeaders.COOKIE, cookies)
            .header(HttpHeaders.USER_AGENT, userAgent)
            .url(wsUrl).build()
    var listener: ActualWebSocketListener? = null

    init {
        connect()
    }

    @Synchronized
    fun connect() {
        Log.i(TAG, "Connect ws: $wsUrl")
        listener?.reconnectCallBack = null
        ws?.close(CLOSE_GOING_AWAY, "Going away")
        listener = ActualWebSocketListener(socketReceiver, reconnectCallback, handler, applicationContext)
        ws = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        ws?.close(CLOSE_NORMAL_CLOSURE, "Bye")
    }

    interface SocketReceiver {
        fun onEventMessage(protocol: WebSocketReceiveProtocol)
    }

    interface Callback {
        fun call()
    }

    inner class ActualWebSocketListener(private val socketReceiver: SocketReceiver,
                                        var reconnectCallBack: Callback?,
                                        private val handler: Handler,
                                        private val applicationContext: Context) : WebSocketListener() {

        @Volatile var alonePingCount = 0
        @Volatile var recentlySendPingCount = 0

        private lateinit var executor: ScheduledThreadPoolExecutor

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket open")
            executor = ScheduledThreadPoolExecutor(1)
            executor.scheduleAtFixedRate(PingPongCheckerRunnable(), 20, 20, TimeUnit.SECONDS)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Receiving: " + text)
            val receiveProtocol = Gson().fromJson(text, WebSocketReceiveProtocol::class.java)
            socketReceiver.onEventMessage(receiveProtocol)
        }

        override fun onPing(webSocket: WebSocket) {
            this.alonePingCount++;
            recentlySendPingCount++
            Log.d(TAG, "ping sent")
        }

        override fun onPong(webSocket: WebSocket, sendPingCount: Int, pongCount: Int) {
            alonePingCount = 0
            Log.d(TAG, "pong received from server, alone $alonePingCount, sent $sendPingCount received $pongCount.")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closing: $code $reason")
            Log.i(TAG, "Reconnection")
            executor.shutdown()
            if (code != CLOSE_NORMAL_CLOSURE) {
                reconnectCallBack?.call()
                reconnectCallBack = null
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            executor.shutdown()
            t.printStackTrace()
            Log.i(TAG, "Reconnection onFailure")
            reconnectCallBack?.call()
            reconnectCallBack = null
        }

        inner class PingPongCheckerRunnable : Runnable {

            private val pingTimeoutThreshold = 3

            override fun run() {
                var disconnected = false
                if (alonePingCount > pingTimeoutThreshold) {
                    disconnected = true
                    Log.i(TAG, "ping-timeout threshold $pingTimeoutThreshold reached, reconnecting")
                }
                if (recentlySendPingCount == 0) {
                    disconnected = true
                    Log.i(TAG, "recently sent ping count = 0, reconnecting")
                }
                if (disconnected) {
                    executor.shutdown()
                    reconnectCallBack?.call()
                    reconnectCallBack = null
                } else {
                    Log.d(TAG, "ping-pong check ok")
                }
                recentlySendPingCount = 0
            }
        }

    }
}
