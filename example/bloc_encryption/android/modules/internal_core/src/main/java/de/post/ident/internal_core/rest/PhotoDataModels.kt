package de.post.ident.internal_core.rest

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdentificationGroupDTO(
        @Json(name = "groupId") val groupId: GroupIdDTO,
        @Json(name = "groupNumber") val groupNumber: Int,
        @Json(name = "groupName") val groupName: String,
        @Json(name = "status") val status: StatusDTO,
        @Json(name = "steps") val steps: List<IdentificationStepDTO>
){
    @JsonEnumClass
    @Keep
    enum class GroupIdDTO {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "identificationCard") IDENTIFICATION_CARD,
        @JsonEnum(name = "driversLicense") DRIVERS_LICENSE,
        @JsonEnum(name = "videoAuth") VIDEO_AUTH
    }
}

@JsonClass(generateAdapter = true)
data class IdentificationStepDTO(
        @Json(name = "stepNumber") val stepNumber: Int,
        @Json(name = "header") val header: String? = null,
        @Json(name = "status") var status: StatusDTO,
        @Json(name = "statusMessage") var statusMessage: String? = null,
        @Json(name = "stepType") val stepType: StepTypeDTO,
        @Json(name = "iconId") val iconId: String,
        @Json(name = "descriptionHeader") val descriptionHeader: String? = null,
        @Json(name = "descriptionFooter") val descriptionFooter: String? = null,
        @Json(name = "fileName") val fileName: String
){
    @JsonEnumClass
    @Keep
    enum class StepTypeDTO {
        @JsonEnum(name = "", default = true) UNKNOWN,
        @JsonEnum(name = "imageFront") IMAGEFRONT,
        @JsonEnum(name = "imageBack") IMAGEBACK,
        @JsonEnum(name = "video") VIDEO
    }
}

@JsonEnumClass
@Keep
enum class StatusDTO {
    @JsonEnum(name = "OPEN", default = true) OPEN,
    @JsonEnum(name = "CREATED") CREATED,
    @JsonEnum(name = "REJECTED") REJECTED,
    @JsonEnum(name = "CLOSED") CLOSED
}


