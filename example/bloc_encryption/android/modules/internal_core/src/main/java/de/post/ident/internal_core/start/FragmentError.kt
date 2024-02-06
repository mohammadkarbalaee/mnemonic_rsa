package de.post.ident.internal_core.start

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.R
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.databinding.PiFragmentErrorMessageBinding
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showBackButton

class FragmentError : DialogFragment() {
    companion object {

        private val ERROR_PARAMETER: BundleParameter<ErrorMessageData> = BundleParameter.moshi(CoreEmmiService.moshi, "ERRORS")

        fun newInstance(errorMessageData: ErrorMessageData, onBackClicked: () -> Unit, onContinueClicked: () -> Unit): FragmentError = FragmentError()
                .withParameter(errorMessageData, ERROR_PARAMETER).apply {
                    this.onBackClicked = onBackClicked
                    this.onContinueClicked = onContinueClicked
            }
    }

    @JsonClass(generateAdapter = true)
    data class ErrorMessageData(val title: String, val message: String, val resultCode: SdkResultCodes, val isDeeplink: Boolean, val allowBackToMethodSelection: Boolean)

    private lateinit var viewBinding: PiFragmentErrorMessageBinding
    private lateinit var errorData: ErrorMessageData

    private val emmiReporter = EmmiCoreReporter

    lateinit var onBackClicked: () -> Unit
    lateinit var onContinueClicked: () -> Unit

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
        viewBinding = PiFragmentErrorMessageBinding.inflate(inflater, container, false)
        errorData = checkNotNull(ERROR_PARAMETER.getParameter(arguments))

        showBackButton(viewBinding.root.findViewById(R.id.toolbar_actionbar), true) { dismiss() }

        log("showing error screen: $errorData")

        viewBinding.title.text = errorData.title
        viewBinding.image.isVisible = errorData.allowBackToMethodSelection.not()
        viewBinding.subtitle.text = errorData.message
        viewBinding.buttonBack.text =
                if (errorData.allowBackToMethodSelection) LocalizedStrings.getString("default_btn_back")
                else LocalizedStrings.getString(R.string.default_btn_close)
        viewBinding.buttonBack.setOnClickListener { dismiss() }

        if (errorData.resultCode == SdkResultCodes.ERROR_CASE_DONE) {
            viewBinding.title.setText(LocalizedStrings.getString("identification_successful_title"))
            viewBinding.image.setImageResource(R.drawable.pi_ic_check)
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                140f, //dp
                resources.displayMetrics
            ).toInt()
            viewBinding.image.layoutParams.apply {
                width = px
                height = px
            }
        }

        if (errorData.isDeeplink) {
            if (Commons.caseId != null) showCaseId()
            viewBinding.buttonSecondaryAction.text = LocalizedStrings.getString(R.string.default_btn_continue)
            viewBinding.buttonSecondaryAction.setOnClickListener { onContinueClicked() }
            viewBinding.buttonSecondaryAction.visibility = View.VISIBLE
        }

        if (!CoreConfig.isSdk && errorData.resultCode == SdkResultCodes.ERROR_SSL_PINNING) {
            if (Commons.caseId != null) showCaseId()
            viewBinding.buttonSecondaryAction.text = LocalizedStrings.getString("default_btn_update")
            viewBinding.buttonSecondaryAction.setOnClickListener { onContinueClicked() }
            viewBinding.buttonSecondaryAction.visibility = View.VISIBLE
        }

        val eventContext = mapOf(
                "sdkResultCode" to errorData.resultCode.name,
                "sdkResultCodeId" to errorData.resultCode.id.toString()
        )
        emmiReporter.send(logEvent = LogEvent.SD_ERROR_SCREEN, eventContext = eventContext)

        return viewBinding.root
    }

    private fun showCaseId() {
        viewBinding.caseIdHintContainer.visibility = View.VISIBLE
        viewBinding.caseIdHint.text = LocalizedStrings.getString(R.string.case_id_clipboard)
        viewBinding.caseId.text = Commons.caseId
        viewBinding.caseId.setOnClickListener {
            copyCaseIdToClipboard()
        }
        viewBinding.btnCopy.setOnClickListener {
            copyCaseIdToClipboard()
        }
    }

    private fun copyCaseIdToClipboard() {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("caseID", Commons.caseId))
            Toast.makeText(requireContext(), LocalizedStrings.getString(R.string.case_id_clipboard_success), Toast.LENGTH_SHORT).show()
    }

    override fun onDismiss(dialog: DialogInterface) {
        onBackClicked()
        super.onDismiss(dialog)
    }

}