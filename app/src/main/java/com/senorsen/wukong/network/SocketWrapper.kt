package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import okhttp3.*
import okio.ByteString
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class SocketWrapper(wsUrl: String, cookies: String, socketReceiver: SocketReceiver, handler: Handler, applicationContext: Context) {

    lateinit var ws: WebSocket

    init {

        val client = OkHttpClient()

        val request = Request.Builder()
                .addHeader("Cookie", cookies)
                .url(wsUrl).build()
        val listener = ActualWebSocketListener(socketReceiver, handler, applicationContext)

        ws = client.newWebSocket(request, listener)

        client.dispatcher().executorService().shutdown()
    }

    fun disconnect() {
        ws.close(ActualWebSocketListener.Companion.NORMAL_CLOSURE_STATUS, "Bye")
    }

    abstract class SocketReceiver {
        abstract fun onEventMessage(protocol: WebSocketReceiveProtocol)
    }


    class ActualWebSocketListener(private val socketReceiver: SocketReceiver,
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
                        webSocket.send(Gson().toJson(WebSocketTransmitProtocol("test", "ping"), WebSocketTransmitProtocol::class.java))
                    }
                }, 0, 30, TimeUnit.SECONDS)
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
            when {
                receiveProtocol.eventName == "Play" -> {
                    val song = receiveProtocol.song!!
                    handler.post {
                        Toast.makeText(applicationContext, "Wukong: ${song.artist} - ${song.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            t.cancel(true)
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            println("Closing: $code $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            t.printStackTrace()
        }

    }
}
