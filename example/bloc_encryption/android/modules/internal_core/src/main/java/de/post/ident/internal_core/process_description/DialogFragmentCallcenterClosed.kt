package de.post.ident.internal_core.process_description

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.R
import de.post.ident.internal_core.databinding.PiFragmentCallcenterClosedBinding
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.ServiceInfoDTO
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.showBackButton

class DialogFragmentCallcenterClosed : DialogFragment() {
    companion object {

        private val CALLCENTER_CLOSED: BundleParameter<CallcenterClosedData> = BundleParameter.moshi(CoreEmmiService.moshi, "CALLCENTER_CLOSED")

        fun newInstance(viewData: CallcenterClosedData, onDismiss: () -> Unit): DialogFragmentCallcenterClosed = DialogFragmentCallcenterClosed().withParameter(viewData, CALLCENTER_CLOSED).apply { this.onDismissed = onDismiss }
    }

    @JsonClass(generateAdapter = true)
    data class CallcenterClosedData(
            val backButtonText: String?,
            val serviceCenterInfo: ServiceInfoDTO
    )

    private lateinit var viewBinding: PiFragmentCallcenterClosedBinding
    private var onDismissed: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NORMAL, android.R.style.ThemeOverlay_Material_ActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                dismiss()
            }
        }

        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentCallcenterClosedBinding.inflate(inflater, container, false)
        val viewData = checkNotNull(CALLCENTER_CLOSED.getParameter(arguments))

        showBackButton(viewBinding.root.findViewById(R.id.toolbar_actionbar), true) { dismiss() }

        if (viewData.serviceCenterInfo.isInBusinessTime.not()) {
            viewBinding.closedTitle.text = LocalizedStrings.getString("callcenter_closed_title")
            viewBinding.closedDescription.text = LocalizedStrings.getHtmlString("callcenter_closed_description")
            viewBinding.closedBusinessTime.text = viewData.serviceCenterInfo.businessTimeText
        } else if (viewData.serviceCenterInfo.status.not()) {
            viewBinding.closedTitle.text = ""
            viewBinding.closedDescription.text = LocalizedStrings.getHtmlString("dialog_no_agent_available")
            viewBinding.closedBusinessTime.text = ""
        }

        viewData.backButtonText?.let {
            viewBinding.closedBtnBack.text = it
            viewBinding.closedBtnBack.isVisible = Commons.skipMethodSelection.not()
            viewBinding.closedBtnBack.setOnClickListener { dismiss() }
        }

        EmmiCoreReporter.send(LogEvent.IH_SERVICECENTER_CLOSED)

        return viewBinding.root
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDismissed?.invoke()
        super.onDismiss(dialog)
    }
}