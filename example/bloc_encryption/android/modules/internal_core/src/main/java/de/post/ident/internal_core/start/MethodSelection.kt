package de.post.ident.internal_core.start

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.R
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.databinding.PiFragmentMethodSelectionBinding
import de.post.ident.internal_core.databinding.PiMethodselectionItemBinding
import de.post.ident.internal_core.process_description.DialogFragmentCallcenterClosed
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.IdentMethodClassMapping.Companion.toClassMapping
import de.post.ident.internal_core.util.*
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.lang.Exception
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class IdentMethodStart(
        private val activity: Activity,
        val onContactDataMissing: (ContactDataDTO) -> Unit,
        val onError: () -> Unit = {}
) {
    private val emmiService = CoreEmmiService
    private val novomindService = NovomindCertificateCheck
    private val emmiReporter = EmmiCoreReporter

    suspend fun startIdentMethod(identMethodInfo: IdentMethodInfoDTO, caseInfo: CaseResponseDTO) {
        if (identMethodInfo.forceUpdate) {
            (activity as StartActivity).showMethodForceUpdateDialog()
        } else {
            when (identMethodInfo.method) {
                IdentMethodDTO.VIDEO -> {
                    emmiReporter.send(logEvent = LogEvent.PD_START_VIDEO,
                        eventContext = mapOf("serviceCenterCategory" to caseInfo.callcenterCategory.toString()))
                    startVideoIdent(identMethodInfo, caseInfo)
                }
                IdentMethodDTO.BASIC -> {
                    emmiReporter.send(LogEvent.PD_START_BASIC)
                    startIdent(identMethodInfo, IdentMethodClassMapping.BASIC, caseInfo)
                }
                IdentMethodDTO.EID -> {
                    emmiReporter.send(LogEvent.PD_START_EID)
                    startEidIdent(identMethodInfo, caseInfo)
                }
                IdentMethodDTO.PHOTO -> {
                    emmiReporter.send(LogEvent.PD_START_PHOTO)
                    startIdent(identMethodInfo, IdentMethodClassMapping.PHOTO, caseInfo)
                }
                IdentMethodDTO.AUTOID -> {
                    emmiReporter.send(LogEvent.PD_START_AUTOID)
                    startIdent(identMethodInfo, IdentMethodClassMapping.AUTOID, caseInfo)
                }
                else -> log(Throwable("illegal ident method!"))
            }
        }
    }

    private suspend fun startVideoIdent(identMethodInfo: IdentMethodInfoDTO, caseResponse: CaseResponseDTO) {
        try {
            var caseInformation: CaseResponseDTO = caseResponse
            caseInformation.modules.processDescription = identMethodInfo.continueButton.target?.let {
                emmiService.getProcessDescription(it)
            }
            BasicCaseManager.setCaseresponse(caseInformation)
            val serviceCenterInfo: ServiceInfoDTO? = if (CoreConfig.isITU.not()) emmiService.getServiceCenterInfo(caseInformation.callcenterCategory, Commons.caseId) else null

            if (Build.VERSION.SDK_INT < 23) throw VideochatRequirementsError()
            if (serviceCenterInfo != null && Commons.identMethodsAvailable > 1) {
                if (serviceCenterInfo.status.not() || serviceCenterInfo.isInBusinessTime.not()) throw NoAgentAvailableError(LocalizedStrings.getString("dialog_no_agent_available"))
            }

            novomindService.testVideoCertificate()

            if (serviceCenterInfo != null && (serviceCenterInfo.isInBusinessTime.not() || serviceCenterInfo.status.not())) {
                DialogFragmentCallcenterClosed.newInstance(DialogFragmentCallcenterClosed.CallcenterClosedData(caseInformation.toMethodSelection?.text, serviceCenterInfo)) {
                    if (Commons.identMethodsAvailable == 1) activity.finish()
                }.show((activity as AppCompatActivity).supportFragmentManager, "CALLCENTER_CLOSED")
            } else {
                checkNotNull(caseInformation.modules.processDescription != null)
                activity.startIdentActivity(IdentMethodClassMapping.VIDEO, caseInformation, identMethodInfo)
            }
        } catch (err: Throwable) {
            when (err) {
                is CaseResponseMissingData -> onContactDataMissing(err.contactData)
                is VideochatRequirementsError -> showAlertDialog(activity, LocalizedStrings.getString("dialog_videochat_requirements"), onError)
                is SSLHandshakeException, is SSLPeerUnverifiedException, is SSLException -> {
                    if (CoreConfig.isSdk) {
                            (activity as StartActivity).showPostidentDeeplinkDialog(SdkResultCodes.ERROR_SSL_PINNING, true)
                    } else {
                        if (AppUpdateService().updateAvailable(activity)) {
                                (activity as StartActivity).showAppUpdateDialog()
                        } else {
                            showAlertDialog(activity, LocalizedStrings.getString(R.string.err_dialog_ssl_error_text), onError)
                        }
                    }
                }
                else -> showAlertDialog(activity, err.getUserMessage(), onError)
            }
        }
    }

    private suspend fun startEidIdent(identMethodInfo: IdentMethodInfoDTO, caseResponse: CaseResponseDTO) {
        try {
            if (Build.VERSION.SDK_INT < 24) throw EidRequirementsError()
            // the AusweisApp2 SDK does only support the processor architecture 'arm64-v8a'
            if (Build.SUPPORTED_ABIS.contains("arm64-v8a").not()) {
                showEidImpossibleDialog("eid_error_extended_length_issue1", "processorArchitecture")
                return
            }
            startIdent(identMethodInfo, IdentMethodClassMapping.EID, caseResponse)
        } catch (err: Throwable) {
            when (err) {
                is CaseResponseMissingData -> onContactDataMissing(err.contactData)
                is EidRequirementsError -> showEidImpossibleDialog("eid_error_extended_length_issue1", "Android 5 or 6")
                else -> showAlertDialog(activity, err.getUserMessage(), onError)
            }
        }
    }

    private suspend fun startIdent(identMethodInfo: IdentMethodInfoDTO, methodName: IdentMethodClassMapping, caseResponse: CaseResponseDTO) {
        try {
            val caseInformation: CaseResponseDTO = caseResponse
            caseInformation.modules.processDescription = identMethodInfo.continueButton.target?.let {
                emmiService.getProcessDescription(it)
            }
            checkNotNull(caseInformation.modules.processDescription != null)
            BasicCaseManager.setCaseresponse(caseInformation)
            activity.startIdentActivity(methodName, caseInformation, identMethodInfo)
        } catch (err: Throwable) {
            if (err is CaseResponseMissingData) onContactDataMissing(err.contactData)
            else showAlertDialog(activity, err.getUserMessage(), onError)
        }
    }

    private fun showEidImpossibleDialog(errorStringKey: String, reason: String) {
        emmiReporter.send(logEvent = LogEvent.EI_IMPOSSIBLE, eventContext = mapOf("reason" to reason))
        showAlertDialog(activity, LocalizedStrings.getString(errorStringKey), onError)
    }
}

