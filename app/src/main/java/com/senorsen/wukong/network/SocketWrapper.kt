package com.senorsen.wukong.network

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import okhttp3.*
import okio.ByteString


class SocketWrapper(wsUrl: String, cookies: String, handler: Handler, applicationContext: Context) {

    lateinit var ws: WebSocket

    init {

        val client = OkHttpClient()

        val request = Request.Builder()
                .addHeader("Cookie", cookies)
                .url(wsUrl).build()
        val listener = ActualWebSocketListener(handler, applicationContext)

        ws = client.newWebSocket(request, listener)

        client.dispatcher().executorService().shutdown()
    }

    inner class ActualWebSocketListener(private val handler: Handler, private val applicationContext: Context) : WebSocketListener() {

        private val NORMAL_CLOSURE_STATUS = 1000

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("Knock, knock!")
            webSocket.send("Hello!")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Receiving: " + text)
            val receiveProtocol = Gson().fromJson(text, WebSocketReceiveProtocol::class.java)
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
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            println("Closing: $code $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            t.printStackTrace()
        }

    }
}
