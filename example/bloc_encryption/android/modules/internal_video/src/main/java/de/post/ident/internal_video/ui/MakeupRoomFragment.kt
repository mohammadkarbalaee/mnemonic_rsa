package de.post.ident.internal_video.ui

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.process_description.DialogFragmentCallcenterClosed
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.AgentLanguageDTO
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.getUserMessage
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.ConnectionTypeDTO
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.getLatency
import de.post.ident.internal_core.util.getMobileDataSignalStrength
import de.post.ident.internal_core.util.getNetworkConnectionType
import de.post.ident.internal_core.util.getmobileDataNetworkType
import de.post.ident.internal_core.util.getWifiSignalStrength
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.showBackButton
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import de.post.ident.internal_video.R
import de.post.ident.internal_video.databinding.PiAgentLanguageViewBinding
import de.post.ident.internal_video.databinding.PiFragmentAgentLanguagesBinding
import de.post.ident.internal_video.databinding.PiFragmentMakeupRoomBinding
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class MakeupRoomFragment : BaseVideoFragment() {
    companion object {
        fun newInstance(): MakeupRoomFragment = MakeupRoomFragment()
        private const val LATENCY_DOMAIN = "deutschepost.de"
    }

    private val emmiService = CoreEmmiService

    private var languageList: List<AgentLanguageDTO> = emptyList()
    private var selectedLanguageCode = ""
    private var isLoading = false
    private var eventContext = mapOf<String, String>()

    private lateinit var viewBinding: PiFragmentMakeupRoomBinding
    private lateinit var continueButtonController: MaterialButtonLoadingController

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentMakeupRoomBinding.inflate(inflater, container, false)

        viewBinding.languageContainer.setOnClickListener { onLanguageSelectionClicked() }
        viewBinding.languageLabel.text = LocalizedStrings.getString("language_for_videochat")
        viewBinding.buttonStartCall.text = LocalizedStrings.getString("start_videochat")
        viewBinding.buttonStartCall.setOnClickListener { onStartCallClicked() }
        continueButtonController = MaterialButtonLoadingController(requireContext(), viewBinding.buttonStartCall)

        updateUi()
        checkServiceInfo()
        parseLegalText()
        eventContext = checkUserConnection()

        return viewBinding.root
    }

    private fun checkServiceInfo() {
        lifecycleScope.launch {
            try {
                isLoading(true)

                val serviceCenterInfo = emmiService.getServiceCenterInfo(videoManager.caseResponse.callcenterCategory, Commons.caseId)

                languageList = serviceCenterInfo.agentLanguageList
                selectedLanguageCode = languageList[0].languageCode

                val eventContext = mapOf(
                        "isInBusinessTime" to serviceCenterInfo.isInBusinessTime.toString(),
                        "status" to serviceCenterInfo.status.toString(),
                        "serviceCenterCategory" to videoManager.caseResponse.callcenterCategory.toString())
                emmiReporter.send(logEvent = LogEvent.MR_SERVICECENTER_CHECK_RESULT, eventContext = eventContext)

                if (serviceCenterInfo.isInBusinessTime.not() || serviceCenterInfo.status.not()) {
                    DialogFragmentCallcenterClosed.newInstance(DialogFragmentCallcenterClosed.CallcenterClosedData(videoManager.caseResponse.toMethodSelection?.text, serviceCenterInfo)) {
                        viewBinding.buttonStartCall.isEnabled = false
                        activity?.finish()
                    }.show(childFragmentManager, "CALLCENTER_CLOSED")
                }
            } catch (err: Throwable) {
                showAlertDialog(activity, err.getUserMessage()) {
                    viewBinding.buttonStartCall.isEnabled = false
                }
            } finally {
                ensureActive()
                isLoading(false)
            }
        }
    }

    private fun onStartCallClicked() {
        if (viewBinding.buttonStartCall.isEnabled.not()) {
            return
        }
        enableStartButton(false)

        emmiReporter.send(LogEvent.VC_CONFIRM_CHAT, eventContext = eventContext)

        lifecycleScope.launch {
            try {
                videoManager.prepareCall(selectedLanguageCode)
            } catch (err: Throwable) {
                showAlertDialog(activity, err.getUserMessage())
            } finally {
                ensureActive()
                isLoading(false)
            }
        }
    }

    private fun onLanguageSelectionClicked() {
        if (languageList.isEmpty().not()) {
            emmiReporter.send(LogEvent.VC_LANGUAGE_SELECTION)
            DialogFragmentAgentLanguages.newInstance(DialogFragmentAgentLanguages.AgentLanguageData(
                    languageList, selectedLanguageCode)
            ) { onLanguageSelected(it) }.show(childFragmentManager, "AGENT_LANGUAGES")
        }
    }

    private fun onLanguageSelected(selectedLanguage: String?) {
        selectedLanguage?.let { selectedLanguageCode = it }
        updateUi()
    }

    private fun isLoading(loading: Boolean) {
        isLoading = loading
        updateUi()
    }

    private fun checkUserConnection(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )

            if (permissions.all { permission ->
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        permission
                    ) == PackageManager.PERMISSION_GRANTED
                }) {

                try {
                    val context = requireContext()
                    val conType = getNetworkConnectionType(context)
                    log("ConnectionType: $conType")

                    runBlocking {
                        map["latency"] = getLatency(LATENCY_DOMAIN).toString()
                    }

                    map["connectionType"] = "${conType.name}"
                    when (conType) {
                        ConnectionTypeDTO.ConnectionType.CELLULAR -> {
                            map["mobileNetworkType"] = getmobileDataNetworkType(context).name
                            getMobileDataSignalStrength(context) { strength ->
                                map["mobileDataStrength"] = strength.toString()
                            }
                        }

                        ConnectionTypeDTO.ConnectionType.WIFI -> {
                            map.putAll(getWifiSignalStrength(context))
                        }

                        else -> {}
                    }
                } catch (e: Throwable) {
                    map["errorConnectionCheck"] = "${e.message}"
                }
            }
        }
        return map
    }

    private fun updateUi() {
        viewBinding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        viewBinding.languageContainer.visibility = if (isLoading) View.GONE else View.VISIBLE

        if (languageList.isNotEmpty()) {
            viewBinding.languageText.text = languageList.find {
                it.languageCode.equals(selectedLanguageCode, true)
            }?.languageName ?: languageList.first().languageName
        }

        viewBinding.hintTextPhotos.text = LocalizedStrings.getString("hint_photos_regulatory_reasons")

        enableStartButton(isLoading.not())
    }

    private fun parseLegalText() {
        videoManager.caseResponse.modules.makeupRoom?.legalNote?.let {
            val spannable = SpannableString(it)

            val privacyKeyword = videoManager.caseResponse.modules.makeupRoom?.legalTerms?.text
            val privacyLink = videoManager.caseResponse.modules.makeupRoom?.legalTerms?.target

            if (privacyKeyword != null && privacyLink != null) {
                setSpan(spannable, findStringBounds(it, privacyKeyword), getClickableSpan {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(privacyLink)
                    startActivity(intent)
                })
            }

            val recordKeyword = videoManager.caseResponse.modules.makeupRoom?.videoRecord?.text
            val recordText = videoManager.caseResponse.modules.makeupRoom?.videoRecordTerms?.text

            if (recordKeyword != null && recordText != null) {
                setSpan(spannable, findStringBounds(it, recordKeyword), getClickableSpan {
                    showAlertDialog(requireContext(), recordText)
                })
            }

            viewBinding.textviewConsentDeclaration.text = spannable
            viewBinding.textviewConsentDeclaration.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun getClickableSpan(onClick: () -> Unit): ClickableSpan {
        return object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }

            override fun updateDrawState(text: TextPaint) {
                super.updateDrawState(text)
                text.isUnderlineText = false
            }
        }
    }

    private fun setSpan(spannable: SpannableString, bounds: Pair<Int, Int>?, clickableSpan: ClickableSpan) {
        bounds?.let {
            spannable.setSpan(clickableSpan, it.first, it.second, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            spannable.setSpan(TextAppearanceSpan(requireContext(), R.style.PITextAppearance_LegalLinks), it.first, it.second, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private fun findStringBounds(text: String?, keyword: String?): Pair<Int, Int>? {
        if (text != null && keyword != null) {
            val start = text.indexOf(keyword)
            return if (start >= 0) Pair(start, start + keyword.length) else null
        }
        return null
    }

    private fun enableStartButton(enabled: Boolean) {
        viewBinding.buttonStartCall.isEnabled = enabled
        viewBinding.buttonStartCall.isClickable = enabled
        continueButtonController.loadingAnimation(enabled.not())
    }
}

class DialogFragmentAgentLanguages : DialogFragment() {
    companion object {

        private val AGENT_LANGUAGE_PARAMETER: BundleParameter<AgentLanguageData> = BundleParameter.moshi(CoreEmmiService.moshi, "AGENT_LANGUAGE")

        fun newInstance(languageList: AgentLanguageData, onLanguageSelected: (String?) -> Unit): DialogFragmentAgentLanguages = DialogFragmentAgentLanguages()
                .withParameter(languageList, AGENT_LANGUAGE_PARAMETER).apply { this.onLanguageSelected = onLanguageSelected }
    }

    @JsonClass(generateAdapter = true)
    data class AgentLanguageData(
            val list: List<AgentLanguageDTO>,
            val preselectedLanguageIso: String
    )

    private lateinit var viewBinding: PiFragmentAgentLanguagesBinding
    private var onLanguageSelected: ((String?) -> Unit)? = null

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
        viewBinding = PiFragmentAgentLanguagesBinding.inflate(inflater, container, false)
        val languages = checkNotNull(AGENT_LANGUAGE_PARAMETER.getParameter(arguments))
        showBackButton(viewBinding.root.findViewById(R.id.toolbar_actionbar), true) { dismiss() }

        viewBinding.languageListFooter.text = LocalizedStrings.getString("language_list_info")

        viewBinding.languageList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = LanguageListAdapter(languages.list, languages.preselectedLanguageIso) {
                onLanguageSelected?.invoke(it)
                dismiss()
            }
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        return viewBinding.root
    }

    class LanguageListAdapter(
            private val list: List<AgentLanguageDTO>,
            private val preselectedLanguageIso: String,
            private val onLanguageSelected: ((String?) -> Unit)?
    ) : RecyclerView.Adapter<LanguageListAdapter.ViewHolder>() {
        class ViewHolder(val binding: PiAgentLanguageViewBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(PiAgentLanguageViewBinding.inflate(LayoutInflater.from(parent.context)))
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.language.text = list[position].languageName
            holder.binding.root.setOnClickListener { onLanguageSelected?.invoke(list[position].languageCode) }

            if (list[position].languageCode == preselectedLanguageIso) {
                holder.binding.checkmark.visibility = View.VISIBLE
            } else {
                holder.binding.checkmark.visibility = View.INVISIBLE // don't use GONE due to rtl-layouts
            }
        }

    }
}