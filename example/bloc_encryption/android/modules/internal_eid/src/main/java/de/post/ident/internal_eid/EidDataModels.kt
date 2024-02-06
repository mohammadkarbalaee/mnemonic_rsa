package de.post.ident.internal_eid

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.rest.JsonEnum
import de.post.ident.internal_core.rest.JsonEnumClass

interface EidDataDto

//region messages

//see: https://www.ausweisapp.bund.de/sdk/messages.html

sealed class EidMessage

@JsonClass(generateAdapter = true)
data class EidMessageDto(
        @Json(name = "msg") val msg: MessageType,
        @Json(name = "error") val error: String?, // used for message: AUTH, BAD_STATE, ENTER_CAN, ENTER_PIN, ENTER_NEW_PIN, INTERNAL_ERROR, INVALID, UNKNOWN_COMMAND
        @Json(name = "result") val result: EidResultDto?, // used for message: AUTH
        @Json(name = "url") val url: String?, // used for message: AUTH
        @Json(name = "description") val certificate: EidCertificateDto?, // used for message: CERTIFICATE
        @Json(name = "validity") val validity: EidValidityDto?, // used for message: CERTIFICATE
        @Json(name = "success") val success: Boolean?, // used for message: CHANGE_PIN
        @Json(name = "card") val card: EidCardDto?, // used for message: READER
        @Json(name = "reader") val reader: EidReaderDto?, // used for message: READER
        @Json(name = "chat") val accessRights: EidAccessRightsDto?, // used for message: ACCESS_RIGHTS
        @Json(name = "VersionInfo") val versionInfo: EidVersionInfoDto?, // used for message: INFO
        @Json(name = "status") val status: EidStatusDto?, // used for message: STATUS
        @Json(name = "name") val name: String?, // used for message: STATUS
) : EidDataDto {
    @JsonEnumClass
    @Keep
    enum class MessageType {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "AUTH") AUTH,
        @JsonEnum(name = "ACCESS_RIGHTS") ACCESS_RIGHTS,
        @JsonEnum(name = "INSERT_CARD") INSERT_CARD,
        @JsonEnum(name = "ENTER_PIN") ENTER_PIN,
        @JsonEnum(name = "ENTER_NEW_PIN") ENTER_NEW_PIN,
        @JsonEnum(name = "ENTER_CAN") ENTER_CAN,
        @JsonEnum(name = "ENTER_PUK") ENTER_PUK, //not supported, handled by retryCounter = 0
        @JsonEnum(name = "CHANGE_PIN") CHANGE_PIN,
        @JsonEnum(name = "CERTIFICATE") CERTIFICATE,
        @JsonEnum(name = "READER") READER,
        @JsonEnum(name = "INFO") INFO,
        @JsonEnum(name = "BAD_STATE") BAD_STATE,
        @JsonEnum(name = "INTERNAL_ERROR") INTERNAL_ERROR,
        @JsonEnum(name = "INVALID") INVALID,
        @JsonEnum(name = "UNKNOWN_COMMAND") UNKNOWN_COMMAND,
    }
}

@JsonClass(generateAdapter = true)
data class EidResultDto( // used for message: AUTH
        @Json(name = "major") val majorRaw: String,
        @Json(name = "minor") val minorRaw: String?,
        @Json(name = "language") val language: String?,
        @Json(name = "description") val description: String?,
        @Json(name = "message") val errorMessage: String?,
) {

    val major = when(majorRaw) {
        "http://www.bsi.bund.de/ecard/api/1.1/resultmajor#ok" -> EidResultMajor.RESULT_OK
        "http://www.bsi.bund.de/ecard/api/1.1/resultmajor#error" -> EidResultMajor.RESULT_ERROR
        "http://www.bsi.bund.de/ecard/api/1.1/resultmajor#warning" -> EidResultMajor.RESULT_WARNING
        "http://www.bsi.bund.de/ecard/api/1.1/resultmajor#nextRequest" -> EidResultMajor.RESULT_NEXT_REQUEST
        else -> EidResultMajor.UNKNOWN
    }

    @Keep
    enum class EidResultMajor {
        RESULT_ERROR,
        RESULT_OK,
        RESULT_WARNING,
        RESULT_NEXT_REQUEST,
        UNKNOWN
    }

    val minor = when(minorRaw) {
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/al/common#communicationError" -> EidResultMinor.COMMUNICATION_ERROR // Kommunikationsfehler (API_COMMUNICATIOPN_FAILURE)
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/al/common#internalError" -> EidResultMinor.INTERNAL_ERROR
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/al/common#noPermission" -> EidResultMinor.NO_PERMISSION // wrong ID document?
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/al/common#unknownAPIFunction" -> EidResultMinor.WRONG_API_ERROR // wrong ID document?
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/al/common#unknownError" -> EidResultMinor.UNKNOWN_ERROR // wrong ID document?
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/dp#trustedChannelEstablishmentFailed" -> EidResultMinor.TRUSTED_CHANNEL_ESTABLISHMENT_FAILED
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/ifdl#cancellationByUser" -> EidResultMinor.CARD_REMOVED
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/sal#cancellationByUser" -> EidResultMinor.PROCESS_CANCELLED
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/sal/mEAC#DocumentValidityVerificationFailed" -> EidResultMinor.VALIDITY_VERIFICATION_FAILED // card expired
        "http://www.bsi.bund.de/ecard/api/1.1/resultminor/al/common#parameterError" -> EidResultMinor.PARAMETER_ERROR // wrong test environment (Dev)?
        else -> EidResultMinor.RESULT_ERROR
    }

    @Keep
    enum class EidResultMinor {
        RESULT_ERROR,
        CARD_REMOVED,
        PROCESS_CANCELLED,
        INTERNAL_ERROR,
        TRUSTED_CHANNEL_ESTABLISHMENT_FAILED,
        PARAMETER_ERROR,
        WRONG_API_ERROR,
        VALIDITY_VERIFICATION_FAILED,
        COMMUNICATION_ERROR,
        NO_PERMISSION,
        UNKNOWN_ERROR
    }
}

