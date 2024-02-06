package de.post.ident.internal_basic

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.rest.CouponDTO
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_basic.databinding.PiCouponFragmentBinding
import de.post.ident.internal_core.util.BasicCaseManager
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch


class CouponFragment : Fragment() {
    companion object {

        private val COUPON_PARAMETER: BundleParameter<CaseResponseDTO> =
            BundleParameter.moshi(CoreEmmiService.moshi, "BASIC_IDENT")
        fun newInstance(caseResponseDTO: CaseResponseDTO): CouponFragment = CouponFragment()
                .withParameter(caseResponseDTO, COUPON_PARAMETER)

        private val OFFLINE_COUPON_PARAMETER: BundleParameter<Boolean> =
            BundleParameter.moshi(CoreEmmiService.moshi, "OFFLINE_COUPON")
        fun newInstanceOfflineCoupon(caseResponseDTO: CaseResponseDTO): CouponFragment = CouponFragment()
            .withParameter(caseResponseDTO, COUPON_PARAMETER)
            .withParameter(true, OFFLINE_COUPON_PARAMETER)
    }

    private lateinit var viewBinding: PiCouponFragmentBinding
    private val emmiReporter = EmmiCoreReporter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiCouponFragmentBinding.inflate(inflater, container, false)
        initView(viewBinding)
        return viewBinding.root
    }

    override fun onResume() {
        super.onResume()
        toggleScreenBrightness(requireActivity(), true)
        emmiReporter.send(LogEvent.BA_DISPLAY_COUPON)
    }

    override fun onPause() {
        toggleScreenBrightness(requireActivity(), false)
        super.onPause()
    }

    private fun toggleScreenBrightness(activity: Activity, bright: Boolean) {
        val attr = activity.window.attributes
        attr.screenBrightness = if (bright) 1f else -1f
        activity.window.attributes = attr
    }

    private fun initView(viewBinding: PiCouponFragmentBinding) {
        val caseResponse = checkNotNull(COUPON_PARAMETER.getParameter(arguments))

        viewBinding.piCouponContent.couponImage.contentDescription = LocalizedStrings.getString("basic_image_desc_coupon")
        viewBinding.piCouponContent.labelBillingNumber.text = LocalizedStrings.getString("basic_billing_number")
        viewBinding.piCouponContent.labelReferenceNumber.text = LocalizedStrings.getString("basic_reference_number")
        viewBinding.piCouponContent.labelCaseId.text = LocalizedStrings.getString("basic_case_id")
        viewBinding.piCouponContent.errorText.text = LocalizedStrings.getString("branch_finder_network_error")

        if (caseResponse.modules.basicIdent != null) {
            viewBinding.title.text = caseResponse.modules.basicIdent?.title
            viewBinding.subtitle.text = caseResponse.modules.basicIdent?.description
        }

        val path = caseResponse.modules.basicIdent?.downloadCoupon?.image?.target?.trimStart('/')
        if (path != null) {
            lifecycleScope.launch {
                viewBinding.piCouponContent.loadingIndicator.visibility = View.VISIBLE

                try {
                    val coupon = BasicCaseManager.getCoupon(checkNotNull(Commons.caseId), path)
                    displayCoupon(coupon)
                } catch (err: Throwable) {
                    ensureActive()
                    viewBinding.piCouponContent.errorText.visibility = View.VISIBLE
                } finally {
                    ensureActive()
                    viewBinding.piCouponContent.loadingIndicator.visibility = View.GONE
                }
            }
        } else {
            viewBinding.piCouponContent.errorText.visibility = View.VISIBLE
        }

        if (OFFLINE_COUPON_PARAMETER.getParameter(arguments) ?: false) {
            viewBinding.piMethodSelectionButton.methodSelectionButton.visibility = View.GONE
        } else if (caseResponse.toMethodSelection != null && Commons.skipMethodSelection.not()) {
            val methodSelectionButton = viewBinding.piMethodSelectionButton.methodSelectionButton
            methodSelectionButton.text = caseResponse.toMethodSelection?.text
            methodSelectionButton.visibility = View.VISIBLE

            methodSelectionButton.setOnClickListener {
                requireActivity().finish()
            }
        }
    }

    private fun displayCoupon(coupon: CouponDTO) {
        coupon.base64ImageData?.let {
            viewBinding.piCouponContent.couponImage.setImageBitmap(decoder(it))
        }

        viewBinding.piCouponContent.billingNumber.text = coupon.accountingNumber
        viewBinding.piCouponContent.referenceNumber.text = coupon.referenceId
        viewBinding.piCouponContent.caseId.text = Commons.caseId
        viewBinding.piCouponContent.couponTitle.text = coupon.title
        viewBinding.piCouponContent.couponSubtitle.text = coupon.description
        viewBinding.piCouponContent.couponFooter.text = coupon.footer
        viewBinding.piCouponContent.couponContent.visibility = View.VISIBLE
    }


    private fun decoder(base64Str: String): Bitmap {
        val imageByteArray = Base64.decode(base64Str, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)
    }

}



