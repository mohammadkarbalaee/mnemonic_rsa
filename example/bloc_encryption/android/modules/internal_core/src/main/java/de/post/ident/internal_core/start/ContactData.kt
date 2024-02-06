package de.post.ident.internal_core.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.databinding.PiFragmentContactDataBinding
import de.post.ident.internal_core.rest.ContactDataDTO
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.DataFieldDTO
import de.post.ident.internal_core.rest.GeneralError
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class ContactDataFragment : Fragment() {
    companion object {

        private val CONTACT_DATA_PARAMETER: BundleParameter<ContactDataDTO> = BundleParameter.moshi(CoreEmmiService.moshi, "CONTACT_DATA")

        fun newInstance(contactData: ContactDataDTO): ContactDataFragment {
            val fragment = ContactDataFragment()
            val bundle = Bundle()
            CONTACT_DATA_PARAMETER.putParameter(bundle, contactData)
            fragment.arguments = bundle
            return fragment
        }
    }

    private val emmiReporter = EmmiCoreReporter
    private val emmiService = CoreEmmiService

    private lateinit var viewBinding: PiFragmentContactDataBinding

    private lateinit var submitButtonController: MaterialButtonLoadingController

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentContactDataBinding.inflate(inflater, container, false)
        initView(viewBinding)
        return viewBinding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        emmiReporter.send(LogEvent.DC_CONTACT_DATA)
    }

    private fun initView(views: PiFragmentContactDataBinding) {
        val data = checkNotNull(CONTACT_DATA_PARAMETER.getParameter(arguments))

        log("Data: $data")
        val formElements = mutableListOf<FormField>()

        views.textViewContactDataTitle.text = LocalizedStrings.getString("contact_data_title")
        views.buttonConfirmContactData.text = LocalizedStrings.getString("contact_data_button")

        val onActionDone = {
            validate(formElements)
        }
        data.contactDataList
                .sortedBy { it.sortKey }
                .forEach { dataField ->
                    log("${dataField.key} type: ${dataField.type}")
                    when (dataField.type) {
                        DataFieldDTO.Type.TEXT,
                        DataFieldDTO.Type.EMAIL,
                        DataFieldDTO.Type.PHONE -> {
                            formElements.add(FormTextField(layoutInflater, views.contactDataLayout, dataField, actionDone = onActionDone))
                        }
                        DataFieldDTO.Type.CHOICE -> {
                            formElements.add(FormRadioField(layoutInflater, views.contactDataLayout, dataField))
                        }
                        else -> log(Throwable("unknown data field type (skipping...)!"))
                    }
                }

        views.buttonConfirmContactData.setOnClickListener {
            validate(formElements)
        }
        submitButtonController = MaterialButtonLoadingController(requireContext(), views.buttonConfirmContactData)
    }

    private fun validate(formElements: List<FormField>) {
        var validated = true
        formElements.forEach { field ->
            if (field.validate().not()) {
                validated = false
            }
        }
        if (validated) {
            sendContactData(formElements)
        }
    }

    private fun sendContactData(formElements: List<FormField>) {
        lifecycleScope.launch {

            val data = mutableMapOf<String, String?>()

            formElements.forEach { field ->
                data[field.data.key] = field.userInput()
            }
            log("User data: $data")
            try {
                viewBinding.buttonConfirmContactData.isEnabled = false
                submitButtonController.loadingAnimation(true)
                emmiReporter.send(LogEvent.DC_SEND_CONTACT_DATA)
                emmiService.sendContactData(data, requireNotNull(Commons.caseId))
                finished()
            } catch (err: GeneralError) {
                showAlertDialog(context, err.userMessage)
            } finally {
                ensureActive()
                viewBinding.buttonConfirmContactData.isEnabled = true
                submitButtonController.loadingAnimation(false)
            }
        }
    }
}