@JsonClass(generateAdapter = true)
data class EidCertificateDto( // used for message: CERTIFICATE
        @Json(name = "issuerName") val issuerName: String,
        @Json(name = "issuerUrl") val issuerUrl: String,
        @Json(name = "subjectName") val subjectName: String,
        @Json(name = "subjectUrl") val subjectUrl: String,
        @Json(name = "termsOfUsage") val termsOfUsage: String,
        @Json(name = "purpose") val purpose: String,
        @Json(name = "validity") var validity: EidValidityDto?
) : EidMessage()

@JsonClass(generateAdapter = true)
data class EidValidityDto( // used for message: CERTIFICATE
        @Json(name = "effectiveDate") val effectiveDate: String,
        @Json(name = "expirationDate") val expirationDate: String,
) : EidMessage()

@JsonClass(generateAdapter = true)
data class EidCardDto( // used for message: READER, ENTER_PIN
        @Json(name = "inoperative") val inoperative: Boolean,
        @Json(name = "deactivated") val deactivated: Boolean,
        @Json(name = "retryCounter") val retryCounter: Int,
) : EidMessage()

@JsonClass(generateAdapter = true)
data class EidAccessRightsDto( // used for message: ACCESS_RIGHTS
        @Json(name = "effective") val effective: Array<String>?, //currently not needed
        @Json(name = "optional") val optional: Array<String>?, //currently not needed
        @Json(name = "required") val required: Array<String>?,
) : EidMessage()

@JsonClass(generateAdapter = true)
data class EidReaderDto(  // used for message: READER
        @Json(name = "attached") val attached: Boolean,
        @Json(name = "card") val card: EidCardDto,
) : EidMessage()

@JsonClass(generateAdapter = true)
data class EidVersionInfoDto(  // used for message: INFO
        @Json(name = "Implementation-Title") val ausweisAppTitle: String,
        @Json(name = "Implementation-Version") val ausweisAppVersion: String,
) : EidMessage()

@JsonClass(generateAdapter = true)
data class EidStatusDto(  // used for message: READER
    @Json(name = "workflow") val workflow: String?,
    @Json(name = "progress") val progress: Int?,
    @Json(name = "state") val state: String?
) : EidMessage()

data class InsertCardEvent(val error: String? = null) : EidMessage()
data class EnterPinEvent(val requireCan: Boolean = false, val error: Boolean? = false, val card: EidCardDto) : EidMessage()
object EnterNewPinEvent : EidMessage()
data class ReadEvent(val card: EidCardDto?, val simulated: Boolean = false) : EidMessage()
data class ChangePinResultEvent(val success: Boolean) : EidMessage()
data class BadStateEvent(val error: String?) : EidMessage()
data class EidAuthEvent(val url: String?, val result: EidResultDto) : EidMessage()
object EidResultOk : EidMessage()
data class EidResultError(val result: EidResultDto) : EidMessage()
data class EidErrorMessage(val error: EidException) : EidMessage()

//endregion

//region commands

//see: https://www.ausweisapp.bund.de/sdk/commands.html

@JsonClass(generateAdapter = true)
data class EidCommandDto(
        @Json(name = "cmd") val cmd: CommandType,
        @Json(name = "tcTokenURL") val tcTokenUrl: String? = null, // used for command: RUN_AUTH
        @Json(name = "developerMode") val developerMode: Boolean? = false, // used for command: RUN_AUTH
//        @Json(name = "chat") val chat: Array<String>? = null, // used for command: SET_ACCESS_RIGHTS
        @Json(name = "value") val value: String? = null, // used for command: SET_PIN, SET_NEW_PIN, SET_CAN
        @Json(name = "name") val name: String? = null, // used for command: SET_CARD (Simulator)
) : EidDataDto {

    @JsonEnumClass
    @Keep
    enum class CommandType {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "RUN_AUTH") RUN_AUTH,
        @JsonEnum(name = "RUN_CHANGE_PIN") RUN_CHANGE_PIN,
        @JsonEnum(name = "ACCEPT") ACCEPT,
        @JsonEnum(name = "SET_PIN") SET_PIN,
        @JsonEnum(name = "SET_NEW_PIN") SET_NEW_PIN,
        @JsonEnum(name = "SET_CAN") SET_CAN,
        @JsonEnum(name = "CANCEL") CANCEL,
//        @JsonEnum(name = "SET_ACCESS_RIGHTS") SET_ACCESS_RIGHTS, // not used for now
//        @JsonEnum(name = "GET_ACCESS_RIGHTS") GET_ACCESS_RIGHTS, // not used for now
        @JsonEnum(name = "GET_INFO") GET_INFO,
        @JsonEnum(name = "GET_CERTIFICATE") GET_CERTIFICATE,
        @JsonEnum(name = "GET_STATUS") GET_STATUS, // not used for now
        @JsonEnum(name = "GET_API_LEVEL") GET_API_LEVEL, // not used for now
        @JsonEnum(name = "SET_API_LEVEL") SET_API_LEVEL, // not used for now
        @JsonEnum(name = "SET_CARD") SET_CARD, // not used for now
    }
}

//endregion

