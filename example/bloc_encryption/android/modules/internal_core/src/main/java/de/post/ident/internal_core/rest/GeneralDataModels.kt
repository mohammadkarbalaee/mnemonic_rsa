package de.post.ident.internal_core.rest

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/*
  https://pi.k8s.dev.7p-group.com/emmi/swagger-ui/index.html?configUrl=/emmi/v3/api-docs/swagger-config
 */
@JsonClass(generateAdapter = true)
data class AppConfigDTO(
    @Json(name = "currentVersion") val currentVersion: String,
    @Json(name = "appStoreUrl") val appStoreUrl: String,
    @Json(name = "forceUpdateApp") val forceUpdateApp: Boolean = false,
    @Json(name = "forceUpdateSdk") val forceUpdateSdk: Boolean = false,
    @Json(name = "videoStreamSettings") val videoStreamSettings: VideoStreamSettingsDTO?,
    @Json(name = "pauseStreamWhileUpload") val pauseStreamWhileUpload: Boolean = false,
    @Json(name = "enableWebSockets") val enableWebSockets: Boolean = true,
    @Json(name = "eIdHelpVideoId") val eIdHelpVideoId: String?,
    @Json(name = "minimumSupportedOsVersion") val minimumSupportedOsVersion: Int = 0,
    @Json(name = "minimumSupportedPISdkVersion") val minimumSupportedPiSdkVersion: String?,
    @Json(name = "enableBandwidthAdaptation") val enableBandwidthAdaptation: Boolean = true,
    @Json(name = "texts") val texts: Map<String, String>
)

@JsonClass(generateAdapter = true)
data class VideoStreamSettingsDTO(
    @Json(name = "videoQualityLow") val videoQualityLow: VideoQualityDTO,
    @Json(name = "videoQualityMedium") val videoQualityMedium: VideoQualityDTO,
    @Json(name = "videoQualityHigh") val videoQualityHigh: VideoQualityDTO
)

@JsonClass(generateAdapter = true)
data class VideoQualityDTO(
    @Json(name = "bitRate") val bitRate: Int,
    @Json(name = "frameRate") val frameRate: Int,
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int
)

interface CaseResponse

@JsonClass(generateAdapter = true)
data class ContactDataDTO(
    @Json(name = "contactData") val contactDataList: List<DataFieldDTO>
) : CaseResponse

@JsonClass(generateAdapter = true)
data class DataFieldDTO(
    @Json(name = "key") val key: String,
    @Json(name = "display") val display: String,
    @Json(name = "type") val type: Type,
    @Json(name = "choices") val choices: List<ChoicesDTO>?,
    @Json(name = "maxLength") val maxLength: Int = 50,
    @Json(name = "sortKey") val sortKey: Int = 0,
    @Json(name = "mandatory") val mandatory: Boolean = true,
    @Json(name = "hint") val hint: String? = null,
    @Json(name = "preselectedValue") val preselectedValue: String? = null
) {
    @JsonEnumClass
    @Keep
    enum class Type {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "choice") CHOICE,
        @JsonEnum(name = "text") TEXT,
        @JsonEnum(name = "phone") PHONE,            //Only used in ContactData
        @JsonEnum(name = "email") EMAIL,            //Only used in ContactData
        @JsonEnum(name = "date") DATE,              //Only used in USA
        @JsonEnum(name = "idcard") ID_CARD,         //Only used in USA
        @JsonEnum(name = "postalcode") POSTAL_CODE, //Only used in USA
    }
}

@JsonClass(generateAdapter = true)
data class DataFieldErrorDTO(
    @Json(name = "parameterName") val parameterName: String,
    @Json(name = "errorText") val errorText: String
)

@JsonClass(generateAdapter = true)
data class ChoicesDTO(
    @Json(name = "key") val key: String,
    @Json(name = "display") val display: String
)

@JsonClass(generateAdapter = true)
data class CaseIDErrorDTO(
    @Json(name = "errorCode") val errorCode: String?,
    @Json(name = "errorText") val errorText: String?,
    @Json(name = "signingURL") val signingURL: String?
)

