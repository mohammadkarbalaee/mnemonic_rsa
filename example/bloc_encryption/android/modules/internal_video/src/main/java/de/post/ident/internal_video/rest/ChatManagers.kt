package de.post.ident.internal_video.rest

import com.squareup.moshi.Types
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.GeneralError
import de.post.ident.internal_core.rest.HttpException
import de.post.ident.internal_core.rest.KRestApi
import de.post.ident.internal_core.util.log
import de.post.ident.internal_video.util.EmmiVideoReporter
import kotlinx.coroutines.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Data stream from Novomind.
 */
abstract class NovomindChatManager(val emmiReporter: EmmiVideoReporter) : CoroutineScope by MainScope() {
    abstract fun start(startChatId: Int, startToken: String)
    abstract fun stop()

    var isRunning = false
        protected set

    var chatId: Int = 0
        protected set
    var token: String = ""
        protected set

    private val subscriptions = mutableListOf<(ChatChangeTypeDTO) -> Unit>()

    fun subscribe(onEvent: (ChatChangeTypeDTO) -> Unit) {
        subscriptions.add(onEvent)
    }

    protected open fun handleEvent(event: ChatChangeTypeDTO) {
        when (event.type) {
            ChatChangeTypeDTO.ChatChangeType.ChatChangeInitChatId -> {
                token = event.token
                        ?: throw IOException("in ChatChangeInitChatId event token null!")
                chatId = event.chatId
                        ?: throw IOException("in ChatChangeInitChatId event chatId null!")
                emmiReporter.send(LogEvent.VC_ID)
            }
            else -> subscriptions.forEach { it(event) }
        }
    }
}

class PollManager(emmiReporter: EmmiVideoReporter) : NovomindChatManager(emmiReporter) {
    private val api = NovomindRestService

