package de.post.ident.internal_core.util

import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.rest.CouponDTO

object BasicCaseManager {
    private val kPrefs = KPrefDelegate(CoreConfig.appContext, "basicIdent")
    var caseData by kPrefs.objNA<OfflineCaseData>("caseData")
    var couponData by kPrefs.objNA<OfflineCouponData>("couponData")
    var caseResponse by kPrefs.objNA<CaseResponseDTO>("caseResponse")

    private val emmiService: CoreEmmiService = CoreEmmiService

    fun setCase(caseId: String, case: CaseResponseDTO) {
        caseData = OfflineCaseData(caseId, case)
    }

    fun getCase(caseId: String): CaseResponseDTO? {
        caseData?.let {
            if (it.caseId == caseId) {
                return it.case
            }
        }
        return null
    }

    suspend fun getCoupon(caseId: String, path: String): CouponDTO {
        val couponAvailable = getOfflineCoupon(caseId)

        return if (couponAvailable != null) {
            couponAvailable
        } else {
            val coupon = emmiService.getCoupon(path)
            couponData = OfflineCouponData(caseId, coupon)
            coupon
        }
    }

    private fun getOfflineCoupon(caseId: String): CouponDTO? {
        couponData?.let {
            if (it.caseId == caseId) {
                return it.coupon
            }
        }
        return null
    }

    internal fun deleteOfflineData() {
        caseData = null
        couponData = null
    }

    fun setCaseresponse(caseResponse: CaseResponseDTO) {
        BasicCaseManager.caseResponse = caseResponse
    }

    fun getCaseresponse(): CaseResponseDTO? {
        caseResponse?.let {
            return it
        }
        return null
    }

    internal fun removeCaseResponse() {
        caseResponse = null
    }
}

@JsonClass(generateAdapter = true)
data class OfflineCaseData(val caseId: String, val case: CaseResponseDTO)

@JsonClass(generateAdapter = true)
data class OfflineCouponData(val caseId: String, val coupon: CouponDTO)