@JsonClass(generateAdapter = true)
data class CaseResponseDTO(
    @Json(name = "caseId") val caseId: String,
    @Json(name = "bcId") val bcId: String,
    @Json(name = "bcName") val bcName: String,
    @Json(name = "clientId") val clientId: String?,
    @Json(name = "principalDisplayName") val principalDisplayName: String,
    @Json(name = "legalEntity") val legalEntity: String,
    @Json(name = "postidentType") var postidentType: PostidentTypeDTO,
    @Json(name = "modules") val modules: IdentMethodModuleDTO,
    @Json(name = "callcenterCategory") val callcenterCategory: String?,
    @Json(name = "caseStatus") val caseStatus: CaseStatusDTO?,
    @Json(name = "toMethodSelection") val toMethodSelection: InteractionDTO?,
    @Json(name = "uploadDate") val uploadDate: String?,
    @Json(name = "identificationGroups") val identificationGroups: List<IdentificationGroupDTO>?
) : CaseResponse

@JsonClass(generateAdapter = true)
data class CaseStatusDTO(
    @Json(name = "buttonText") val buttonText: String?,
    @Json(name = "displayText") val displayText: String?,
    @Json(name = "uploadStatusText") val uploadStatusText: String?,
    @Json(name = "statusCode") val statusCode: StatusCodeDTO?,
    @Json(name = "subStatusCode") val subStatusCode: SubStatusCodeDTO?
)

@JsonEnumClass
@Keep
enum class StatusCodeDTO {
    @JsonEnum(name = "Neu") NEU,
    @JsonEnum(name = "InBearbeitung") IN_BEARBEITUNG,
    @JsonEnum(name = "Abgewiesen") ABGEWIESEN,
    @JsonEnum(name = "Abgeschlossen") ABGESCHLOSSEN,
    @JsonEnum(name = "Abgelehnt") ABGELEHNT
}

@JsonEnumClass
@Keep
enum class SubStatusCodeDTO {
    @JsonEnum(name = "OtherSubStatus", default = true) OTHER_SUB_STATUS,
    @JsonEnum(name = "NotSupported") NOT_SUPPORTED,
    @JsonEnum(name = "Expired") EXPIRED
}

@JsonClass(generateAdapter = true)
data class IdentMethodModuleDTO( // all modules are optional (nullable)
    @Json(name = "identMethodSelection") val identMethodSelection: IdentMethodSelectionDTO?,
    @Json(name = "processDescription") var processDescription: ProcessDescriptionDTO?,
    @Json(name = "basicIdent") val basicIdent: BasicIdentDTO?,
    @Json(name = "videoChat") val videoChat: VideoChatDTO?,
    @Json(name = "makeupRoom") val makeupRoom: MakeupRoomDTO?,
    @Json(name = "identCallTermination") val identCallTermination: IdentCallTerminationDTO?,
    @Json(name = "identStatus") val identStatus: IdentStatusDTO?,
    @Json(name = "identStatusUpdate") val identStatusUpdate: IdentStatusUpdateDTO?,
    @Json(name = "customerFeedback") val customerFeedback: CustomerFeedbackDTO?,
    @Json(name = "selfServicePhoto") val selfServicePhoto: SelfServicePhotosDto?,
    @Json(name = "userSelfAssessment") val userSelfAssessment: UserSelfAssessmentModuleDTO?
)


@JsonClass(generateAdapter = true)
data class CustomerFeedbackDTO(
    @Json(name = "commentHeader") val commentHeader: String?,
    @Json(name = "commentHint") val commentHint: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "continueButton") val continueButton: InteractionDTO?,
    @Json(name = "storeComment") val storeComment: StoreCommentDTO?
) {
    @JsonClass(generateAdapter = true)
    data class StoreCommentDTO(
        @Json(name = "threshold") val threshold: Int
    )
}

@JsonClass(generateAdapter = true)
data class CustomerFeedbackResultDTO(
    @Json(name = "rating") val rating: Int,
    @Json(name = "feedbackText") val feedbackText: String,
    @Json(name = "chatId") val chatId: Int?,
    @Json(name = "caseId") val caseId: String
)

@JsonClass(generateAdapter = true)
data class ProcessDescriptionDTO(
    @Json(name = "title") val title: String?,
    @Json(name = "subtitles") val subtitle: List<String>?,
    @Json(name = "continueButton") val continueButton: InteractionDTO?,
    @Json(name = "legalInfo") val legalInfo: LegalInfoDTO?,
    @Json(name = "faqLink") val faqLink: InteractionDTO?,
    @Json(name = "additionalInfo") val additionalInfo: String?
)

