package de.post.ident.internal_eid

import android.text.Spanned
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import de.post.ident.internal_core.util.LocalizedStrings

@Keep
enum class EidError(private val id: Int, private val label: String, private val source: String, private val logAsError: Boolean = true,
                    private val stringKey: String = "eid_error_unknown", private val showHint: Boolean = false, private val additionalInfo: String? = null,
                    private val loggingErrorCode: String, val loggingMessage: String, private val imageRes: Int? = R.drawable.pi_eid_error_default,
                    private val isRecoverable: Boolean = true) {

    // keep in sync with https://confluence.clear-mail.de/wiki/display/ATARI/eID+Fehlercodes+Apps (before adding new codes)!

    EXTENDED_LENGTH_ISSUE(id = 50, label = "nfc_extended_length_issue", source = Source.DEVICE.id, stringKey = "eid_error_extended_length_issue", imageRes = R.drawable.pi_eid_error_no_nfc, isRecoverable = false, loggingErrorCode = "eid.nfc.extended.length.device.not.supported", loggingMessage = "android device does not support extended length feature"),
    TRUSTED_CHANNEL_FAILURE(id = 51, label = "trusted_channel_failure", source = Source.EID_CLIENT.id, stringKey = "eid_error_trusted_channel_failure", loggingErrorCode = "eid.governikus.trusted.channel.failure", loggingMessage = "trusted channel could not be established"),
    RESULT_MINOR_ERROR(id = 52, label = "result_minor_error", source = Source.EID_CLIENT.id, loggingErrorCode = "eid.governikus.result.unknown.minor.error", loggingMessage = "an unknown result minor error from the eid kernel"),
    INTERNAL_ERROR(id = 53, label = "internal_error", source = Source.EID_CLIENT.id, loggingErrorCode = "eid.internal.error", loggingMessage = "An internal error occurred, see log file of the application server."),
    BAD_STATE(id = 54, label = "bad_state", source = Source.EID_CLIENT.id, loggingErrorCode = "eid.bad.state", loggingMessage = "Some commands can be send to the server only if certain “state” is reached in the workflow to obtain the corresponding result. Otherwise the command will fail with BAD_STATE."),
    PIN_CHANGE_ERROR(id = 56, label = "pin_change_error", source = Source.EID_CLIENT.id, stringKey = "eid_change_pin_error", loggingErrorCode = "eid.pin.change.error", loggingMessage = "pin change error occurred"),
    AUTHENTICATION_ERROR(id = 57, label = "authentication_error", source = Source.EID_CLIENT.id, loggingErrorCode = "eid.authentication.error", loggingMessage = "Scanning process failed"),
    PARAMETER_ERROR(id = 58, label = "parameter_error", source = Source.EID_CLIENT.id, stringKey = "eid_error_authentication_failure", loggingErrorCode = "eid.parameter.error", loggingMessage = "One or more parameters are wrong"),
    CARD_READ_CONNECTION_LOST(id = 59, label = "card_read_connection_lost", source = Source.EID_CLIENT.id, stringKey = "eid_error_client_error", showHint = true, loggingErrorCode = "eid.card.read.connection.lost", loggingMessage = "Card reading process was started but connection was lost."),
    CARD_DEACTIVATED(id = 60, label = "card_deactivated", source = Source.EID_CLIENT.id, stringKey = "eid_error_pin_status_deactivated", showHint = true, imageRes = R.drawable.pi_id_card_error, isRecoverable = false, loggingErrorCode = "eid.card.deactivated", loggingMessage = "Deactivated id card was used for identification."),
    PIN_STATUS_BLOCKED(id = 61, label = "personal_pin_status_blocked", source = Source.EID_CLIENT.id, stringKey = "eid_error_pin_status_blocked", showHint = true, imageRes = R.drawable.pi_eid_error_pin, isRecoverable = false, additionalInfo = "eid_error_pin_status_blocked3", loggingErrorCode = "eid.pin.blocked", loggingMessage = "Id card pin is blocked"),
    CARD_NOT_SUPPORTED(id = 63, label = "card_not_supported", source = Source.BACKEND.id, logAsError = false,  stringKey = "eid_error_card_not_supported", imageRes = R.drawable.pi_id_card_error, isRecoverable = false, loggingErrorCode = "eid.card.not.supported", loggingMessage = "Id card is not supported"),
    CARD_EXPIRED(id = 64, label = "card_expired", source = Source.BACKEND.id, logAsError = false, stringKey = "eid_error_card_expired", imageRes = R.drawable.pi_id_card_expired, isRecoverable = false, loggingErrorCode = "eid.card.expired", loggingMessage = "Id card is expired"),
    OTHER_SUB_STATUS(id = 65, label = "ident_status_other_sub_status", source = Source.BACKEND.id, logAsError = false, loggingErrorCode = "eid.other.sub.status", loggingMessage = "Unexpected sub status occurred"),
    STATUS_POLLING_RETRIES_EXCEEDED(id = 66, label = "ident_status_polling_retries_exceeded", source = Source.BACKEND.id, logAsError = false, loggingErrorCode = "eid.status.polling.error", loggingMessage = "Status polling retries expired"),
    GENERAL_ERROR_BACKEND(id = 67, label = "general_error_backend", source = Source.BACKEND.id, logAsError = true, loggingErrorCode = "eid.general.backend.error", loggingMessage = "General backend error occurred (%s). Identification could not be finished"),
    TECHNICAL_ERROR(id = 68, label = "technical_error", source = Source.EID_CLIENT.id, logAsError = true, loggingErrorCode = "eid.technical.client.error", loggingMessage = "Technical eidClient error occurred. Identification could not be finished");

    fun exception(loggingMessageParam: String? = null) = EidException(getTitle(stringKey), getMessage(stringKey), getHint(stringKey, showHint), getAdditionalInfo(additionalInfo), imageRes, id, label, errorSource = source, logAsError = logAsError, isRecoverable = isRecoverable, loggingErrorCode = loggingErrorCode, loggingMessage = String.format(loggingMessage, loggingMessageParam))
    private fun getTitle(stringKey: String): String = LocalizedStrings.getString("${stringKey}0")
    private fun getMessage(stringKey: String): String = LocalizedStrings.getString("${stringKey}1")
    private fun getAdditionalInfo(stringKey: String?): Spanned? = if (stringKey != null) LocalizedStrings.getHtmlString(stringKey) else null
    private fun getHint(stringKey: String, showHint: Boolean): String? = if (showHint) LocalizedStrings.getString("${stringKey}2") else null

    @Keep
    private enum class Source(val id: String) { DEVICE("device"), EID_CLIENT("eid-client"), BACKEND("backend")}
}


open class EidException(val errorTitle: String, val errorMessage: String,val errorHint: String? = null, val errorAdditionalInfo: Spanned? = null,
                        @DrawableRes val errorImageRes: Int? = null, val errorCodeId: Int, val errorCodeName: String,
                        val loggingErrorCode: String, val loggingMessage: String, val errorSource: String,
                        val logAsError: Boolean, val isRecoverable: Boolean) : Exception()