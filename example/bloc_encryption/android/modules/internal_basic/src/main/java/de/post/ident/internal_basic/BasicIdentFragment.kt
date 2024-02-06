package de.post.ident.internal_basic

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.databinding.PiFragmentBasicIdentBinding
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.KPrefDelegate
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.ui.PiFragmentAdapter

class BasicIdentFragment : Fragment() {
    companion object {
        private val kPrefs = try {
            KPrefDelegate(CoreConfig.appContext, "branch_finder")
        } catch (Error: IllegalStateException) {
            null
        }

        var hasUserConsent by kPrefs!!.boolean("user_consent", false)


        private val BASIC_IDENT_PARAMETER: BundleParameter<CaseResponseDTO> =
            BundleParameter.moshi(CoreEmmiService.moshi, "BASIC_IDENT")
        fun newInstance(caseResponseDTO: CaseResponseDTO): BasicIdentFragment = BasicIdentFragment()
                .withParameter(caseResponseDTO, BASIC_IDENT_PARAMETER)

        private val OFFLINE_COUPON_PARAMETER: BundleParameter<Boolean> =
            BundleParameter.moshi(CoreEmmiService.moshi, "OFFLINE_COUPON")
        fun newInstanceOfflineCoupon(caseResponseDTO: CaseResponseDTO): BasicIdentFragment = BasicIdentFragment()
            .withParameter(caseResponseDTO, BASIC_IDENT_PARAMETER)
            .withParameter(true, OFFLINE_COUPON_PARAMETER)
    }

    private val caseResponse by lazy { checkNotNull(BASIC_IDENT_PARAMETER.getParameter(arguments)) }

    private val viewList = mutableListOf<PiFragmentAdapter.Item>()

    private val emmiReporter = EmmiCoreReporter

    private lateinit var viewBinding: PiFragmentBasicIdentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentBasicIdentBinding.inflate(inflater, container, false)
        initView(viewBinding)
        return viewBinding.root
    }

    private fun initView(viewBinding: PiFragmentBasicIdentBinding) {
        PiFragmentAdapter(viewList, this).attach(viewBinding.viewPager, viewBinding.tabLayout)
        initViewPager()
        // prevent viewPager from intercepting touch events on branch finder map
        viewBinding.viewPager.isUserInputEnabled = false
    }

    private fun initViewPager() {
        val offlineCoupon = OFFLINE_COUPON_PARAMETER.getParameter(arguments) ?: false
        viewList.clear()
        viewList.add(PiFragmentAdapter.Item(LocalizedStrings.getString("basic_tab_title_coupon"), "basic_tab_coupon") {
            if (offlineCoupon) {
                CouponFragment.newInstanceOfflineCoupon(caseResponse)
            } else {
                CouponFragment.newInstance(caseResponse)
            }
        })

        val appInfo = requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
        val mapsApiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")

        if (mapsApiKey != null) {
            viewList.add(PiFragmentAdapter.Item(LocalizedStrings.getString("basic_tab_title_branch_finder"), "basic_tab_branchfinder") {
                if (hasUserConsent) {
                    if (offlineCoupon) {
                        BranchFinderFragment.newInstanceOfflineCoupon(caseResponse)
                    } else {
                        BranchFinderFragment.newInstance(caseResponse)
                    }
                } else {
                    MapConsentFragment.newInstance {
                        hasUserConsent = true
                        initViewPager()
                    }
                }
            })
        } else {
            emmiReporter.send(LogEvent.BA_MAPS_KEY)
        }
        viewBinding.viewPager.adapter?.notifyDataSetChanged()
    }
}