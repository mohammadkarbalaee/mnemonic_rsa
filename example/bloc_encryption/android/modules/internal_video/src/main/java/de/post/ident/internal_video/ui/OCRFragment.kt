package de.post.ident.internal_video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.JsonClass
import de.post.ident.internal_core.camera.CameraView
import de.post.ident.internal_core.camera.CapturedPreviewView
import de.post.ident.internal_core.camera.DocumentType
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.rest.OcrErrorDto
import de.post.ident.internal_core.rest.OcrResponseError
import de.post.ident.internal_core.rest.SelfServicePhotoDto
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.reporting.LogLevel
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.ui.CallbackUrlHandling
import de.post.ident.internal_video.*
import de.post.ident.internal_video.databinding.PiFragmentOcrBinding
import de.post.ident.internal_video.databinding.PiFragmentOcrResultBinding
import de.post.ident.internal_core.camera.CameraWrapper
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class UploadView(private val binding: PiFragmentOcrResultBinding) {
    init {
        showLoading(true)
    }

    fun showError(error: OcrErrorDto, abortButtonClicked: () -> Unit) {
        binding.ocrResultFailure.visibility = View.VISIBLE
        binding.ocrFailureTitle.text = error.title
        binding.ocrFailureDescription.text = error.description
        binding.ocrFailureHint.text = error.hint
        val imageRes = if (error.errorCode == OcrErrorDto.ErrorCode.EXPIRED) R.drawable.pi_id_card_expired else R.drawable.pi_id_card_error
        binding.ocrFailureImage.setImageResource(imageRes)
        binding.ocrResultFailureButton.text = error.continueButton.text
        binding.ocrResultFailureButton.setOnClickListener {
            abortButtonClicked()
        }
        showLoading(false)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.ocrResultSpinner.isVisible = isLoading
        binding.ocrLoadingDescription.text = LocalizedStrings.getString("ocr_result_checking")
    }
}

@JsonClass(generateAdapter = true)
data class Parameter(val sspList: List<SelfServicePhotoDto>, val uploadPath: String)

class OCRFragment : BaseVideoFragment() {
    @Keep
    private enum class Screen {
        CAMERA, PREVIEW, UPLOAD
    }

    companion object {
        private val photoListParameter: BundleParameter<Parameter> = BundleParameter.moshi(CoreEmmiService.moshi, "PLP")
        fun newInstance(photoList: List<SelfServicePhotoDto>, uploadPath: String) = OCRFragment().withParameter(Parameter(photoList, uploadPath), photoListParameter)
    }

    private lateinit var viewBinding: PiFragmentOcrBinding

    private var lastImageData: CameraWrapper.ResultImage? = null

    private val savedImages = mutableListOf<CoreEmmiService.FileUploadItem>()

    private lateinit var parameter: Parameter