@JsonClass(generateAdapter = true)
data class LegalInfoDTO(
    @Json(name = "footerText") var legalText: String,
    @Json(name = "dataPrivacy") val dataPrivacy: InteractionDTO?,
    @Json(name = "dataPrivacySupplement") val dataPrivacySupplement: InteractionDTO?,
    @Json(name = "acceptTerms") val acceptTerms: InteractionDTO
)

@JsonClass(generateAdapter = true)
data class IdentMethodSelectionDTO(
    @Json(name = "header") val header: String,
    @Json(name = "methodChoice") val identMethodInfos: List<IdentMethodInfoDTO>
)

@JsonClass(generateAdapter = true)
data class AutoAttemptUserFrontendCreateRequestDTO(
    @Json(name = "autoAttemptUserFrontend") val autoAttemptUserFrontend: AutoAttemptUserFrontendDTO
)

@JsonClass(generateAdapter = true)
data class AutoAttemptUserFrontendDTO(
    @Json(name = "featureSet") val featureSet: String?,
    @Json(name = "frontendType") val frontendType: String?,
    @Json(name = "deviceString") val deviceString: String?,
    @Json(name = "deviceClass") val deviceClass: String?,
    @Json(name = "deviceName") val deviceName: String?,
    @Json(name = "deviceBrand") val deviceBrand: String?,
    @Json(name = "userAgentString") val userAgentString: String?,
    @Json(name = "osName") val osName: String?,
    @Json(name = "osVersion") val osVersion: String?,
    @Json(name = "appName") val appName: String?,
    @Json(name = "appVersion") val appVersion: String?,
    @Json(name = "browserName") val browserName: String?,
    @Json(name = "browserVersion") val browserVersion: String?,
    @Json(name = "sdkName") val sdkName: String?,
    @Json(name = "sdkVersion") val sdkVersion: String?,
    @Json(name = "sdkIsUsed") val sdkIsUsed: String?,
    @Json(name = "webName") val webName: String?,
    @Json(name = "webVersion") val webVersion: String?,
    @Json(name = "middlewareName") val middlewareName: String?,
    @Json(name = "middlewareVersion") val middlewareVersion: String?,
    @Json(name = "locale") val locale: String?,
    @Json(name = "additionalData") val additionalData: String?,
)

@JsonClass(generateAdapter = true)
data class IdentMethodInfoDTO(
    @Json(name = "method") var method: IdentMethodDTO,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "enabled") var enabled: Boolean = true,
    @Json(name = "disabledInfo") var disabledInfo: String?,
    @Json(name = "footer") val footer: String?,
    @Json(name = "continueButton") val continueButton: InteractionDTO,
    @Json(name = "expanded") val expanded: Boolean = false,
    @Json(name = "faqLink") val faqLink: InteractionDTO?,
    @Json(name = "forceUpdate") val forceUpdate: Boolean
)

@JsonClass(generateAdapter = true)
data class ProcessDescriptionModuleDTO(
    @Json(name = "caseId") val caseId: String,
    @Json(name = "bcId") val bcId: String,
    @Json(name = "bcName") val bcName: String,
    @Json(name = "clientId") val clientId: String?,
    @Json(name = "principalDisplayName") val principalDisplayName: String,
    @Json(name = "legalEntity") val legalEntity: String,
    @Json(name = "postidentType") var postidentType: PostidentTypeDTO,
    @Json(name = "modules") val modules: IdentMethodModuleDTO,
    @Json(name = "callcenterCategory") val callcenterCategory: String?,
    @Json(name = "caseStatus") val caseStatus: CaseStatusDTO?,
    @Json(name = "toMethodSelection") val toMethodSelection: InteractionDTO?,
    @Json(name = "uploadDate") val uploadDate: String?,
    @Json(name = "identificationGroups") val identificationGroups: List<IdentificationGroupDTO>?
)

@JsonEnumClass
@Keep
enum class IdentMethodDTO {
    @JsonEnum(name = "", default = true) UNKNOWN,
    @JsonEnum(name = "video") VIDEO,
    @JsonEnum(name = "photo") PHOTO,
    @JsonEnum(name = "basic") BASIC,
    @JsonEnum(name = "eid") EID,
    @JsonEnum(name = "autoid") AUTOID
}

