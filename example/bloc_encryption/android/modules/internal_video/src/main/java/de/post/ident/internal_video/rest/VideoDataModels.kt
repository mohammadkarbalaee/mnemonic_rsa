package de.post.ident.internal_video.rest

import androidx.annotation.Keep
import com.squareup.moshi.*
import de.post.ident.internal_core.rest.JsonEnum
import de.post.ident.internal_core.rest.JsonEnumClass
import java.lang.reflect.Type
import kotlin.reflect.KClass

@JsonClass(generateAdapter = true)
data class ChatResponseDTO(
        @Json(name = "chatId") val chatId: Int,
        @Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class ChatRequestDataDTO(
        @Json(name = "nickname") val nickname: String,
        @Json(name = "category") val category: String,
        @Json(name = "language") val language: String,
        @Json(name = "channel") val channel: String,
        @Json(name = "info") val info: ChatRequestInfoDTO
)

@JsonClass(generateAdapter = true)
data class ChatRequestInfoDTO(
        @Json(name = "pi_wf_bcId") val bcId: String,
        @Json(name = "pi_wf_bcName") val bcName: String,
        @Json(name = "pi_wf_chatLaunchId") val chatLaunchId: String?,
)

@JsonClass(generateAdapter = true)
data class ChangesResponseDTO(
        @Json(name = "changes") val changesType: List<ChatChangeTypeDTO> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChatChangeTypeDTO(
        @Json(name = "type") val type: ChatChangeType,
        @Json(name = "data") val data: String?,
        @Json(name = "message") val message: ChatChangeMessageDTO?,
        @Json(name = "chatId") val chatId: Int?,
        @Json(name = "token") val token: String?,
        @Json(name = "chatstepType") val chatstepType: Int?
) {
    @JsonEnumClass
    @Keep
    enum class ChatChangeType {
        @JsonEnum(name = "", default = true) Unknown,
        @JsonEnum(name = "ChatChangeInitChatId") ChatChangeInitChatId,
        @JsonEnum(name = "ChatChangeInitNack") ChatChangeInitNack,
        @JsonEnum(name = "ChatChangeChatstep") ChatChangeChatstep,
        @JsonEnum(name = "ChatChangeMetainformation") ChatChangeMetainformation,
        @JsonEnum(name = "WebRtcChange") WebRtcChange,
        @JsonEnum(name = "ChatChangeStopPolling") ChatChangeStopPolling,
        @JsonEnum(name = "ChatChangeStop") ChatChangeStop,
        @JsonEnum(name = "WebSocketStoppedNoFallback") WebSocketStoppedNoFallback, // internal use only
        @JsonEnum(name = "WebSocketStopped") WebSocketStopped // internal use only
    }

    object ChatStepType {
        const val CHAT_MESSAGE = 1
        const val NO_AGENT_AVAILABLE = 11
        const val CLOSED_BROWSER = 12
        const val TIMEOUT_IN_QUEUE = 13
        const val GENERAL_ERROR = 14
        const val AGENT_BLOCKED_USER = 15
        const val NO_CHAT_PERMISSION = 17
        const val CALLCENTER_CLOSED = 18

        // unused:
        // 0;  AGENT_READY
        // 3;  AGENT_LEFT
        // 10; CHAT_CLOSED_NORMALLY
        // 20; Besuchte Seite
        // 21; TYPE_COBROWSE_ANSWER
        // 22; TYPE_COBROWSE_DEACTIVATE
        // 30; TYPE_PUSHLINK
        // 31; TYPE_COBROWSE_REQUEST
        // 32; TYPE_RECATEGORIZATION
        // 40; TYPE_ESCALATION
        // 41; // hat keine CHATSID
        // 42; TYPE_REROUTE
        // 43; TYPE_FORWARD
        // 44; TYPE_AUTHENTICATION
        // 45; TYPE_FILE_REF = Datei Referenzierung
    }
}

interface WebRtcDataDTO

@JsonClass(generateAdapter = true)
data class ConnectDataDTO(
        @Json(name = "type") val type: String = "connect",
        @Json(name = "resolution") val resolution: String
): WebRtcDataDTO

@JsonClass(generateAdapter = true)
data class AnswerDataDTO(
        @Json(name = "type") val type: String = "answer",
        @Json(name = "sdp") val sdp: String
): WebRtcDataDTO

@JsonClass(generateAdapter = true)
data class CandidateDataDTO(
        @Json(name = "type") val type: String = "candidate",
        @JsonEscaped
        @Json(name = "candidate") val candidate: CandidateDataCandidateDTO
): WebRtcDataDTO {
    @JsonClass(generateAdapter = true)
    data class CandidateDataCandidateDTO(
            @Json(name = "candidate") val candidate: String,
            @Json(name = "sdpMLineIndex") val sdpMLineIndex: Int
    )
}

@JsonClass(generateAdapter = true)
data class MessageDataDTO(
        @Json(name = "message") val message: String
)

sealed class ChatChangeMessageDTO {

    data class Default(val message: String) : ChatChangeMessageDTO()

    @JsonClass(generateAdapter = true)
    data class WorkflowUpdateAgentNameDTO(@Json(name = "workflowUpdateAgentName") val workflowUpdateAgentName: String) : ChatChangeMessageDTO()

    @JsonClass(generateAdapter = true)
    data class WorkflowStateDTO(@Json(name = "workflowActiveState") val workflowActiveState: WorkflowState) : ChatChangeMessageDTO()

    @JsonClass(generateAdapter = true)
    data class WorkflowUserDataDTO(
            // the new field names (see comments) can not be used for now, as they are not sent correctly by the agent system
            @Json(name = "workflowUpdateUserData") val workflowUpdateUserData: Boolean,
            @Json(name = "activeTanChannel") val activeTanChannel: ActiveTanChannelDTO?,
            @Json(name = "mobile") val mobileNr: String?,
            @Json(name = "email") val email: String?,
            @Json(name = "cardId") var idcNumber: String?, // fieldIdcNumber
            @Json(name = "name") val idcLastName: String?, // fieldIdcLastName
            @Json(name = "birthName") val idcBirthName: String?, // fieldIdcBirthName
            @Json(name = "vorname") val idcFirstName: String?, // fieldIdcFirstName
            @Json(name = "birthData") val idcBirthDate: String?, // fieldIdcBirthDate
            @Json(name = "birthCity") val idcBirthPlace: String?, // fieldIdcBirthPlace
            @Json(name = "nationality") val idcNationality: String?, // fieldIdcNationality
            @Json(name = "street") val addressStreet: String?, // fieldAddressStreet
            @Json(name = "streetno") val addressStreetNumber: String?, // fieldAddressStreetNumber
            @Json(name = "additionalAdress") val addressAppendix: String?, // fieldAddressAppendix
            @Json(name = "code") val addressPostalCode: String?, // fieldAddressPostalCode
            @Json(name = "city") val addressCity: String?, // fieldAddressCity
            @Json(name = "country") val addressCountry: String?, // fieldAddressCountry
            @Json(name = "givenAt") val idcDateIssued: String?, // fieldIdcDateIssued
            @Json(name = "validUntil") val idcDateOfExpiry: String?, // fieldIdcDateOfExpiry
            @Json(name = "givenBy") val idcAuthority: String?, // fieldIdcAuthority
            @Json(name = "cityOfIssue") val idcPlaceOfIssue: String?, // fieldIdcPlaceOfIssue
    ) : ChatChangeMessageDTO() {
        @JsonEnumClass()
        @Keep
        enum class ActiveTanChannelDTO {
            @JsonEnum(name = "", default = true) UNKNOWN,
            @JsonEnum(name = "EMAIL") EMAIL,
            @JsonEnum(name = "SMS") SMS
        }
    }

    @JsonEnumClass()
    @Keep
    enum class WorkflowState {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "INCOMING_CALL") INCOMING_CALL,
        @JsonEnum(name = "DECLARATION_OF_CONSENT") DECLARATION_OF_CONSENT,
        @JsonEnum(name = "CAPTURE_SCREENSHOT_IDCARD_FRONT") CAPTURE_SCREENSHOT_IDCARD_FRONT,
        @JsonEnum(name = "CAPTURE_SCREENSHOT_IDCARD_BACK") CAPTURE_SCREENSHOT_IDCARD_BACK,
        @JsonEnum(name = "CAPTURE_SCREENSHOT_OF_FACE") CAPTURE_SCREENSHOT_OF_FACE,
        @JsonEnum(name = "GRAB_IDCARD_NUMBER") GRAB_IDCARD_NUMBER,
        @JsonEnum(name = "FILL_IDCARD_DATA") FILL_IDCARD_DATA,
        @JsonEnum(name = "SEND_TAN") SEND_TAN,
        @JsonEnum(name = "END_CHAT") END_CHAT,
        @JsonEnum(name = "CANCELED_VERIFY_PROCESS") CANCELED_VERIFY_PROCESS
    }

    @JsonClass(generateAdapter = true)
    data class WaitingLineDTO(@Json(name = "waitingLine") val waitingLine: WaitingLineInfoDTO) : ChatChangeMessageDTO()

    @JsonClass(generateAdapter = true)
    data class WaitingLineInfoDTO(@Json(name = "time") val timeSeconds: Int)

    @JsonClass(generateAdapter = true)
    data class WorkflowDocumentExtracted(@Json(name = "documentExtracted") val crop: Boolean) : ChatChangeMessageDTO()

    @JsonClass(generateAdapter = true)
    data class WorkflowSigningRedirectToken(@Json(name = "pi_signingRedirectToken") val signingRedirectToken: String) : ChatChangeMessageDTO()

    @JsonClass(generateAdapter = true)
    data class WorkflowCancelledProcess(
        @Json(name = "workflowCancelledProcess") val workflowCancelledProcess: Boolean,
        @Json(name = "workflowCancelledReason") val workflowCancelledReason: String?
    ) : ChatChangeMessageDTO()

    object WorkflowTanTransmitted: ChatChangeMessageDTO()

    data class WorkflowTanResult(val correctTan: Boolean) : ChatChangeMessageDTO()

    data class WorkflowTakeScreenshot(val simulated: Boolean) : ChatChangeMessageDTO()
}

class NovomindAdapterFactory: JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type == ChatChangeMessageDTO::class.java) {
            return ChatChangeMessageAdapter(moshi)
        }
        if (type is Class<*> && annotations.find { it is JsonEscaped } != null) {
            return EscapedJsonAdapter(type, moshi)
        }
        return null
    }
}

