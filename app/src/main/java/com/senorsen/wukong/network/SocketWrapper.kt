package com.senorsen.wukong.network

import okhttp3.*
import okio.ByteString


class SocketWrapper(wsUrl: String, cookies: String) {

    lateinit var ws: WebSocket

    init {

        val client = OkHttpClient()

        val request = Request.Builder()
                .addHeader("Cookie", cookies)
                .url(wsUrl).build()
        val listener = ActualWebSocketListener()

        ws = client.newWebSocket(request, listener)

        client.dispatcher().executorService().shutdown()
    }

    inner class ActualWebSocketListener : WebSocketListener() {

        private val NORMAL_CLOSURE_STATUS = 1000

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("Knock, knock!")
            webSocket.send("Hello!")
            webSocket.close(NORMAL_CLOSURE_STATUS, "Goodbye!")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            println("Receiving: " + text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            println("Closing: $code $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response) {
            t.printStackTrace()
        }

    }
}