@JsonClass(generateAdapter = true)
data class IdentCallTerminationDTO(
    @Json(name = "callBackUrlPreliminarySuccess") var successUrl: CallbackUrlDTO?,
    @Json(name = "callBackUrlInterruption") var failureUrl: CallbackUrlDTO?,
    @Json(name = "showSurvey") val showSurvey: Boolean = false,
    @Json(name = "continueButton") val continueButton: InteractionDTO?,
    @Json(name = "toMethodSelection") val toMethodSelection: InteractionDTO?
)

@JsonClass(generateAdapter = true)
data class IdentStatusDTO(
    @Json(name = "callbackUrl") var callbackUrl: CallbackUrlDTO?,
    @Json(name = "continueButton") var continueButton: InteractionDTO?,
    @Json(name = "title") val title: String?,
    @Json(name = "subtitle") val subtitle: String?,
    @Json(name = "hint") val hint: String?,
    @Json(name = "icon") val icon: String?
)

@JsonClass(generateAdapter = true)
data class IdentStatusUpdateDTO(
    @Json(name = "callBackUrl") var callBackUrl: CallbackUrlDTO?,
    @Json(name = "continueButton") var continueButton: InteractionDTO?,
    @Json(name = "title") val title: String?,
    @Json(name = "subtitle") val subtitle: String?,
    @Json(name = "hint") val hint: String?,
    @Json(name = "icon") val icon: String?
)

@JsonClass(generateAdapter = true)
data class CallbackUrlDTO(
    @Json(name = "webUrl") var webUrl: String?,
    @Json(name = "appUrl") var appUrl: String?
) {
    fun isValid(): Boolean = webUrl != null || appUrl != null
}

@JsonEnumClass
@Keep
enum class PostidentTypeDTO {
    @JsonEnum(name = "classic") CLASSIC,
    @JsonEnum(name = "intern") INTERN,
    @JsonEnum(name = "signing_classic") SIGNING_CLASSIC
}

@JsonClass(generateAdapter = true)
data class InteractionDTO(
    @Json(name = "id") val id: String?,
    @Json(name = "type") val type: InteractionTypeDTO,
    @Json(name = "text") val text: String?,
    @Json(name = "target") val target: String?,
    @Json(name = "content") val content: String?
)

@JsonClass(generateAdapter = true)
data class InteractionAutoidDTO(
    @Json(name = "id") val id: String?,
    @Json(name = "type") val type: InteractionTypeDTO?,
    @Json(name = "text") val text: String?,
    @Json(name = "target") val target: String?,
    @Json(name = "content") val content: String?
)

@JsonEnumClass
@Keep
enum class InteractionTypeDTO {
    @JsonEnum(name = "", default = true) UNKNOWN,
    @JsonEnum(name = "BUTTON") BUTTON,
    @JsonEnum(name = "LINK") LINK,
    @JsonEnum(name = "API") API
}

@JsonClass(generateAdapter = true)
data class ServiceInfoDTO(
    @Json(name = "status") val status: Boolean,
    @Json(name = "statusMessage") val statusMessage: String?,
    @Json(name = "isInBusinessTime") val isInBusinessTime: Boolean,
    @Json(name = "businessTime") val businessTimeText: String?,
    @Json(name = "queueSize") val queueSize: Long? = 0,
    @Json(name = "time") val time: Long? = 0,
    @Json(name = "agentLanguages") val agentLanguageList: List<AgentLanguageDTO>,
    @Json(name = "waitingTimeInfo") val waitingTimeInfo: String?
)

@JsonClass(generateAdapter = true)
data class AgentLanguageDTO(
    @Json(name = "languageCode") val languageCode: String,
    @Json(name = "languageName") val languageName: String
)

@JsonClass(generateAdapter = true)
data class TermsAcceptedDTO(
    @Json(name = "accepted") val accepted: Boolean = true
)

@JsonClass(generateAdapter = true)
data class AttemptResponseDTO(
    @Json(name = "attemptId") val attemptId: String,
    @Json(name = "autoId") val autoId: String?,
    @Json(name = "created") val created: Long? = null
)