    private lateinit var cameraView: CameraView
    private lateinit var previewView: CapturedPreviewView
    private lateinit var uploadView: UploadView

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        emmiReporter.send(LogEvent.CR_PRERECORD_PHOTO, logLevel = LogLevel.INFO);
        parameter = checkNotNull(photoListParameter.getParameter(arguments))
        viewBinding = PiFragmentOcrBinding.inflate(inflater, container, false)
        val camera = CameraWrapper(this, viewBinding.camera.piCameraViewFinder)
        emmiReporter.send(LogEvent.CR_OCR_PHOTO, logLevel = LogLevel.INFO)
        cameraView = CameraView(viewBinding.camera) { action ->
            when (action) {
                CameraView.BtnAction.TAKE -> {
                    lifecycleScope.launch {
                        updateUI(Screen.PREVIEW)
                        previewView.showProgress(true)
                        try {
                            val image = camera.takePictureAndResize()
                            emmiReporter.send(LogEvent.CR_OCR_PHOTO_CREATION, logLevel = LogLevel.INFO, eventStatus = EventStatus.SUCCESS)
                            previewView.showImage(image.bitmap)
                            lastImageData = image
                            emmiReporter.send(LogEvent.CR_OCR_CONFIRM)
                        } catch (err: Throwable) {
                            emmiReporter.send(LogEvent.CR_OCR_PHOTO_CREATION, logLevel = LogLevel.ERROR, eventStatus = EventStatus.ERROR, message = err.message)
                            ensureActive()
                            updateUI(Screen.CAMERA)
                        }
                    }
                }
                CameraView.BtnAction.SWITCH -> {
                    camera.switchCamera()
                }
                CameraView.BtnAction.NEXT -> {
                    viewBinding.camera.tabLayout.getTabAt(viewBinding.camera.tabLayout.selectedTabPosition + 1)?.select()
                    updateUI(Screen.CAMERA)

                }
                CameraView.BtnAction.PREVIOUS -> {
                    viewBinding.camera.tabLayout.getTabAt(viewBinding.camera.tabLayout.selectedTabPosition - 1)?.select()
                    updateUI(Screen.CAMERA)
                }
                else -> { /* ignore */ }
            }
        }
        previewView = CapturedPreviewView(viewBinding.preview) { action ->
            when (action) {
                CapturedPreviewView.BtnAction.RETRY -> {
                    updateUI(Screen.CAMERA)
                }
                CapturedPreviewView.BtnAction.USE -> {
                    lastImageData?.let { image ->
                        val fileName = parameter.sspList[savedImages.size].uploadName
                        savedImages.add(CoreEmmiService.FileUploadItem(
                            fileName = fileName,
                            jpegArrayData = image.jpegDataArray
                        ))
                    }
                    if (savedImages.size < parameter.sspList.size) {
                        updateUI(Screen.CAMERA)
                    } else {
                        updateUI(Screen.UPLOAD)
                        uploadPhotos()
                    }
                }
            }
        }

        uploadView = UploadView(viewBinding.upload)
        updateUI(Screen.CAMERA)
        return viewBinding.root
    }

    private fun uploadPhotos() {
        lifecycleScope.launch {
            try {
                emmiReporter.send(LogEvent.CR_OCR_CHECK)
                videoManager.uploadSelfServicePhotos(parameter.uploadPath, savedImages)
                emmiReporter.send(LogEvent.CR_OCR_SUCCESS)
                emmiReporter.send(LogEvent.CR_OCR_CHECK_RESULT, logLevel = LogLevel.INFO, eventStatus = EventStatus.SUCCESS)
                finished()
            } catch (err: Throwable) {
                emmiReporter.send(LogEvent.CR_OCR_CHECK_RESULT, logLevel = LogLevel.WARN, eventStatus = EventStatus.ERROR)
                ensureActive()
                when (err) {
                    is OcrResponseError -> {
                        val event = when (err.error.errorCode) {
                            OcrErrorDto.ErrorCode.EXPIRED -> LogEvent.CR_OCR_EXPIRED
                            OcrErrorDto.ErrorCode.UNSUPPORTED -> LogEvent.CR_OCR_UNSUPPORTED
                            OcrErrorDto.ErrorCode.GENERAL, OcrErrorDto.ErrorCode.UNKNOWN -> LogEvent.CR_OCR_GENERAL_ERROR
                        }
                        emmiReporter.send(event, logLevel = LogLevel.ERROR, eventStatus = EventStatus.ERROR)
                        uploadView.showError(err.error) { requireActivity().finish() }
                    }
                    else -> {
                        finished()
                    }
                }
            }
        }
    }

    private fun finished() {
        (requireActivity() as VideoIdentActivity).ocrFragmentFinished()
    }

    private fun updateUI(screen: Screen) {
        viewBinding.camera.root.isVisible = screen == Screen.CAMERA
        viewBinding.preview.root.isVisible = screen == Screen.PREVIEW
        viewBinding.upload.root.isVisible = screen == Screen.UPLOAD
        if (savedImages.size < parameter.sspList.size) {
            val currentData = parameter.sspList[savedImages.size]
            val side = when (currentData.type) {
                SelfServicePhotoDto.DocumentSide.UNKNOWN -> CameraView.DocumentSide.OTHER
                SelfServicePhotoDto.DocumentSide.FRONTSIDE -> CameraView.DocumentSide.FRONT
                SelfServicePhotoDto.DocumentSide.BACKSIDE -> CameraView.DocumentSide.BACK
            }
            cameraView.updateViewPhoto(side, DocumentType.IDENTIFICATION)
        }
    }
}
