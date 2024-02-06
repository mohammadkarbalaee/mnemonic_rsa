package de.post.ident.internal_core.process_description

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.R
import de.post.ident.internal_core.databinding.PiFragmentLegalLinksBinding
import de.post.ident.internal_core.databinding.PiFragmentProcessDescriptionBinding
import de.post.ident.internal_core.databinding.PiProcessDescriptionPageBinding
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.IdentMethodDTO
import de.post.ident.internal_core.rest.LegalInfoDTO
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.*
import de.post.ident.internal_core.util.ui.recyclerview.CellRecyclerAdapter
import de.post.ident.internal_core.util.ui.recyclerview.ViewBindingCell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProcessDescriptionFragment : Fragment() {
    companion object {

        private val PROCESS_DESCRIPTION_PARAMETER: BundleParameter<ProcessDescriptionData> = BundleParameter.moshi(CoreEmmiService.moshi, "PROCESS_DESCRIPTION")

        fun newInstance(caseResponseDTO: CaseResponseDTO, pageDataList: List<PageData>, method: IdentMethodDTO): ProcessDescriptionFragment = ProcessDescriptionFragment()
                .withParameter(ProcessDescriptionData(caseResponseDTO, pageDataList, method), PROCESS_DESCRIPTION_PARAMETER)
    }

    private lateinit var viewBinding: PiFragmentProcessDescriptionBinding

    private val emmiReporter = EmmiCoreReporter
    private val viewPagerAnimationDelay = 7000L //ms

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentProcessDescriptionBinding.inflate(inflater, container, false)
        initView(viewBinding)
        return viewBinding.root
    }

    private fun initView(viewBinding: PiFragmentProcessDescriptionBinding) {
        val processDescriptionData = checkNotNull(PROCESS_DESCRIPTION_PARAMETER.getParameter(arguments))
        val processDescription = checkNotNull(processDescriptionData.caseResponse.modules.processDescription)

        log("case response: ${processDescriptionData.caseResponse}")

        if (processDescription.title != null) viewBinding.welcomeTitle.text = HtmlCompat.fromHtml(processDescription.title, HtmlCompat.FROM_HTML_MODE_LEGACY)

        log("wurst ${processDescription.faqLink}")
        viewBinding.faqLink.let { faqBtn ->
            val text = processDescription.faqLink?.text
            val url = processDescription.faqLink?.target


            faqBtn.isVisible = text != null && url != null
            faqBtn.text = text

            faqBtn.setOnClickListener {
                if (url != null) {
                    WebviewActivity.start(requireActivity(), url)
                    val eventContext = mutableMapOf(
                            "identificationMethod" to processDescriptionData.method.name,
                            "context" to "processDescription")
                    if (processDescriptionData.method.name == IdentMethod.AUTOID.name) {
                        eventContext.put("method", "autoIdent")
                    }
                    emmiReporter.send(logEvent = LogEvent.PD_FAQ, eventContext = eventContext)
                }
            }
        }

        val legalInfo = processDescription.legalInfo

        viewBinding.legalLink.let { legalBtn ->
            val text = legalInfo?.legalText
            legalBtn.isVisible = text != null
            legalBtn.text = text

            legalBtn.setOnClickListener {
                if (legalInfo != null) {
                    DialogFragmentLegalLinks.newInstance(legalInfo).show(childFragmentManager, "LEGAL_INFO")
                }
            }
        }

        processDescription.additionalInfo?.let {
            viewBinding.additionalInfo.text = HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY)
            viewBinding.additionalInfo.movementMethod = LinkMovementMethod.getInstance()
            viewBinding.additionalInfo.isVisible = true
        }

        setupViewPager(viewBinding, processDescriptionData)

        lifecycleScope.launch {
            delay(viewPagerAnimationDelay)

            while (viewBinding.viewPager.isUserInputEnabled.not()) {

                if (viewBinding.viewPager.currentItem == processDescriptionData.pageDataList.size - 1) {
                    viewBinding.viewPager.setCurrentItem(0, true)
                } else {
                    viewBinding.viewPager.setCurrentItem(viewBinding.viewPager.currentItem + 1, true)
                }

                delay(viewPagerAnimationDelay)
            }
        }
        startIdentMethodPreCheck(processDescriptionData.method, requireActivity(), emmiReporter)
    }

    private fun startIdentMethodPreCheck(method: IdentMethodDTO, act: Activity, emmiReporter: EmmiCoreReporter) {
        when (method) {
            IdentMethodDTO.EID -> {
                when (getNFCStatus(act)) {
                    NFCStatus.NOT_PRESENT -> showIdentMethodImpossibleDialog("methodSelection_noNFC_Headline", "methodSelection_noNFC_adapter_Body", "nfc", emmiReporter, act)
                    NFCStatus.NOT_SUPPORTED -> showIdentMethodImpossibleDialog("methodSelection_noNFC_Headline", "methodSelection_noNFC_Body", "nfc", emmiReporter, act)
                    else -> {}
                }
            }
            IdentMethodDTO.VIDEO -> {}
            IdentMethodDTO.PHOTO -> {}
            IdentMethodDTO.BASIC -> {}
            IdentMethodDTO.AUTOID -> {}
            else -> {}
        }
    }

    private fun showIdentMethodImpossibleDialog(titleKey: String, textKey: String, reason: String, emmiReporter: EmmiCoreReporter, act: Activity) {
        emmiReporter.send(logEvent = LogEvent.EI_IMPOSSIBLE, eventContext = mapOf("reason" to reason))
        showAlertDialog(act, title = LocalizedStrings.getString(titleKey), msg = LocalizedStrings.getString(textKey), imgResId = 0, isCancelable = false, onFinish = { (act as BaseModuleActivity).finishSdkWithCancelledByUser() })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViewPager(viewBinding: PiFragmentProcessDescriptionBinding, processDescriptionData: ProcessDescriptionData) {

        val pageData = processDescriptionData.pageDataList.map { ProcessDescriptionPageCell(it) }

        val adapter = CellRecyclerAdapter(pageData)
        viewBinding.viewPager.adapter = adapter
        TabLayoutMediator(viewBinding.tabLayout, viewBinding.viewPager) { _, _ -> }.attach()

        viewBinding.viewPager.isUserInputEnabled = false // disable initially to enable touch listener

        viewBinding.viewPager.setOnTouchListener { view, event ->
            viewBinding.viewPager.isUserInputEnabled = true
            // dispatch event directly to disabled viewpager
            view.dispatchTouchEvent(event)
        }
    }

    @JsonClass(generateAdapter = true)
    data class PageData(val title: String?, @DrawableRes val drawableRes: Int)

    @JsonClass(generateAdapter = true)
    data class ProcessDescriptionData(val caseResponse: CaseResponseDTO, val pageDataList: List<PageData>, val method: IdentMethodDTO)
}