@JsonClass(generateAdapter = true)
data class EidAttemptDataDTO(
    @Json(name = "identDocFieldsApprovedByUserTimestamp") var identDocFieldsApprovedByUserTimestamp: Long? = null,
    @Json(name = "idCardHeldToDeviceForReadingCount") var idCardHeldToDeviceForReadingCount: Int? = null,
    @Json(name = "personalPinWrongCount") var personalPinWrongCount: Int? = null,
    @Json(name = "userFrontendResultMajor") var userFrontendResultMajor: String? = null,
    @Json(name = "userFrontendResultMinor") var userFrontendResultMinor: String? = null,
    @Json(name = "userFrontendPollingStartTimestamp") var userFrontendPollingStartTimestamp: Long? = null,
    @Json(name = "eidClientName") var eidClientName: String? = null,
    @Json(name = "eidClientVersion") var eidClientVersion: String? = null,
    @Json(name = "errorCodeIdentifier") var errorCodeIdentifier: String? = null,
    @Json(name = "errorCode") var errorCode: String? = null,
    @Json(name = "errorSource") var errorSource: String? = null,
) {
    fun isNotEmpty(): Boolean {
        return !(identDocFieldsApprovedByUserTimestamp == null && idCardHeldToDeviceForReadingCount == null && personalPinWrongCount == null
                && userFrontendResultMajor == null && userFrontendResultMinor == null && userFrontendPollingStartTimestamp == null
                && eidClientName == null && eidClientVersion == null
                && errorCodeIdentifier == null && errorCode == null && errorSource == null)
    }

    fun increaseCardReadCount() {
        idCardHeldToDeviceForReadingCount =
            if (idCardHeldToDeviceForReadingCount != null) idCardHeldToDeviceForReadingCount!! + 1 else 1
    }

    fun setPinAttemptsLeftCount(pinAttemptsLeftCount: Int?) {
        personalPinWrongCount = when (pinAttemptsLeftCount) {
            0 -> 3
            1 -> 2
            2 -> 1
            else -> 0
        }
    }
}

@JsonClass(generateAdapter = true)
data class SigningDownloadDTO(
    @Json(name = "downloadURL") val downloadURL: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "filename") val filename: String?
)

@JsonClass(generateAdapter = true)
data class MachinePhaseGeneralFeaturesDTO(
    @Json(name = "mrz") val mrz: MrzDTO?
)

@JsonClass(generateAdapter = true)
data class MrzDTO(
    @Json(name = "mediaRecordId") val mediaRecordId: Int?
)

@JsonClass(generateAdapter = true)
data class MediaRecordDTO(
    @Json(name = "id") val id: Int?,
    @Json(name = "lines") val lines: String?,
    @Json(name = "format") val format: MrzFormatDTO?,
    @Json(name = "data") val data: MrzDataDTO?,
)

@JsonClass(generateAdapter = true)
data class MrzFormatDTO(
    @Json(name = "mrzFormatIdDerivedFromDocCodePDC") val mrzFormatIdDerivedFromDocCodePDC: Int?,
    @Json(name = "mrzFormatCodeFromDocCodePDC") val mrzFormatCodeFromDocCodePDC: String?,
    @Json(name = "mrzFormatCodeFromMrzLines") val mrzFormatCodeFromMrzLines: String?,
)

@JsonClass(generateAdapter = true)
data class DvfNextRunResponseDTO(
    @Json(name = "sequenceMediaRecordId") val sequenceMediaRecordId: Int,
    @Json(name = "referenceImageMediaRecordId") val referenceImageMediaRecordId: Int,
    @Json(name = "userStepStatusCode") val userStepStatusCode: String?,
)

@JsonClass(generateAdapter = true)
data class FvlcNextRunResponseDTO(
    @Json(name = "sequenceMediaRecordId") val sequenceMediaRecordId: Int,
    @Json(name = "referenceImageMediaRecordId") val referenceImageMediaRecordId: Int,
    @Json(name = "userStepStatusCode") val userStepStatusCode: String?,
)

@JsonClass(generateAdapter = true)
data class MrzDataDTO(
    @Json(name = "doc") val doc: MrzDocumentDataDTO?,
    @Json(name = "subject") val subject: MrzSubjectDTO?,
    @Json(name = "overallCheckDigit") val overallCheckDigit: Int?,
)

