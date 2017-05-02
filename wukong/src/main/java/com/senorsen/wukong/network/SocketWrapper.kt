package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
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

    lateinit var ws: WebSocket

    init {

        val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

        val request = Request.Builder()
                .addHeader("Cookie", cookies)
                .url(wsUrl).build()
        var listener: ActualWebSocketListener? = null

        listener = ActualWebSocketListener(channelId, socketReceiver, reconnectCallback, handler, applicationContext)

        ws = client.newWebSocket(request, listener)

//        client.dispatcher().executorService().shutdown()
    }

    fun disconnect() {
        ws.close(ActualWebSocketListener.Companion.NORMAL_CLOSURE_STATUS, "Bye")
    }

    interface SocketReceiver {
        fun onEventMessage(protocol: WebSocketReceiveProtocol)
    }

    interface Callback {
        fun call()
    }


    class ActualWebSocketListener(private val channelId: String,
                                  private val socketReceiver: SocketReceiver,
                                  private val reconnectCallBack: Callback,
                                  private val handler: Handler,
                                  private val applicationContext: Context) : WebSocketListener() {

        companion object {
            val NORMAL_CLOSURE_STATUS = 1000
        }

        val executor = ScheduledThreadPoolExecutor(1)
        lateinit private var t: ScheduledFuture<*>

        override fun onOpen(webSocket: WebSocket, response: Response) {
            try {
                t = executor.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        // FIXME(Senorsen): hardcoded string
                        webSocket.send(Gson().toJson(WebSocketTransmitProtocol("", "ping"), WebSocketTransmitProtocol::class.java))
                    }
                }, 0, 10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(applicationContext, "Wukong WebSocket onOpen: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Receiving: " + text)
            val receiveProtocol = Gson().fromJson(text, WebSocketReceiveProtocol::class.java)
            socketReceiver.onEventMessage(receiveProtocol)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closing: $code $reason")
            t.cancel(true)
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
