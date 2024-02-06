package de.post.ident.internal_video.rest

import com.squareup.moshi.Moshi
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.R
import de.post.ident.internal_core.rest.EnumTypeAdapterFactory
import de.post.ident.internal_core.rest.GeneralError
import de.post.ident.internal_core.rest.KRestApi
import de.post.ident.internal_core.util.LocalizedStrings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

object NovomindRestService {

    private var restApiInternal: KRestApi? = null
    val restApi: KRestApi
        get() = checkNotNull(restApiInternal) { "NovomindRestService not initialized!" }

    val moshi = Moshi.Builder()
            .add(EnumTypeAdapterFactory()) // Added to support enum default values
            .add(NovomindAdapterFactory())
            .build()

    private const val tokenHeader = "X-novomind-iAgent-chat-token"
    private const val chats = "iagent_chat_t01/chats"

    init {
        val config = CoreConfig.serverConfig

        restApiInternal = KRestApi("https://${config.agentDomain}/", moshi) {
            errorHandler = { err ->
                when (err) {
                    is UnknownHostException ->
                        GeneralError(LocalizedStrings.getString(R.string.err_dialog_no_connection_text), GeneralError.Type.NO_CONNECTION)
                    is SocketTimeoutException, is TimeoutException ->
                        GeneralError(LocalizedStrings.getString(R.string.err_dialog_technical_error), GeneralError.Type.SERVER_ERROR)
                    is SSLHandshakeException, is SSLPeerUnverifiedException, is SSLException ->
                        GeneralError(LocalizedStrings.getString(R.string.err_dialog_ssl_error_text), GeneralError.Type.SSL_ERROR)
                    else -> err
                }
            }

            addHeader("User-Agent", config.userAgent)

            addPinnedDomains(config.pinnedDomains)
        }
    }

    fun createWebSocket(listener: WebSocketListener, chatId: Int, token: String): WebSocket {
        val targetUrl = "wss://${CoreConfig.serverConfig.agentDomain}/$chats/$chatId/changes?token=$token"
        val request: Request = Request.Builder().url(targetUrl).build()
        return restApi.newWebSocket(request, listener)
    }

    /** URL: iagent_chat_t01/chats */
    suspend fun requestChatId(chatRequestData: ChatRequestDataDTO): ChatResponseDTO = restApi
            .post(chatRequestData)
            .path(chats)
            .execute(ChatResponseDTO::class)

    /** URL: iagent_chat_t01/chats/{chatId}/changes */
    suspend fun poll(chatId: Int, token: String): List<ChatChangeTypeDTO> = restApi
            .get()
            .addHeader(tokenHeader, token)
            .path("$chats/$chatId/changes")
            .queryParam("token", token)
            .executeNA(ChangesResponseDTO::class)?.changesType ?: emptyList()

    suspend fun webRtcSendConnect(chatId: Int, token: String, webRtcData: ConnectDataDTO) = webRtcSend(chatId, token, webRtcData)
    suspend fun webRtcSendCandidate(chatId: Int, token: String, webRtcData: CandidateDataDTO) = webRtcSend(chatId, token, webRtcData)
    suspend fun webRtcSendAnswer(chatId: Int, token: String, webRtcData: AnswerDataDTO) = webRtcSend(chatId, token, webRtcData)

    /** URL: iagent_chat_t01/chats/{chatId}/webrtc/send */
    private suspend inline fun <reified T : WebRtcDataDTO> webRtcSend(chatId: Int, token: String, webRtcData: T) = restApi
            .post(webRtcData)
            .addHeader(tokenHeader, token)
            .path("$chats/$chatId/webrtc/send")
            .execute()

    suspend fun sendAppPause(chatId: Int, token: String, appPause: Boolean) = sendMetaInformation(chatId, token, MessageDataDTO("{\"appPause\":$appPause}"))

    suspend fun sendTan(chatId: Int, token: String, tan: String) = sendMetaInformation(chatId, token, MessageDataDTO("{\"tan\":$tan}"))

    suspend fun sendCanTakeScreenshots(chatId: Int, token: String) = sendMetaInformation(chatId, token, MessageDataDTO("{\"pi_canTakeScreenshots\":true}"))

    /** URL: iagent_chat_t01/chats/{chatId}/metainformation */
    private suspend fun sendMetaInformation(chatId: Int, token: String, messageData: MessageDataDTO) = restApi
            .post(messageData)
            .addHeader(tokenHeader, token)
            .path("$chats/$chatId/metainformation")
            .execute()

    /** URL: iagent_chat_t01/chats/{chatId}/messages */
    suspend fun sendChatMessage(chatId: Int, token: String, chatMessage: String) = restApi
            .post(MessageDataDTO(chatMessage))
            .addHeader(tokenHeader, token)
            .path("$chats/$chatId/messages")
            .execute()

    /** URL: iagent_chat_t01/chats/{chatId}/stop */
    suspend fun stop(chatId: Int, token: String) = restApi
            .post(EMPTY_REQUEST)
            .addHeader(tokenHeader, token)
            .path("$chats/$chatId/stop")
            .execute()

    /** URL: iagent_chat_t01/chats/{chatId}/attachments */
    suspend fun uploadScreenshot(jpgData: ByteArray, chatId: Int, token: String) {
        val fileName = "${Commons.caseId}.jpg"
        val mediaType: MediaType = "image/jpeg".toMediaType()

        val requestBody: RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("visibility", "I")
                .addFormDataPart("contentType", mediaType.toString())
                .addFormDataPart("filename", fileName)
                .addFormDataPart("size", jpgData.size.toString())
                .addFormDataPart("file", fileName, jpgData.toRequestBody(mediaType))
                .build()

        restApi.post(requestBody).addHeader(tokenHeader, token).path("$chats/$chatId/attachments").execute()
    }
}