@JsonClass(generateAdapter = true)
data class MrzDocumentDataDTO(
    @Json(name = "countryCodeICAO") val countryCodeICAO: String?,
    @Json(name = "countryCode") val countryCode: String?,
    @Json(name = "typeCodeOriginal") val typeCodeOriginal: String?,
    @Json(name = "typeCandidatesCodePDC") val typeCandidatesCodePDC: String?,
    @Json(name = "number") val number: String?,
    @Json(name = "numberCheckDigit") val numberCheckDigit: Int?,
    @Json(name = "expiryDateOriginal") val expiryDateOriginal: String?,
    @Json(name = "expiryDate") val expiryDate: String?,
    @Json(name = "expiryDateCheckDigit") val expiryDateCheckDigit: Int?,
)

@JsonClass(generateAdapter = true)
data class MrzSubjectDTO(
    @Json(name = "firstNames") val firstNames: String?,
    @Json(name = "birthDateOriginal") val birthDateOriginal: String?,
    @Json(name = "birthDate") val birthDate: String?,
    @Json(name = "birthDateCheckDigit") val birthDateCheckDigit: String?,
    @Json(name = "nationalityCodeICAO") val nationalityCodeICAO: String?,
    @Json(name = "nationalityCode") val numberCheckDigit: Int?
)

@JsonClass(generateAdapter = true)
data class UserDocScanDTO(
    @Json(name = "id") val id: Int?,
    @Json(name = "index") val index: Int?,
    @Json(name = "imagesRequired") val imagesRequired: Int?,
    @Json(name = "docImage1MediaRecordId") val docImage1MediaRecordId: Int?,
    @Json(name = "docImage2MediaRecordId") val docImage2MediaRecordId: Int?,
    @Json(name = "userStepStatusCode") val userStepStatusCode: String?,
    @Json(name = "documentType") var documentType: String = "iddoc_card",
)

@JsonClass(generateAdapter = true)
data class SourceFactsDTO(
    @Json(name = "sourceFact") val sourceFact: SourceFactDTO?
)

@JsonClass(generateAdapter = true)
data class SourceFactDTO(
    @Json(name = "id") val id: Int?,
    @Json(name = "type") val type: String?,
    @Json(name = "payload") val payload: String?
)

abstract class HttpStatusCodeDTO() {
    var httpStatusCode: Int = 0
}

@JsonClass(generateAdapter = true)
data class MpFinishResultDTO(
    @Json(name = "httpStatusCode") val httpStatusCode: Int? = null
)


@JsonClass(generateAdapter = true)
data class StatusAndResultDTO(
    @Json(name = "statusCode") val statusCode: String?,
    @Json(name = "resultLevel1Code") val resultLevel1Code: String?,
    @Json(name = "resultLevel2Code") val resultLevel2Code: String?,
    @Json(name = "resultLevel3Code") val resultLevel3Code: String?,
    @Json(name = "internalNote") val internalNote: String?,
    @Json(name = "externalRemark") val externalRemark: String?,
    @Json(name = "startTime") val startTime: String?,
    @Json(name = "identificationTime") val identificationTime: String?,
    @Json(name = "doneTime") val doneTime: String?
) : HttpStatusCodeDTO()

@JsonClass(generateAdapter = true)
data class UserDocScansDTO(
    @Json(name = "captureImages") val captureImages: Boolean?,
    @Json(name = "signatureExtraction") val signatureExtraction: Boolean?
)

@JsonClass(generateAdapter = true)
data class AutoIdentCheckDocumentDTO(
    @Json(name = "resultData") val resultData: AutoIdentResultDTO,
    @Json(name = "isComplete") val isComplete: Boolean,
)

@JsonClass(generateAdapter = true)
data class AutoIdentResultDTO(
    @Json(name = "doc") val doc: AutoIdentDocumentDTO,
    @Json(name = "fields") val fields: Array<AutoIdentDocumentField>?
)

@JsonClass(generateAdapter = true)
data class AutoIdentDocumentField(
    @Json(name = "code") val code: String?,
    @Json(name = "value") val value: String?,
)

@JsonClass(generateAdapter = true)
data class AutoIdentDocumentDTO(
    @Json(name = "countryCode") val countryCode: String?,
    @Json(name = "typePdcId") val typePdcId: Int?,
    @Json(name = "codePdc") val codePdc: String?,
    @Json(name = "documentNumber") val documentNumber: String?,
)

@JsonClass(generateAdapter = true)
data class AutoIdentStatusDTO(
    @Json(name = "statusCode") val statusCode: Int?,
    @Json(name = "subStatusCode") val subStatusCode: Int?,
    @Json(name = "userFeedback") val userFeedback: String?
)