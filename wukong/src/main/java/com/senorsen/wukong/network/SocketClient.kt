package com.senorsen.wukong.network

import android.content.Context
import android.os.Handler
import android.util.Log
import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.senorsen.wukong.BuildConfig
import com.senorsen.wukong.network.message.WebSocketReceiveProtocol
import okhttp3.*
import okhttp3.internal.ws.RealWebSocket
import java.util.concurrent.TimeUnit


class SocketClient(
        private val wsUrl: String,
        val cookies: String,
        private val channelId: String,
        private val reconnectCallback: Callback,
        val socketReceiver: SocketReceiver,
        val handler: Handler,
        val applicationContext: Context
) {

    private val TAG = javaClass.simpleName

    private val userAgent = "WukongAndroid/${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    var ws: RealWebSocket? = null

    companion object {
        val CLOSE_NORMAL_CLOSURE = 1000
        val CLOSE_GOING_AWAY = 1001
    }

    var disconnected = true

    val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(5, TimeUnit.SECONDS)
            .build()

    val request = Request.Builder()
            .header(HttpHeaders.COOKIE, cookies)
            .header(HttpHeaders.USER_AGENT, userAgent)
            .url(wsUrl).build()
    var listener: ActualWebSocketListener? = null

    @Synchronized
    fun connect() {
        disconnected = false
        Log.i(TAG, "Connect ws $this: $wsUrl")
        listener?.reconnectCallBack = null
        ws?.close(CLOSE_GOING_AWAY, "Going away")
        listener = ActualWebSocketListener(socketReceiver, reconnectCallback)
        ws = client.newWebSocket(request, listener) as RealWebSocket
    }

    fun disconnect() {
        disconnected = true
        ws?.close(CLOSE_NORMAL_CLOSURE, "Bye")
    }

    interface SocketReceiver {
        fun onEventMessage(protocol: WebSocketReceiveProtocol)
    }

    interface Callback {
        fun call()
    }

    inner class ActualWebSocketListener(private val socketReceiver: SocketReceiver,
                                        var reconnectCallBack: Callback?) : WebSocketListener() {

        var alonePingCount = 0
        var recentlySendPingCount = 0
        private val pingCheck = PingPongCheckerRunnable()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket open")
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
            pingCheck.run()
        }

        override fun onPong(webSocket: WebSocket, sendPingCount: Int, pongCount: Int) {
            alonePingCount = 0
            Log.d(TAG, "pong received from server, alone $alonePingCount, sent $sendPingCount received $pongCount.")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "Closing: $code $reason")
            if (code != CLOSE_NORMAL_CLOSURE) {
                Log.i(TAG, "Reconnect")
                reconnectCallBack?.call()
                reconnectCallBack = null
            } else {
                Log.i(TAG, "Server sent normal closure, please wait for ")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "failure, reconnect")
            t.printStackTrace()
            disconnect()
            reconnectCallBack?.call()
            reconnectCallBack = null
        }

        inner class PingPongCheckerRunnable : Runnable {

            private val pingTimeoutThreshold = 2

            override fun run() {
                if (disconnected) return
                var tryReconnect = false
                if (alonePingCount > pingTimeoutThreshold) {
                    tryReconnect = true
                    Log.i(TAG, "ping-timeout threshold $pingTimeoutThreshold reached, reconnecting")
                }
                if (recentlySendPingCount == 0) {
                    tryReconnect = true
                    Log.i(TAG, "recently sent ping count = 0, reconnecting")
                }
                if (tryReconnect) {
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
