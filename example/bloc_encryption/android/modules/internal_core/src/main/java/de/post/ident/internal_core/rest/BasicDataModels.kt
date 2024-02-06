package de.post.ident.internal_core.rest

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BasicIdentDTO(
        @Json(name = "title") var title: String?,
        @Json(name = "description") var description: String?,
        @Json(name = "downloadCoupon") var downloadCoupon: DownloadCouponDTO?
)

@JsonClass(generateAdapter = true)
data class DownloadCouponDTO(
        @Json(name = "image") var image: ImageDTO?
)

@JsonClass(generateAdapter = true)
data class ImageDTO(
        @Json(name = "id") var id: String?,
        @Json(name = "type") var type: String?,
        @Json(name = "text") var text: String?,
        @Json(name = "action") var action: String?,
        @Json(name = "target") var target: String?,
        @Json(name = "response") var response: String?
)

@JsonClass(generateAdapter = true)
data class CouponDTO(
        @Json(name = "title") var title: String?,
        @Json(name = "description") var description: String?,
        @Json(name = "footer") var footer: String?,
        @Json(name = "accountingNumber") var accountingNumber: String?,
        @Json(name = "referenceId") var referenceId: String?,
        @Json(name = "mimeType") var mimeType: String?,
        @Json(name = "data") var base64ImageData: String?
)


@JsonClass(generateAdapter = true)
data class BranchDetailDTO(
        @Json(name = "address") var branchAddress: BranchAddressDTO?,
        @Json(name = "branchType") var branchType: String?,
        @Json(name = "denotation") var denotation: String?,
        @Json(name = "externalMarker") var externalMarker: String?,
        @Json(name = "id") var id: String?,
        @Json(name = "localisation") var localisation: BranchLocalisationDTO,
        @Json(name = "openingTime") var openingTime: List<BranchOpeningTimeDTO>?
)

@JsonClass(generateAdapter = true)
data class BranchAddressDTO(
        @Json(name = "city") var city: String?,
        @Json(name = "street") var street: String?,
        @Json(name = "streetNumber") var streetNumber: String?,
        @Json(name = "zip") var zip: String?
)

@JsonClass(generateAdapter = true)
data class BranchLocalisationDTO(
        @Json(name = "distance") var distance: Long,
        @Json(name = "latitude") var latitude: Double,
        @Json(name = "longitude") var longitude: Double
)

@JsonClass(generateAdapter = true)
data class BranchOpeningTimeDTO(
        @Json(name = "days") var days: String?,
        @Json(name = "times") var times: List<String>?
)