class ProcessDescriptionPageCell(val pageData: ProcessDescriptionFragment.PageData) : ViewBindingCell<PiProcessDescriptionPageBinding>() {
    override fun bindView(viewHolder: PiProcessDescriptionPageBinding) {
        if (pageData.title != null) viewHolder.title.text = HtmlCompat.fromHtml(pageData.title, HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewHolder.image.setImageResource(pageData.drawableRes)
    }

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean) = PiProcessDescriptionPageBinding.inflate(inflater, parent, false)
}

class DialogFragmentLegalLinks : DialogFragment() {
    companion object {

        private val LEGAL_LINKS_PARAMETER: BundleParameter<LegalInfoDTO> = BundleParameter.moshi(CoreEmmiService.moshi, "LEGAL_LINKS")

        fun newInstance(legalInfo: LegalInfoDTO): DialogFragmentLegalLinks = DialogFragmentLegalLinks()
                .withParameter(legalInfo, LEGAL_LINKS_PARAMETER)
    }

    private lateinit var viewBinding: PiFragmentLegalLinksBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NORMAL, android.R.style.ThemeOverlay_Material_ActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentLegalLinksBinding.inflate(inflater, container, false)
        val legalLinks = checkNotNull(LEGAL_LINKS_PARAMETER.getParameter(arguments))
        showBackButton(viewBinding.root.findViewById(R.id.toolbar_actionbar), true) { dismiss() }
        val privacy = legalLinks.dataPrivacy
        if (privacy?.target != null) {
            viewBinding.tvPrivacyPolicy.text = privacy.text
            viewBinding.tvPrivacyPolicy.visibility = View.VISIBLE
            viewBinding.separatorLine1.visibility = View.VISIBLE
            viewBinding.tvPrivacyPolicy.setOnClickListener {
                openBrowser(privacy.target)
            }
        }

        val supplement = legalLinks.dataPrivacySupplement
        if (supplement?.target != null) {
            viewBinding.tvAdditionalPrivacyPolicy.text = supplement.text
            viewBinding.tvAdditionalPrivacyPolicy.visibility = View.VISIBLE
            viewBinding.separatorLine2.visibility = View.VISIBLE
            viewBinding.tvAdditionalPrivacyPolicy.setOnClickListener {
                openBrowser(supplement.target)
            }
        }

        return viewBinding.root
    }

    private fun openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showAlertDialog(requireContext(), LocalizedStrings.getString("err_dialog_browser_error_title"), LocalizedStrings.getString("err_dialog_browser_error_text"))
        }
    }
}