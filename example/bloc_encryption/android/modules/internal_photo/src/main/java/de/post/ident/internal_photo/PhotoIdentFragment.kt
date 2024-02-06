package de.post.ident.internal_photo

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.camera.CameraView
import de.post.ident.internal_core.camera.CameraWrapper
import de.post.ident.internal_core.camera.CapturedPreviewView
import de.post.ident.internal_core.camera.DocumentType
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import de.post.ident.internal_photo.databinding.PiFragmentPhotoBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PhotoIdentFragment : Fragment() {

    companion object {

        private val PHOTO_IDENT_PARAMETER: BundleParameter<CaseResponseDTO> = BundleParameter.moshi(CoreEmmiService.moshi, "PHOTO_IDENT")

        fun newInstance(caseResponseDTO: CaseResponseDTO): PhotoIdentFragment = PhotoIdentFragment()
            .withParameter(caseResponseDTO, PHOTO_IDENT_PARAMETER)
    }

    private val currentUploadStatus = MutableLiveData<UploadStatus>()
    private val HTTP_PRECONDITION_REQUIRED = 428
    private var uploadSize: Double? = 0.0

    private lateinit var viewBinding: PiFragmentPhotoBinding
    private lateinit var cameraView: CameraView
    private lateinit var previewView: CapturedPreviewView
    private lateinit var uploadUi: UploadUi
    private lateinit var uploadStatusView: UploadStatusView
    private lateinit var camera: CameraWrapper
    private lateinit var submitButtonController: MaterialButtonLoadingController

    private var lastImageData: CameraWrapper.ResultImage? = null
    private val emmiReporter = EmmiCoreReporter

    private val caseResponse by lazy { checkNotNull(PHOTO_IDENT_PARAMETER.getParameter(arguments)) }
    private var overviewData: List<IdentificationGroupDTO>? = emptyList()

    private var isIdCardExpired = false
    private var errorMessageIdCardExpired = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = PiFragmentPhotoBinding.inflate(inflater, container, false)

        overviewData = caseResponse.identificationGroups
        val act = requireActivity() as PhotoIdentActivity

        act.currentScreen.observe(viewLifecycleOwner) { updateUi(it) }
        currentUploadStatus.observe(viewLifecycleOwner) { uploadUi.showProgress(uploadSize, it.value) }

        camera = CameraWrapper(this, viewBinding.camera.piCameraViewFinder)

        initCameraView(act)
        initPreviewView(act)
        initUploadStatusView(act)

        uploadUi = UploadUi(viewBinding.upload) {
            if (isIdCardExpired) {
                handleExpiredIdCardError()
            } else {
                act.updateCaseResponse()
            }
        }

        if (caseResponse.caseStatus?.statusCode == StatusCodeDTO.NEU) {
            act.updateScreen(Screen.OVERVIEW)
        } else {
            act.updateScreen(Screen.UPLOAD_STATUS)
        }

        return viewBinding.root
    }

    private fun initUploadStatusView(act: PhotoIdentActivity) {
        uploadStatusView = UploadStatusView(viewBinding.uploadStatus, caseResponse) { action ->
            when (action) {
                UploadStatusView.BtnAction.CLOSE -> act.finishPhotoWithSuccess()
                UploadStatusView.BtnAction.BACK -> act.updateScreen(Screen.OVERVIEW)
                UploadStatusView.BtnAction.TO_METHOD_SELECTION -> requireActivity().onBackPressed()
                UploadStatusView.BtnAction.REFRESH -> {

                    lifecycleScope.launch {
                        viewBinding.uploadStatus.statusProgressBar.isVisible = true
                        viewBinding.uploadStatus.refreshButton.isVisible = false
                        act.updateCaseResponse()
                    }
                }
            }
        }
    }

    private fun initCameraView(act: PhotoIdentActivity) {
        cameraView = CameraView(viewBinding.camera) { action ->
            when (action) {
                CameraView.BtnAction.TAKE -> {
                    lifecycleScope.launch {
                        act.updateScreen(Screen.PREVIEW)
                        previewView.showProgress(true)
                        try {
                            val image = camera.takePictureAndResize(cameraView.filename)
                            previewView.showImage(image.bitmap)
                            lastImageData = image
                        } catch (err: Throwable) {
                            ensureActive()
                            act.updateScreen(Screen.CAMERA)
                        }
                    }
                }
                CameraView.BtnAction.SWITCH -> {
                    camera.switchCamera()
                }
                CameraView.BtnAction.NEXT -> {
                    viewBinding.camera.tabLayout.getTabAt(viewBinding.camera.tabLayout.selectedTabPosition + 1)?.select()
                    act.updateScreen(Screen.CAMERA)
                }
                CameraView.BtnAction.PREVIOUS -> {
                    viewBinding.camera.tabLayout.getTabAt(viewBinding.camera.tabLayout.selectedTabPosition - 1)?.select()
                    act.updateScreen(Screen.CAMERA)
                }
                CameraView.BtnAction.START -> {
                    recordVideo(act)
                }
            }
        }
    }

    private fun recordVideo(act: PhotoIdentActivity) {
        lifecycleScope.launch {
            cameraView.updateViewVideoRecording()
            cameraView.filename?.let { MediaEncryption.clearStepFiles(viewBinding.root.context, caseResponse.caseId, it) }

            val file = File(MediaEncryption.getMediaPath(viewBinding.root.context, caseResponse.caseId), "${cameraView.filename}.mp4")
            camera.startVideo(
                file = file,
                onVideoSaved = { MediaEncryption.encryptMediaFile(file) },
                onErrorListener = {
                    cameraView.filename?.let { filename ->
                        MediaEncryption.clearStepFiles(viewBinding.root.context, caseResponse.caseId, filename)
                        getStepItem(filename)?.apply {
                            status = StatusDTO.REJECTED
                            statusMessage = LocalizedStrings.getString("err_photo_empty_file")
                            Handler(Looper.getMainLooper()).post {
                                updateUi(Screen.OVERVIEW)
                            }
                        }
                    }
                })

            for (i in 3 downTo 0) {
                cameraView.updateRemainingTimeRecording("0:0$i")
                if (i > 0) delay(1000)
            }
            camera.stopVideo()
            act.updateScreen(Screen.OVERVIEW)
        }
    }

    private fun initPreviewView(act: PhotoIdentActivity) {
        previewView = CapturedPreviewView(viewBinding.preview) { action ->
            when (action) {
                CapturedPreviewView.BtnAction.RETRY -> {
                    act.updateScreen(Screen.CAMERA)
                }
                CapturedPreviewView.BtnAction.USE -> {
                    lastImageData?.let { image ->
                        val file = File(MediaEncryption.getMediaPath(viewBinding.root.context, caseResponse.caseId), "${image.filename}.jpeg")

                        try {
                            FileOutputStream(file).use {
                                image.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
                            }
                            MediaEncryption.encryptMediaFile(file)
                        } catch (e: Exception) {
                            log("bitmap error", e)
                            image.filename?.let { filename ->
                                MediaEncryption.clearStepFiles(viewBinding.root.context, caseResponse.caseId, filename)
                                getStepItem(filename)?.apply {
                                    status = StatusDTO.REJECTED
                                    statusMessage = LocalizedStrings.getString("err_photo_empty_file")
                                }
                            }
                        } finally {
                            file.delete()
                        }
                        act.updateScreen(Screen.OVERVIEW)
                    }
                }
            }
        }
    }

    private fun getStepItem(filename: String): IdentificationStepDTO? {
        overviewData?.forEach { group ->
            group.steps.forEach { step ->
                if (step.fileName == filename) return step
            }
        }
        return null
    }

    private fun updateUi(screen: Screen) {

        when (screen) {
            Screen.UPLOAD_STATUS -> {
                updateCallbackUrl()
            }
            else -> {}
        }

        viewBinding.camera.root.isVisible = screen == Screen.CAMERA
        viewBinding.preview.root.isVisible = screen == Screen.PREVIEW
        viewBinding.overview.root.isVisible = screen == Screen.OVERVIEW || screen == Screen.UPLOAD_UI
        viewBinding.upload.root.isVisible = screen == Screen.UPLOAD_UI
        viewBinding.uploadStatus.root.isVisible = screen == Screen.UPLOAD_STATUS

        if (overviewData == null) {
            showAlertDialog(requireContext(), LocalizedStrings.getString(R.string.err_dialog_technical_error)) {
                requireActivity().setResult(SdkResultCodes.RESULT_TECHNICAL_ERROR.id)
                requireActivity().finish()
            }
            return
        }

        checkForExistingMediaFiles(overviewData!!, caseResponse.caseId)

        viewBinding.overview.dataFieldsContainer.removeAllViews()
        overviewData!!.sortedBy { it.groupNumber }
                .forEach { identificationGroup ->
                    val overviewItems = mutableListOf<OverviewItem>()

                    overviewItems.add(GroupItem(layoutInflater, viewBinding.overview.dataFieldsContainer, identificationGroup) { stepItem, groupId ->
                        onStepClicked(stepItem, groupId)
                    })
                }
        val readyForUpload = isReadyForUpload(overviewData!!, caseResponse.caseId)

        if (readyForUpload) {
            try {
                val file = createZipFile(caseResponse.caseId)
                 uploadSize = getFileSizeInMB(file)
            } catch (e: Exception) {
                removeAllFiles(caseResponse.caseId)
                log("error creating Zip File")
            }
        }
        val buttonText = if (readyForUpload) " (${uploadSize} MB)" else ""
        updateButton(readyForUpload,buttonText)
    }

    private fun onStepClicked(stepItem: IdentificationStepDTO, groupId: IdentificationGroupDTO.GroupIdDTO) {
        val groupItem = overviewData!!.find { it.groupId == groupId }
        val clickedStep = groupItem?.steps?.find { it.stepType == stepItem.stepType }

        if (clickedStep?.stepType == IdentificationStepDTO.StepTypeDTO.VIDEO) {
            camera.selectCamera(true)
            cameraView.updateViewVideoDescription()
        } else {
            val side = when (clickedStep?.stepType) {
                IdentificationStepDTO.StepTypeDTO.UNKNOWN -> CameraView.DocumentSide.OTHER
                IdentificationStepDTO.StepTypeDTO.IMAGEFRONT -> CameraView.DocumentSide.FRONT
                IdentificationStepDTO.StepTypeDTO.IMAGEBACK -> CameraView.DocumentSide.BACK
                else -> throw IllegalArgumentException()
            }
            val type = when (groupItem.groupId) {
                IdentificationGroupDTO.GroupIdDTO.UNKNOWN -> throw IllegalArgumentException()
                IdentificationGroupDTO.GroupIdDTO.IDENTIFICATION_CARD -> DocumentType.IDENTIFICATION
                IdentificationGroupDTO.GroupIdDTO.DRIVERS_LICENSE -> DocumentType.DRIVERS_LICENSE
                else -> throw IllegalArgumentException()
            }
            camera.selectCamera(false)
            cameraView.updateViewPhoto(side, type)
        }

        cameraView.filename = clickedStep.fileName
        (requireActivity() as PhotoIdentActivity).updateScreen(Screen.CAMERA)
    }

    private fun isReadyForUpload(overviewData: List<IdentificationGroupDTO>, caseId: String): Boolean {
        overviewData.forEach { group ->
            group.steps.forEach { step ->
                if (step.status == StatusDTO.OPEN || step.status == StatusDTO.REJECTED) return false
            }
        }
        return true
    }

    private fun checkForExistingMediaFiles(overviewData: List<IdentificationGroupDTO>, caseId: String) {
        val folder = File(MediaEncryption.getMediaPath(viewBinding.root.context, caseId))
        overviewData.forEach { group ->
            group.steps.forEach { step ->
                val foundFile = folder.listFiles()?.find { it.name.contains(step.fileName) }
                if ((step.status == StatusDTO.OPEN || step.status == StatusDTO.REJECTED) && foundFile != null) {
                    step.status = StatusDTO.CREATED
                }
            }
        }
    }

    private fun getFileSizeInMB(file: File): Double {
        val bytes = file.length()
        val kilobytes = bytes / 1024
        val megabytes = kilobytes / 1024
        return megabytes.toDouble()
    }

    private fun createZipFile(caseId: String): File {
        val mediaFolder = File(MediaEncryption.getMediaPath(viewBinding.root.context, caseId))
        val files = mediaFolder.listFiles()
        val zipFile = File(MediaEncryption.getRootPath(viewBinding.root.context, caseId), "archive.zip")

        val buffer = ByteArray(1024)
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->

            files?.forEach { file ->
                val inputStream = FileInputStream(file)
                zipOutputStream.putNextEntry(ZipEntry(file.name))

                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    zipOutputStream.write(buffer, 0, length)
                }

                zipOutputStream.closeEntry()
                inputStream.close()
            }
        }
        return zipFile
    }

    private fun removeAllFiles(caseId: String) {
        File(MediaEncryption.getRootPath(viewBinding.root.context, caseId)).deleteRecursively()
    }

    private fun updateButton(isReadyForUpload: Boolean, uploadSize: String) {
        val act = (requireActivity() as PhotoIdentActivity)
        viewBinding.overview.btnUpload.text = LocalizedStrings.getString("photo_send_documents_button") + uploadSize
        submitButtonController = MaterialButtonLoadingController(requireContext(), viewBinding.overview.btnUpload)
        viewBinding.overview.btnUpload.isEnabled = isReadyForUpload
        viewBinding.overview.btnUpload.setOnClickListener { onUploadClicked(act) }
    }

    private fun onUploadClicked(act: PhotoIdentActivity) {
        lifecycleScope.launch {
            try {
                submitButtonController.loadingAnimation(true)
                act.updateScreen(Screen.UPLOAD_UI)
                CoreEmmiService.uploadPhotoDocuments(caseResponse.caseId, File(MediaEncryption.getRootPath(viewBinding.root.context, caseResponse.caseId), "archive.zip")) { _, value ->
                    /* filesize of the zipfile is used for calculation progress, because okhttp
                    offered size differs -> user would be confused why size on button differs fromm size on upload progress*/
                    uploadUi.showProgress(uploadSize, value)
                }
                emmiReporter.send(logEvent = LogEvent.PH_UPLOAD_DOCUMENTS)

                removeAllFiles(caseResponse.caseId)

                act.updateCaseResponse()
            } catch (err: Throwable) {
                if (err is HttpException) {
                    when (err.code) {
                        in HttpURLConnection.HTTP_INTERNAL_ERROR..HttpURLConnection.HTTP_VERSION -> {
                            showUploadError("photo_upload_failed")
                        }
                        HTTP_PRECONDITION_REQUIRED -> {
                            when {
                                err.body?.contains("FI_D_002") == true -> { // ERROR_CODE_EMPTY_FILE
                                    showUploadError("photo_upload_failed_empty_file", true)
                                }
                                err.body?.contains("FI_D_003") == true -> { // ERROR_CODE_DOCUMENT_EXPIRED
                                    showUploadError("eid_error_card_expired0", false)
                                    isIdCardExpired = true
                                    errorMessageIdCardExpired = err.body.toString().substringAfter("errorText\":\"").substringBefore("\",\"parameterName")
                                }
                                err.body?.contains("FI_D_004") == true // ERROR_CODE_DOCUMENT_INVALID_COUNTRY
                                        || err.body?.contains("FI_D_005") == true // ERROR_CODE_DOCUMENT_NOT_SUPPORTED
                                        || err.body?.contains("FI_D_006") == true -> { // ERROR_CODE_DOCUMENT_TYPE_INVALID
                                    showUploadError("eid_error_card_not_supported0", true)
                                }
                                else -> {
                                    showUploadError("photo_upload_failed", true)
                                }
                            }
                        }
                        else -> {
                            showUploadError("photo_upload_failed", true)
                        }
                    }
                } else {
                    showUploadError("photo_upload_failed", true)
                }
            }
            viewBinding.upload.progressBar.isVisible = false
            submitButtonController.loadingAnimation(false)
        }
    }

    private fun showUploadError(stringKey: String, removeAllFiles: Boolean = false) {
        if (removeAllFiles) removeAllFiles(caseResponse.caseId)
        viewBinding.upload.textviewUpload.text = LocalizedStrings.getString(stringKey)
        viewBinding.upload.uploadFail.isVisible = true
    }

    private fun updateCallbackUrl() {
        lifecycleScope.launch {
            try {
                val act = (requireActivity() as PhotoIdentActivity)
                val caseResponse =
                    CoreEmmiService.getPhotoIdentInformationByCaseId(act.getCaseResponse().caseId)
                caseResponse.modules.identStatusUpdate?.callBackUrl.let { act.setCallbackUrl(it) }
            } catch (err: Throwable) {
                log("Error occurred during refresh callbackUrl", err)
            }
        }
    }

    //TODO Use Localize for statusMsg
    private fun handleExpiredIdCardError() {
        isIdCardExpired = false
        getStepItem("idfrontside")?.apply {
            MediaEncryption.clearStepFiles(viewBinding.root.context, caseResponse.caseId, fileName)
            status = StatusDTO.REJECTED
            statusMessage = errorMessageIdCardExpired
        }
        getStepItem("idbackside")?.apply {
            MediaEncryption.clearStepFiles(viewBinding.root.context, caseResponse.caseId, fileName)
            status = StatusDTO.REJECTED
            statusMessage = errorMessageIdCardExpired
        }

        // Reset uploadUi
        viewBinding.upload.apply {
            textviewUpload.text = ""
            progressBar.isVisible = true
            uploadFail.isVisible = false
        }
        updateUi(Screen.OVERVIEW)
    }
}

