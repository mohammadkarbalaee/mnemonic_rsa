package de.post.ident.internal_video.ui

import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.*
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.ui.CallbackUrlHandling
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import de.post.ident.internal_core.util.ui.showKeyboard
import de.post.ident.internal_video.R
import de.post.ident.internal_video.databinding.PiFragmentUserSelfAssessmentBinding
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OCRUSAFragment : BaseVideoFragment() {
    companion object {
        const val ATTEMPT_ID_PLACEHOLDER = "{ATTEMPT_ID}"

        private val MODULE_PARAMETER: BundleParameter<UserSelfAssessmentModuleDTO> = BundleParameter.moshi(CoreEmmiService.moshi, "MODULE_DATA")
        fun newInstance(usaModule: UserSelfAssessmentModuleDTO) = OCRUSAFragment().withParameter(usaModule, MODULE_PARAMETER)
    }

    sealed class State {
        object Loading: State()
        data class FieldResult(val fieldList: List<DataFieldDTO>): State()
        data class FieldError(val errorList: List<DataFieldErrorDTO>): State()
        data class OcrError(val error: OcrErrorDto): State()
    }

    private val emmiService = CoreEmmiService

    private lateinit var viewBinding: PiFragmentUserSelfAssessmentBinding

    private lateinit var submitButtonController: MaterialButtonLoadingController

    private lateinit var uploadView: UploadView

    private lateinit var getFieldsUrl: String
    private lateinit var createFieldsUrl: String
    private lateinit var documentDataUrl: String

    private var formElementList: List<FormField> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentUserSelfAssessmentBinding.inflate(inflater, container, false)
        val data = checkNotNull(MODULE_PARAMETER.getParameter(arguments))
        getFieldsUrl = data.userSelfAssessmentGet.target.replace(ATTEMPT_ID_PLACEHOLDER, checkNotNull(videoManager.attemptId))
        createFieldsUrl = data.userSelfAssessmentCreate.target.replace(ATTEMPT_ID_PLACEHOLDER, checkNotNull(videoManager.attemptId))
        documentDataUrl = data.userSelfAssessmentDocumentData.target.replace(ATTEMPT_ID_PLACEHOLDER, checkNotNull(videoManager.attemptId))

        viewBinding.btValidateInput.text = LocalizedStrings.getString("usa_input_button_label")
        viewBinding.tvHeaderText.text = data.title
        submitButtonController = MaterialButtonLoadingController(requireContext(), viewBinding.btValidateInput)
        loadFields()

        uploadView = UploadView(viewBinding.ocrResultView)

        return viewBinding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        emmiReporter.send(LogEvent.CR_USER_SELF_ASSESSMENT)
    }

    private fun updateUi(state: State) {
        with(viewBinding) {
            ocrUsaLoadingSpinner.isVisible = false
            ocrUsaInputLayout.isVisible = false
            ocrResultView.root.isVisible = false
            imageHighlightViewContainer.isVisible = false

            when (state) {
                State.Loading -> ocrUsaLoadingSpinner.isVisible = true
                is State.FieldResult -> {
                    ocrUsaInputLayout.isVisible = true
                    updateFields(state.fieldList)
                    updateDocument("")
                }
                is State.FieldError -> {
                    ocrUsaInputLayout.isVisible = true
                    showServerErrors(state.errorList)
                }
                is State.OcrError -> {
                    ocrResultView.root.isVisible = true
                    showKeyboard(requireActivity(), false)
                    uploadView.showError(state.error) {
                        val started = CallbackUrlHandling(requireActivity(), state.error.callbackUrl).startActivity()
                        if (started.not()) requireActivity().finish()
                    }
                }
            }
        }
    }

    private fun updateFields(fieldList: List<DataFieldDTO>) {
        log("Data: $fieldList")
        val formElements = mutableListOf<FormField>()

        val onActionDone = {
            validate()
        }

        viewBinding.btValidateInput.setOnClickListener {
            validate()
        }

        val focusChanged: (field: FormField, hasFocus: Boolean) -> Unit = { field, hasFocus ->
            if (hasFocus) updateDocument(field.data.key)
        }

        fieldList.sortedBy { it.sortKey }
                .forEach { dataField ->
                    log("${dataField.key} type: ${dataField.type}")
                    when (dataField.type) {
                        DataFieldDTO.Type.TEXT,
                        DataFieldDTO.Type.EMAIL,
                        DataFieldDTO.Type.PHONE,
                        DataFieldDTO.Type.POSTAL_CODE,
                        DataFieldDTO.Type.ID_CARD -> {
                            formElements.add(FormTextField(layoutInflater, viewBinding.dataFieldsContainer, dataField,
                                    actionDone = onActionDone, onFocusChanged = focusChanged))
                        }
                        DataFieldDTO.Type.CHOICE -> {
                            formElements.add(FormDropdownField(layoutInflater, viewBinding.dataFieldsContainer, dataField, focusChanged))
                        }
                        DataFieldDTO.Type.DATE -> {
                            formElements.add(FormDateField(this, layoutInflater, viewBinding.dataFieldsContainer, dataField, focusChanged))
                        }
                        else -> log(Throwable("unknown data field type (skipping...)!"))
                    }
                }
        formElementList = formElements
    }

    private fun validate() {
        var validated = true
        var firstError: FormField? = null

        formElementList.forEach { field ->
            if (field.validate().not()) {
                validated = false
                if (firstError == null) firstError = field
            }
        }

        if (validated) {
            sendData(formElementList)
        } else {
            firstError?.binding?.root?.requestFocus()
        }
    }

    private fun showServerErrors(errorList: List<DataFieldErrorDTO>) {
        val errorMap = errorList.associateBy { it.parameterName }
        var firstError = true
        formElementList.forEach { field ->
            val errorText = errorMap[field.data.key]?.errorText
            if (firstError && errorText != null) {
                firstError = false
                field.binding.root.requestFocus()
            }
            field.error(errorText)
        }
    }

    private fun highlightDocument(documentData: DocumentDataDto?, fieldKey: String) {
        val field = documentData?.fields?.find { it.key == fieldKey }
        if (field != null) {
            val front = field.side != FieldInfoDto.SideDTO.BACK_SIDE
            val bytes = Base64.decode(if (front) documentData.imageFrontSide else documentData.imageBackSide, Base64.DEFAULT)
            val tmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val bitmap = tmp.copy(tmp.config, true)
            tmp.recycle()
            val canvas = Canvas(bitmap)
            log("   $field")
            val x = (field.x * bitmap.width).roundToInt()
            val y = (field.y * bitmap.height).roundToInt()
            val width = (field.width * bitmap.width).roundToInt()
            val height = (field.height * bitmap.height).roundToInt()
            val rect = Rect(x, y, x + width, y + height)
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                color = ContextCompat.getColor(requireContext(), R.color.pi_white)
                strokeWidth = 5f
            }
            with(canvas) {
                if (Build.VERSION.SDK_INT >= 26) {
                    save()
                    clipOutRect(rect)
                    drawColor(ContextCompat.getColor(requireContext(), R.color.pi_usa_highlight_background))
                    restore()
                }
                drawRect(rect, paint)
            }

            viewBinding.imageHighlightView.setImageBitmap(bitmap)
        }
        viewBinding.imageHighlightViewContainer.isVisible = field != null
    }

    data class CachedDocumentData(val country: String?, val type: String?, val data: DocumentDataDto)

    private var cachedDocumentData: CachedDocumentData? = null

    private fun updateDocument(fieldKey: String) {
        lifecycleScope.launch {
            try {
                val country = formElementList.find { it.data.key == "documentCountry" }?.userInput()
                val type = formElementList.find { it.data.key == "documentType" }?.userInput()

                val cache = cachedDocumentData
                val documentData = if (cache != null && cache.country == country && cache.type == type) {
                    cache.data
                } else {
                    val data = emmiService.getDocumentData(documentDataUrl, country, type)
                    cachedDocumentData = CachedDocumentData(country, type, data)
                    data
                }
                highlightDocument(documentData, fieldKey)
            } catch (err: Throwable) {
                log(err)
                cachedDocumentData = null
                ensureActive()
                highlightDocument(null, "")
            }
        }
    }

    private fun loadFields() {
        lifecycleScope.launch {
            try {
                updateUi(State.Loading)
                // Docs/2019-06_OCR_USA_Flow.png ID#08
                val fieldList = emmiService.getUsaFields(getFieldsUrl)
                updateUi(State.FieldResult(fieldList))
                // Docs/2019-06_OCR_USA_Flow.png ID#09
                emmiReporter.send(LogEvent.CR_USA_MODULES_CHECK_RESULT, eventStatus = EventStatus.SUCCESS)
            } catch (err: EmptyBodyException) {
                log(err)
                emmiReporter.send(LogEvent.CR_USA_MODULES_CHECK_RESULT, eventStatus = EventStatus.ERROR, message = "no data requested")
                // Docs/2019-06_OCR_USA_Flow.png ID#10
                ensureActive()
                finished()
            } catch (err: Throwable) {
                log(err)
                emmiReporter.send(LogEvent.CR_USA_MODULES_CHECK_RESULT, eventStatus = EventStatus.ERROR, message = err.message)
                ensureActive()
                showAlertDialog(requireActivity(), err.getUserMessage()) {
                    finished()
                }
            }
        }
    }

    private fun sendData(formElements: List<FormField>) {
        // Docs/2019-06_OCR_USA_Flow.png ID#11

        fun reportCheckResult(err: Throwable?) {
            emmiReporter.send(LogEvent.CR_USA_CHECK_RESULT, eventStatus = if (err == null) EventStatus.SUCCESS else EventStatus.ERROR, message = err?.message)
        }
        lifecycleScope.launch {

            val data = mutableMapOf<String, String?>()

            formElements.forEach { field ->
                data[field.data.key] = field.userInput()
            }
            log("User data: $data")
            try {
                submitButtonController.loadingAnimation(true)
                viewBinding.btValidateInput.isEnabled = false
                emmiReporter.send(LogEvent.CR_USA_SEND_DATA)
                emmiService.sendUsaFields(createFieldsUrl, data)
                // Docs/2019-06_OCR_USA_Flow.png ID#12
                reportCheckResult(null)
                finished()
            } catch (err: FieldResponseError) {
                updateUi(State.FieldError(err.fieldErrorList))
                reportCheckResult(err)
            } catch (err: OcrResponseError) {
                reportCheckResult(err)
                updateUi(State.OcrError(err.error))
            } catch (err: ServerNotAvailableError) {
                reportCheckResult(err)
                ensureActive()
                finished()
            } catch (err: Throwable) {
                reportCheckResult(err)
                ensureActive()
                showAlertDialog(context, err.getUserMessage()) {
                    finished()
                }
            } finally {
                ensureActive()
                viewBinding.btValidateInput.isEnabled = true
                submitButtonController.loadingAnimation(false)
            }
        }
    }

    private fun finished() {
        (requireActivity() as VideoIdentActivity).usaFragmentFinished()
    }
}
