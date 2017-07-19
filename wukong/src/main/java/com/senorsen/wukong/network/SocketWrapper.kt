package com.senorsen.wukong.network

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.google.common.net.HttpHeaders
import com.google.gson.Gson
import com.senorsen.wukong.BuildConfig
import okhttp3.*
import okio.ByteString
import java.io.EOFException
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class SocketWrapper(
        wsUrl: String,
        cookies: String,
        private val channelId: String,
        reconnectCallback: Callback,
        socketReceiver: SocketReceiver,
        handler: Handler,
        applicationContext: Context
) {

    private val TAG = javaClass.simpleName

    private val userAgent = "WukongAndroid/${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    var ws: WebSocket

    companion object {
        val NORMAL_CLOSURE_STATUS = 1000
    }

    init {
        Log.i(TAG, "Connect ws: $wsUrl")

        val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()

        val request = Request.Builder()
                .header(HttpHeaders.COOKIE, cookies)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .url(wsUrl).build()
        var listener: ActualWebSocketListener? = null

        listener = ActualWebSocketListener(socketReceiver, reconnectCallback, handler, applicationContext)

        ws = client.newWebSocket(request, listener)

        client.dispatcher().executorService().shutdown()
    }

    fun disconnect() {
        ws.close(NORMAL_CLOSURE_STATUS, "Bye")
    }

    fun cancel() {
        ws.cancel()
    }

    interface SocketReceiver {
        fun onEventMessage(protocol: WebSocketReceiveProtocol)
    }

    interface Callback {
        fun call()
    }

    inner class ActualWebSocketListener(private val socketReceiver: SocketReceiver,
                                        private val reconnectCallBack: Callback,
                                        private val handler: Handler,
                                        private val applicationContext: Context) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket open")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Receiving: " + text)
            val receiveProtocol = Gson().fromJson(text, WebSocketReceiveProtocol::class.java)
            socketReceiver.onEventMessage(receiveProtocol)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closing: $code $reason")
            if (code == NORMAL_CLOSURE_STATUS) {
                webSocket.close(NORMAL_CLOSURE_STATUS, null)
            } else {
                Log.i(TAG, "Reconnection")
                reconnectCallBack.call()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (t is EOFException) {
                Log.i(TAG, "Ignore EOFException")
            } else {
                t.printStackTrace()
                Log.i(TAG, "Reconnection onFailure")
                reconnectCallBack.call()
            }
        }

    }
}
