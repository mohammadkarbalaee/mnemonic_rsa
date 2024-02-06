package de.post.ident.internal_core.rest

import android.os.Build
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class SelfServicePhotosDto(
        @Json(name = "postRecords") val postRecords: InteractionDTO,
        @Json(name = "records") val records: List<SelfServicePhotoDto>
) {
    fun isValid(): Boolean {
        return (postRecords.target?.isNotBlank() == true && records.isNotEmpty())
    }
}
@JsonClass(generateAdapter = true)
data class SelfServicePhotoDto(
        @Json(name = "title") val title: String,
        @Json(name = "subtitle") val subtitle: String,
        @Json(name = "uploadName") val uploadName: String,
        @Json(name = "type") val type: DocumentSide
) {
    @JsonEnumClass
    @Keep
    enum class DocumentSide {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "FRONTSIDE") FRONTSIDE,
        @JsonEnum(name = "BACKSIDE") BACKSIDE
    }
}

@JsonClass(generateAdapter = true)
data class SelfServicePhotoCropResponseDto(
        @Json(name = "x") val x: Float,
        @Json(name = "y") val y: Float,
        @Json(name = "width") val width: Float,
        @Json(name = "height") val height: Float,
        @Json(name = "rotation") val rotation: Float
)

@JsonClass(generateAdapter = true)
data class UserSelfAssessmentDTO(
    @Json(name = "target") val target: String,
    @Json(name = "id") val id: String,
    @Json(name = "action") val action: String,
    @Json(name = "type") val type: String
) {
    fun isValid(): Boolean {
        return target.isNotBlank() && id.isNotBlank() && action.isNotBlank() && type.isNotBlank()
    }
}

@JsonClass(generateAdapter = true)
data class UserSelfAssessmentModuleDTO(
        @Json(name = "title") val title: String,
        @Json(name = "createUsa") val userSelfAssessmentCreate: UserSelfAssessmentDTO,
        @Json(name = "getUsa") val userSelfAssessmentGet: UserSelfAssessmentDTO,
        @Json(name = "documentData") val userSelfAssessmentDocumentData: UserSelfAssessmentDTO
) {
    fun isValid(): Boolean {
        return title.isNotBlank() && userSelfAssessmentCreate.isValid() && userSelfAssessmentGet.isValid() && userSelfAssessmentDocumentData.isValid()
    }
}

@JsonClass(generateAdapter = true)
data class DocumentDataDto(
        @Json(name = "imageFrontSide") val imageFrontSide: String,
        @Json(name = "imageBackSide") val imageBackSide: String,
        @Json(name = "fields") val fields: List<FieldInfoDto>
)

@JsonClass(generateAdapter = true)
data class FieldInfoDto(
        @Json(name = "key") val key: String,
        @Json(name = "x") val x: Float,
        @Json(name = "y") val y: Float,
        @Json(name = "width") val width: Float,
        @Json(name = "height") val height: Float,
        @Json(name = "side") val side: SideDTO
) {
@JsonEnumClass
@Keep
enum class SideDTO {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "frontSide") FRONT_SIDE,
        @JsonEnum(name = "backSide") BACK_SIDE
    }
}

@JsonClass(generateAdapter = true)
data class VideoChatDTO(
        @Json(name = "captureUserFacePhoto") var captureUserFacePhoto: Boolean
)

@JsonClass(generateAdapter = true)
data class MakeupRoomDTO(
        @Json(name = "legalNote") var legalNote: String?,
        @Json(name = "legalTerms") var legalTerms: InteractionDTO?,
        @Json(name = "videoRecord") var videoRecord: InteractionDTO?,
        @Json(name = "videoRecordTerms") var videoRecordTerms: VideoRecordTermsDTO?
)

@JsonClass(generateAdapter = true)
data class MakeupRoomDataDTO(
        @Json(name = "makeupRoomStart") val makeupRoomStart: Long? = null,
        @Json(name = "preferredUserLanguage") val preferredUserLanguage: String? = null,
        @Json(name = "userHasCalled") val userHasCalled: Boolean? = null,
        @Json(name = "userHasCalledTimestamp") val userHasCalledTimestamp: Long? = null,
        @Json(name = "makeupRoomEnd") val makeupRoomEnd: Long? = null
)

@JsonClass(generateAdapter = true)
data class VideoConnectionQualityDTO(
    @Json(name = "videoConnectionQuality") val data: VideoConnectionQualityDataDTO? = null
)

@JsonClass(generateAdapter = true)
data class VideoConnectionQualityDataDTO(
    @Json(name = "packetLoss") val packetLoss: Int? = null,
    @Json(name = "jitter") val jitter: Int? = null
)

@JsonClass(generateAdapter = true)
data class VideoRecordTermsDTO(
        @Json(name = "text") var text: String
)

@JsonClass(generateAdapter = true)
data class ChatLaunchIdDTO(
        @Json(name = "chatLaunchId") val chatLaunchId: String?
) {
    fun isValid(): Boolean = chatLaunchId.isNullOrEmpty().not() && chatLaunchId.equals("0").not()
}

@JsonClass(generateAdapter = true)
data class UserFrontendInformationDTO(
        @Json(name = "userFrontendFeatureSet") val userFrontendFeatureSet: List<String>? = null,
        @Json(name = "userFrontendLocale") val userFrontendLocale: String = Locale.getDefault().language,
        @Json(name = "deviceName") val deviceName: String = Build.BRAND + " " + Build.MODEL,
        @Json(name = "additionalData") val additionalData: String? = null
)

@JsonClass(generateAdapter = true)
data class OcrErrorDto(
        @Json(name = "callbackUrl") val callbackUrl: CallbackUrlDTO? = null,
        @Json(name = "continueButton") val continueButton: InteractionDTO,
        @Json(name = "errorCode") val errorCode: ErrorCode,
        @Json(name = "title") val title: String,
        @Json(name = "description") val description: String,
        @Json(name = "hint") val hint: String,
        ) {
    @JsonEnumClass
    @Keep
    enum class ErrorCode {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "VIC_006") EXPIRED,
        @JsonEnum(name = "VIC_007") UNSUPPORTED,
        @JsonEnum(name = "GENERAL") GENERAL
    }
}