internal class ChatChangeMessageAdapter(val moshi: Moshi): JsonAdapter<ChatChangeMessageDTO>() {
    override fun toJson(writer: JsonWriter, value: ChatChangeMessageDTO?) = TODO("Not needed yet!")

    override fun fromJson(reader: JsonReader): ChatChangeMessageDTO? {
        val message = (reader.readJsonValue() as? String) ?: return null
        fun <T : Any> parse(clazz: KClass<T>) = moshi.adapter(clazz.java).fromJson(message)

        return when {
            message.contains("workflowUpdateAgentName") -> parse(ChatChangeMessageDTO.WorkflowUpdateAgentNameDTO::class)
            message.contains("workflowActiveState") -> parse(ChatChangeMessageDTO.WorkflowStateDTO::class)
            message.contains("workflowCancelledProcess") -> parse(ChatChangeMessageDTO.WorkflowCancelledProcess::class)
            message.contains("workflowUpdateUserData") -> parse(ChatChangeMessageDTO.WorkflowUserDataDTO::class)
            message.contains("waitingLine") -> parse(ChatChangeMessageDTO.WaitingLineDTO::class)
            message.contains("documentExtracted") -> parse(ChatChangeMessageDTO.WorkflowDocumentExtracted::class)
            message.contains("tanTransmitted") -> ChatChangeMessageDTO.WorkflowTanTransmitted
            message.contains("workflowGotWrongTAN") -> ChatChangeMessageDTO.WorkflowTanResult(false)
            message.contains("workflowGotCorrectTAN") -> ChatChangeMessageDTO.WorkflowTanResult(true)
            message.contains("pi_takeScreenshot") -> ChatChangeMessageDTO.WorkflowTakeScreenshot(false)
            message.contains("pi_simulateScreenshot") -> ChatChangeMessageDTO.WorkflowTakeScreenshot(true)
            message.contains("pi_signingRedirectToken") -> parse(ChatChangeMessageDTO.WorkflowSigningRedirectToken::class)
            else -> ChatChangeMessageDTO.Default(message)
        }
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class JsonEscaped

internal class EscapedJsonAdapter<T : Any>(val clazz: Class<T>, val moshi: Moshi = Moshi.Builder().build()): JsonAdapter<T>() {
    override fun toJson(writer: JsonWriter, value: T?) {
        val adapter = moshi.adapter(clazz)
        if (value != null) {
            writer.value(adapter.toJson(value))
        }
    }

    override fun fromJson(reader: JsonReader): T? {
        TODO("Not yet implemented")
    }
}