class MethodSelectionFragment : Fragment() {
    companion object {

        private val METHOD_SELECTION_PARAMETER: BundleParameter<CaseResponseDTO> = BundleParameter.moshi(CoreEmmiService.moshi, "METHOD_SELECTION")

        fun newInstance(caseResponseDTO: CaseResponseDTO): MethodSelectionFragment {
            val fragment = MethodSelectionFragment()
            val bundle = Bundle()
            METHOD_SELECTION_PARAMETER.putParameter(bundle, caseResponseDTO)
            fragment.arguments = bundle
            return fragment
        }
    }

    private lateinit var viewBinding: PiFragmentMethodSelectionBinding

    private val emmiReporter = EmmiCoreReporter
    private var itemList = mutableListOf<MethodSelectionItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentMethodSelectionBinding.inflate(inflater, container, false)
        initView(inflater, viewBinding)
        return viewBinding.root
    }

    override fun onStart() {
        super.onStart()
        emmiReporter.send(LogEvent.CE_METHOD_SELECTION)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                val caseInfo = CoreEmmiService.getCaseInformationByCaseId(requireNotNull(Commons.caseId))
                itemList.forEach { item ->
                    caseInfo.modules.identMethodSelection?.identMethodInfos?.forEach {
                        val updatedIdentMethodInfo = updateIdentMethodInfo(it, requireActivity())
                        if (updatedIdentMethodInfo.method == item.identMethod.method) {
                            item.updateIdentMethod(it)
                        }
                    }
                }
            } catch (e: Exception) {
                log("error getting caseInfo and updating MethodSelection")
            }
        }
    }
    private fun initView(inflater: LayoutInflater, views: PiFragmentMethodSelectionBinding) {
        val caseInfo = checkNotNull(METHOD_SELECTION_PARAMETER.getParameter(arguments))
        log("case response: $caseInfo")

        createIdentMethodListView(inflater, caseInfo)
    }

    private fun createIdentMethodListView(inflater: LayoutInflater, caseInfo: CaseResponseDTO) {
        viewBinding.piMethodSelectionHeader.text = caseInfo.modules.identMethodSelection?.header
        itemList = mutableListOf()
        caseInfo.modules.identMethodSelection?.identMethodInfos?.filter {
            requireContext().isIdentActivityAvailable(it.method.toClassMapping())
        }?.forEach { identMethodInfo ->
            val updatedIdentMethodInfo = updateIdentMethodInfo(identMethodInfo, requireActivity())
            itemList.add(MethodSelectionItem(
                inflater = inflater,
                views = viewBinding,
                identMethod = updatedIdentMethodInfo
            ) { action ->
                when (action) {
                    is ActionType.Faq -> WebviewActivity.start(requireActivity(), action.url)
                    is ActionType.Expanded -> {
                        val item = action.methodSelectionItem

                        if (item.isItemExpanded) {
                            item.setExpanded(false)
                        } else {
                            itemList.forEach { // collapse all other items
                                it.setExpanded(it == item)
                            }
                        }
                    }
                    is ActionType.Started -> startIdent(action.identMethodInfo, caseInfo)
                }
            })
        }
        if (itemList.size == 1) {
            itemList.first().setExpanded(true)
        }
    }

    // used for disabling identMethods in addition to disabledInfo from EMMI
    private fun updateIdentMethodInfo(identMethodInfo: IdentMethodInfoDTO, act: Activity): IdentMethodInfoDTO {
        when (identMethodInfo.method) {
            IdentMethodDTO.EID -> {
                return when (getNFCStatus(act)) {
                    NFCStatus.NOT_PRESENT -> disableIdentMethodInfoItem(identMethodInfo, "methodSelection_noNFC_adapter_Hint")
                    NFCStatus.NOT_SUPPORTED -> disableIdentMethodInfoItem(identMethodInfo, "methodSelection_noNFC_Hint")
                    else -> identMethodInfo
                }
            }
            IdentMethodDTO.VIDEO -> {}
            IdentMethodDTO.PHOTO -> {}
            IdentMethodDTO.BASIC -> {}
            IdentMethodDTO.AUTOID -> {}
            else -> {}
        }
        return identMethodInfo
    }

    private fun disableIdentMethodInfoItem(identMethodInfo: IdentMethodInfoDTO, disabledInfoKey: String): IdentMethodInfoDTO {
        identMethodInfo.disabledInfo = LocalizedStrings.getString(disabledInfoKey)
        identMethodInfo.enabled = false
        return identMethodInfo
    }

    private fun startIdent(identMethodInfo: IdentMethodInfoDTO, caseInfo: CaseResponseDTO) {
        lifecycleScope.launch {
            try {
                setButtonsEnabled(false)
                IdentMethodStart(requireActivity(), onContactDataMissing = { contactDataMissing(it) }).startIdentMethod(identMethodInfo, caseInfo)
            } finally {
                ensureActive()
                setButtonsEnabled(true)
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        itemList.forEach { item ->
            item.setButtonEnabled(enabled)
        }
    }
}

// callbacks
private sealed class ActionType {
    data class Faq(val url: String) : ActionType()
    data class Expanded(val methodSelectionItem: MethodSelectionItem) : ActionType()
    data class Started(val identMethodInfo: IdentMethodInfoDTO) : ActionType()
}

private class MethodSelectionItem(
        inflater: LayoutInflater,
        views: PiFragmentMethodSelectionBinding,
        val identMethod: IdentMethodInfoDTO,
        val onAction: (ActionType) -> Unit
) {
    private val emmiReporter = EmmiCoreReporter

    private val item = PiMethodselectionItemBinding.inflate(inflater, views.piMethodSelectionContent, true)
    private val loadingController: MaterialButtonLoadingController

    var isItemExpanded = false

    init {
        val resId = when (identMethod.method) {
            IdentMethodDTO.VIDEO -> R.drawable.pi_ic_method_selection_video
            IdentMethodDTO.PHOTO -> R.drawable.pi_ic_method_selection_photo
            IdentMethodDTO.BASIC -> R.drawable.pi_ic_method_selection_basic
            IdentMethodDTO.EID -> R.drawable.pi_ic_method_selection_eid
            IdentMethodDTO.AUTOID -> R.drawable.pi_ic_method_selection_autoid
            IdentMethodDTO.UNKNOWN -> R.drawable.pi_ic_method_selection_video // should never happen
        }

        item.icon.setImageResource(resId)
        item.title.text = identMethod.title
        item.description.text = identMethod.description
        item.expandIcon.contentDescription = identMethod.method.name //for test automation

        item.identMethodItem.setOnClickListener {
            onAction(ActionType.Expanded(this))
        }

        setExpanded(identMethod.expanded)

        item.btnStartIdentMethod.contentDescription = identMethod.continueButton.text //for test automation
        item.btnStartIdentMethod.text = identMethod.continueButton.text
        item.btnStartIdentMethod.setOnClickListener {
            onAction(ActionType.Started(identMethod))
        }
        item.btnStartIdentMethod.id = when (identMethod.method) { //for test automation
            IdentMethodDTO.VIDEO -> R.id.btn_start_ident_method_video
            IdentMethodDTO.PHOTO -> R.id.btn_start_ident_method_photo
            IdentMethodDTO.BASIC -> R.id.btn_start_ident_method_basic
            IdentMethodDTO.EID -> R.id.btn_start_ident_method_eid
            IdentMethodDTO.AUTOID -> R.id.btn_start_ident_method_autoid
            IdentMethodDTO.UNKNOWN -> R.id.btn_start_ident_method_video // should never happen
        }

        updateIdentMethod(identMethod)
        loadingController = MaterialButtonLoadingController(inflater.context, item.btnStartIdentMethod)
    }

    fun setExpanded(expanded: Boolean) {
        isItemExpanded = expanded
        item.identMethodDetails.isVisible = expanded
        item.expandIcon.animate().rotation(if (expanded) 180f else 0f)
    }

    fun setButtonEnabled(enabled: Boolean) {
        item.btnStartIdentMethod.isEnabled = identMethod.enabled && enabled
        loadingController.loadingAnimation(enabled.not())
    }

    fun updateIdentMethod(identMethod: IdentMethodInfoDTO) {
        item.btnStartIdentMethod.isEnabled = identMethod.enabled
        if (identMethod.enabled.not() && identMethod.disabledInfo.isNullOrEmpty().not()) {
            item.infoLayout.visibility = View.VISIBLE
            item.disabledInfo.visibility = View.VISIBLE
            item.disabledInfo.text = identMethod.disabledInfo
        }

        if (identMethod.footer.isNullOrEmpty().not() && identMethod.enabled) {
            item.infoLayout.visibility = View.VISIBLE
            item.footer.text = identMethod.footer
        } else {
            item.footer.visibility = View.GONE
            item.disabledInfo.setPadding(0)
        }

        if (identMethod.faqLink?.type == InteractionTypeDTO.LINK) {
            item.faq.text = identMethod.faqLink.text
            item.faq.visibility = View.VISIBLE
            item.faq.setOnClickListener {
                identMethod.faqLink.target?.let { onAction(ActionType.Faq(it)) }
                val eventContext = mapOf(
                    "identificationMethod" to identMethod.method.name,
                    "context" to "methodSelection")
                emmiReporter.send(logEvent = LogEvent.PD_FAQ, eventContext = eventContext)
            }
        }
    }
}