    override fun start(startChatId: Int, startToken: String) {
        this.chatId = startChatId
        this.token = startToken
        launch {
            isRunning = true
            var timeoutCounter = 0
            fun resetTimeoutCounter() { timeoutCounter = 0 }
            fun increaseTimeoutCounter() { timeoutCounter++ }

            log("=== STARTING POLLING ===")

            while (isRunning) {
                try {
                    val result = api.poll(chatId, token)
                    result.forEach {
                        log("poll: $it")
                        resetTimeoutCounter()
                        handleEvent(it)
                    }
                } catch (err : Throwable) {
                    log("polling error - message:${err.message} cause:${err.cause}", err)
                    increaseTimeoutCounter()
                    if (timeoutCounter >= 3) stopPolling()
                    when (err) {
                        is HttpException -> {
                            when (err.code) {
                                HttpURLConnection.HTTP_GONE -> {
                                    emmiReporter.send(LogEvent.VC_POLL_ERROR, message = "Chat is gone - HTTP Statuscode 410")
                                    stopPolling()
                                }
                                in HttpURLConnection.HTTP_INTERNAL_ERROR .. HttpURLConnection.HTTP_VERSION -> {
                                    emmiReporter.send(LogEvent.VC_POLL_ERROR, message = "server timeout - no response after ${KRestApi.READ_TIMEOUT} Seconds")
                                }
                                else -> { emmiReporter.send(LogEvent.VC_POLL_ERROR, message = err.message )}                            }
                        }
                        is GeneralError -> {
                            when (err.type) {
                                GeneralError.Type.SERVER_ERROR -> {
                                    emmiReporter.send(LogEvent.VC_POLL_ERROR, message = "server timeout - no response after ${KRestApi.READ_TIMEOUT} Seconds")
                                }
                                GeneralError.Type.NO_CONNECTION -> {
                                    emmiReporter.send(LogEvent.VC_POLL_ERROR, message = "client timeout")
                                }
                                else -> { emmiReporter.send(LogEvent.VC_POLL_ERROR, message = err.message )}
                            }
                        }
                        else -> {
                            emmiReporter.send(LogEvent.VC_POLL_ERROR, message = err.message)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    override fun stop() {
        isRunning = false
    }

    private fun stopPolling() {
        val event = ChatChangeTypeDTO(ChatChangeTypeDTO.ChatChangeType.ChatChangeStop, null, null,null,null,null)
        handleEvent(event)
    }
}

class WebSocketManager(emmiReporter: EmmiVideoReporter) : NovomindChatManager(emmiReporter) {

    private val api = NovomindRestService
    private var webSocket: WebSocket? = null

    //see: https://www.iana.org/assignments/websocket/websocket.xml
    private val STOPPED_BY_CLIENT = 4000
    private val RESTART_WEBSOCKET = 4001
    private val STATUS_TERMINATED = 1003 // internal webSocket error code

    override fun start(startChatId: Int, startToken: String) {
        chatId = startChatId
        token = startToken

        log("=== STARTING WEBSOCKET ===")
        try {
            webSocket = api.createWebSocket(webSocketListener, chatId, token)
            emmiReporter.send(LogEvent.VC_WEBSOCKET_CONNECT)
        } catch (e: Throwable) {
            e.printStackTrace()
            emmiReporter.send(LogEvent.VC_WEBSOCKET_ERROR, message = e.message)
            handleEvent(
                ChatChangeTypeDTO(
                    ChatChangeTypeDTO.ChatChangeType.ChatChangeInitNack,
                    null,
                    ChatChangeMessageDTO.Default("creating websocket failed"),
                    null,null,null)
            )
        }
    }

    override fun stop() {
        stop(STOPPED_BY_CLIENT)
    }

    private fun stop(errorCode: Int) {
        isRunning = false
        webSocket?.close(errorCode, "stopped by client")
    }

    private fun restart() {
        stop(RESTART_WEBSOCKET)
        start(chatId, token)
    }

    override fun handleEvent(event: ChatChangeTypeDTO) {
        super.handleEvent(event)
        if (event.type == ChatChangeTypeDTO.ChatChangeType.ChatChangeInitChatId) restart()
    }

    private val webSocketListener: WebSocketListener = object : WebSocketListener() {

        private val type = Types.newParameterizedType(List::class.java, ChatChangeTypeDTO::class.java)
        private val adapter = api.moshi.adapter<List<ChatChangeTypeDTO>>(type)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            log("opened webSocket, response: $response")
            isRunning = true
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        override fun onMessage(webSocket: WebSocket, text: String) {
            launch {
                try {
                    val result: List<ChatChangeTypeDTO>? = adapter.fromJson(text)
                    result?.forEach {
                        log("ws: $it")
                        handleEvent(it)
                    }
                } catch (err: Throwable) {
                    log("webSocket message error", err)
                    emmiReporter.send(LogEvent.VC_WEBSOCKET_ERROR, message = err.message)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            log("webSocket message: $bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log("closing webSocket, code: $code, reason: $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log("closed webSocket, code: $code, reason: $reason")
            isRunning = false

            // if code is RESTART_WEBSOCKET we need to restart the connection and not handle the event
            if (code != RESTART_WEBSOCKET) {
                val typeDTO = if (code == STOPPED_BY_CLIENT || code == STATUS_TERMINATED)
                    ChatChangeTypeDTO.ChatChangeType.WebSocketStoppedNoFallback
                else ChatChangeTypeDTO.ChatChangeType.WebSocketStopped

                handleEvent(ChatChangeTypeDTO(typeDTO, null, null, null, null, null))
            }
        }

        override fun onFailure(webSocket: WebSocket, err: Throwable, response: Response?) {
            log("webSocket failure: $err | $response")
            emmiReporter.send(LogEvent.VC_WEBSOCKET_ERROR, message = err.message)
            handleEvent(ChatChangeTypeDTO(ChatChangeTypeDTO.ChatChangeType.WebSocketStopped, null, null, null, null, null))
        }
    }
}