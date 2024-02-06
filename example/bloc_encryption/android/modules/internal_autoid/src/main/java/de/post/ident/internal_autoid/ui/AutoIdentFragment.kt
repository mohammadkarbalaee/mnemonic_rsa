package de.post.ident.internal_autoid.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.ImageCapture
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.JsonClass
import de.post.ident.internal_autoid.*
import de.post.ident.internal_autoid.AutoIdentActivity.Screen
import de.post.ident.internal_autoid.databinding.*
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.camera.CameraWrapper
import de.post.ident.internal_core.reporting.AutoIdErrorCode
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.Exception

@JsonClass(generateAdapter = true)
data class Parameter(val sspList: List<SelfServicePhotoDto>, val uploadPath: String)

@JsonClass(generateAdapter = true)
data class AutoIdentData(val caseId: String, val attemptId: String, val autoId: String)

class AutoIdentFragment : Fragment() {

    companion object {
        private val AUTO_IDENT_PARAMETER: BundleParameter<AutoIdentData> = BundleParameter.moshi(
            CoreEmmiService.moshi, "AUTO_IDENT")

        fun newInstance(autoIdentData: AutoIdentData) =
            AutoIdentFragment().withParameter(autoIdentData, AUTO_IDENT_PARAMETER)
    }

    private val caseResponse by lazy { checkNotNull(AUTO_IDENT_PARAMETER.getParameter(arguments)) }
    private lateinit var viewBinding: PiFragmentAutoidBinding
    private val emmiService: CoreEmmiService = CoreEmmiService
    private val emmiReporter: EmmiAutoIdReporter = EmmiAutoIdReporter

    private val savedImages = mutableListOf<CoreEmmiService.FileUploadItem>()
    private fun getDocumentSide() = when (savedImages.size) {
        0 -> CameraView.DocumentSide.FRONT
        1 -> CameraView.DocumentSide.BACK
        else -> CameraView.DocumentSide.OTHER
    }

    lateinit var lastTakenImage: CameraWrapper.ResultImage
    private lateinit var camera: CameraWrapper
    private lateinit var cameraView: CameraView
    private lateinit var imageReviewView: ImageReviewView
    private lateinit var docCheckResultView: DocCheckResultView
    private lateinit var docTypeSelectionView: DocTypeSelectionView
    private lateinit var stepDescriptionView: StepDescriptionView
    private lateinit var stepSuccessView: StepSuccessView
    private lateinit var dvfView: DvfView
    private lateinit var fvLcView: FvLcView
    private lateinit var statusUi: StatusView
    private lateinit var loadingView: LoadingView
    private lateinit var errorView: ErrorView
    private lateinit var act: AutoIdentActivity
    private lateinit var userDocScans: UserDocScanDTO
    private var lastScreen: Screen = Screen.STEP_DESCRIPTION

    var autoIdentService: WebsocketManager? = null

    private val wsListener = object: WebsocketManager.WsListener {
        override fun onWsFailure() {
            lifecycleScope.launch(Dispatchers.Main) {
                if (act.getStepIteration() >= act.MAX_STEP_RETRIES) {
                    act.sendAndLogIncomplete(AutoIdErrorCode.WS_CONNECTION_PROBLEM, act.getNoteWsError(act.MAX_STEP_RETRIES))
                }
                if (camera.isFlashlightOn()) camera.toggleFlashlight()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        viewBinding = PiFragmentAutoidBinding.inflate(inflater, container, false)
        act = requireActivity() as AutoIdentActivity
        act.currentScreen.observe(viewLifecycleOwner) { updateUI(it) }
        act.currentFaceDirection.observe(viewLifecycleOwner) { fvLcView.updateFaceDirection(it) }

        initCameraView(act)
        initImageReviewView(act)
//        initDocCheckResultView(act)
        initDocTypeSelectionView(act)
        initStepDescriptionView(act)
        initStepSuccessView(act)
        initDfvView(act)
        initFvLcView(act)
        initStatusUi(act)
        initLoadingView()
        initErrorView(act)

        act.hideContinueButton()
        when (act.getCurrentIdentStep()) {
            Screen.DOC_CHECK_FRONT -> act.updateScreen(Screen.DOC_TYPE_SELECTION)
            Screen.STATUS_UI,
            Screen.ERROR -> { /* nothing to do */ }
            else -> act.updateScreen(Screen.STEP_DESCRIPTION)
        }

        return viewBinding.root
    }

    private fun initStatusUi(act: AutoIdentActivity) {
        statusUi = StatusView(viewBinding.statusui, act) { action ->
            when (action) {
                StatusView.BtnAction.STOP_AUTOID -> {
                    when (act.status) {
                        AutoIdStatus.REENTRY_IN_PROGRESS,
                        AutoIdStatus.RESULT_AGENT_REQUIRED -> act.finishSdk()
                        AutoIdStatus.REENTRY_SUCCESS,
                        AutoIdStatus.RESULT_SUCCESS -> act.finishSdkWithSuccess()
                        AutoIdStatus.RESULT_DECLINED,
                        AutoIdStatus.REENTRY_INCOMPLETE -> act.finishSdkWithCancelledByUser()
                        AutoIdStatus.RESULT_INCOMPLETE -> act.finishSdkWithTechnicalError()
                    }
                }
            }
        }
    }

    private fun initFvLcView(act: AutoIdentActivity) {
        fvLcView = FvLcView(viewBinding.fvlc, act) { action ->
            when (action) {
                FvLcView.BtnAction.START_FVLC -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            autoIdentService?.cancel("FVLC retry")
                            autoIdentService = WebsocketManager(caseResponse.caseId, caseResponse.attemptId, caseResponse.autoId, wsListener)
                            autoIdentService?.init(act, camera, act.getReferenceImage())
                            val fvlcNextRunResponse: FvlcNextRunResponseDTO = emmiService.fvlcNextRun(caseResponse.caseId, caseResponse.attemptId)
                            autoIdentService?.startFvLcRun(fvlcNextRunResponse)
                            emmiReporter.send(
                                logEvent = LogEvent.AUTOIDENT_FVLC_CAPTURE,
                                iterationCount = act.getStepIteration(),
                                attemptId = Commons.attemptId,
                                eventStatus = EventStatus.SUCCESS
                            )
                        } catch (e: Exception) {
                            log("fvlc error: ${e.message}")
                            emmiReporter.send(
                                logEvent = LogEvent.AUTOIDENT_FVLC_TIMEOUT,
                                eventContext = mapOf("frames" to autoIdentService?.frameCount.toString()),
                                flush = true,
                                eventStatus = EventStatus.ERROR
                            )
                            act.sendAndLogIncomplete(
                                AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON,
                                act.getNoteTimeout("ML", KRestApi.TIMEOUT.toInt())
                            )

                            lifecycleScope.launch(Dispatchers.Main) {
                                act.showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
                                autoIdentService?.cancel("FVLC ${AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initDfvView(act: AutoIdentActivity) {
        dvfView = DvfView(viewBinding.dvf, act) { action ->
            when (action) {
                DvfView.BtnAction.START_DFV -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            autoIdentService?.cancel("DVF retry")
                            autoIdentService = WebsocketManager(caseResponse.caseId, caseResponse.attemptId, caseResponse.autoId, wsListener)
                            autoIdentService?.init(act, camera, act.getReferenceImage())
                            val dvfNextRun: DvfNextRunResponseDTO = emmiService.dvfNextRun(caseResponse.caseId, caseResponse.attemptId)
                            autoIdentService?.startDvfRun(dvfNextRun, act.autoIdentDocument)
                            emmiReporter.send(
                                logEvent = LogEvent.AUTOIDENT_DVF_CAPTURE,
                                iterationCount = act.getStepIteration(),
                                attemptId = Commons.attemptId,
                                eventStatus = EventStatus.SUCCESS
                            )
                        } catch (e: Exception) {
                            log("dvf error: ${e.message}")
                            if (camera.isFlashlightOn()) camera.toggleFlashlight()
                            emmiReporter.send(
                                logEvent = LogEvent.AUTOIDENT_DVF_TIMEOUT,
                                eventContext = mapOf("frames" to autoIdentService?.frameCount.toString()),
                                flush = true,
                                eventStatus = EventStatus.ERROR
                            )
                            act.sendAndLogIncomplete(
                                    AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON,
                                    act.getNoteTimeout("ML", KRestApi.TIMEOUT.toInt())
                                )
                            lifecycleScope.launch(Dispatchers.Main) {
                                act.showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
                                autoIdentService?.cancel("DVF ${AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON.name}")
                            }
                        }
                    }
                }
                DvfView.BtnAction.FLASHLIGHT -> toggleFlashlight()
            }
        }
    }

    private fun initDocTypeSelectionView(act: AutoIdentActivity) {
        docTypeSelectionView = DocTypeSelectionView(viewBinding.docTypeSelection, act) { action ->
            when (action) {
                DocTypeSelectionView.BtnAction.CONTINUE -> {
                    act.updateScreen(Screen.STEP_DESCRIPTION)
                }
            }
        }
    }

    private fun initStepDescriptionView(act: AutoIdentActivity) {
        stepDescriptionView = StepDescriptionView(viewBinding.stepDescription, act) { action ->
            when (action) {
                StepDescriptionView.BtnAction.CONTINUE -> {
                    if (act.getCurrentIdentStep() == Screen.FVLC) fvLcView.resetView()
                    act.updateScreen(act.getCurrentIdentStep())
                }
            }
        }
    }

    private fun initStepSuccessView(act: AutoIdentActivity) {
        stepSuccessView = StepSuccessView(viewBinding.stepSuccess, act)
    }

    private fun initDocCheckResultView(act: AutoIdentActivity) {
        docCheckResultView = DocCheckResultView(viewBinding.docCheckResult, act) { action ->
            when (action) {
                DocCheckResultView.BtnAction.RETRY -> {
                    lifecycleScope.launch { (requireActivity() as AutoIdentActivity).startIdentification() }
                }
                DocCheckResultView.BtnAction.CONTINUE -> {
                    // todo: when docCheckResultScreen is implemented, check stepIteration Logic to be consistent
                    act.updateCurrentIdentStep(Screen.DVF)
                    act.updateScreen(Screen.STEP_DESCRIPTION)
                }
            }
        }
    }

    private fun initImageReviewView(act: AutoIdentActivity) {
        imageReviewView = ImageReviewView(viewBinding.review) { action ->
            when (action) {
                ImageReviewView.BtnAction.RETRY -> {
                    act.updateScreen(act.getCurrentIdentStep())
                }
                ImageReviewView.BtnAction.USE -> {
                    lastTakenImage.let { image ->
                        when (act.documentType) {
                            DocumentType.ID_CARD,
                            DocumentType.ID_CARD_ALTERNATIVE,
                            DocumentType.DRIVING_LICENSE -> {
                                if (savedImages.size == 0) {
                                    act.setReferenceImage(image.jpegDataArray)
                                    act.updateCurrentIdentStep(Screen.DOC_CHECK_BACK)
                                }
                            }
                            DocumentType.PASSPORT -> {
                                if (savedImages.size == 0) {
                                    act.updateCurrentIdentStep(Screen.DOC_CHECK_BACK)
                                } else if (savedImages.size == 1) {
                                    act.setReferenceImage(image.jpegDataArray)
                                }
                            }
                        }
                        savedImages.add(
                            CoreEmmiService.FileUploadItem(
                                getDocumentSide().name,
                                lastTakenImage.bitmap.width,
                                lastTakenImage.bitmap.height,
                                image.jpegDataArray
                            )
                        )
                    }
                    act.updateScreen(Screen.STEP_DESCRIPTION)
                    if (savedImages.size >= 2) {
                        uploadPhotos()
                    }
                }
            }
        }
    }

    private fun initCameraView(act: AutoIdentActivity) {
        cameraView = CameraView(act, viewBinding.camera) { action ->
            when (action) {
                CameraView.BtnAction.TAKE -> {
                    lifecycleScope.launch {
                        act.updateScreen(Screen.IMAGE_REVIEW)
                        imageReviewView.showProgress(true)
                        try {
                            val image = camera.getDocCheckImage(useCameraMock = BuildConfig.CAMERA_MOCK)
                            lastTakenImage = image
                            imageReviewView.showImage(image.bitmap)
                            lifecycleScope.launch {
                                try {
                                    if (savedImages.size == 0) userDocScans = act.sendUserDocScans()
                                    emmiReporter.send(
                                        logEvent = LogEvent.AUTOIDENT_DOCCHECK_CREATE_IMAGE,
                                        iterationCount = act.getStepIteration(),
                                        eventContext = mapOf(
                                            "userDocScanId" to userDocScans.id.toString(),
                                            "type" to getDocumentSide().name.lowercase()),
                                        attemptId = Commons.attemptId,
                                        eventStatus = EventStatus.SUCCESS
                                    )
                                } catch (e: HttpException) {
                                    act.sendAndLogIncomplete(
                                        AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON,
                                        act.getNoteHttpError("EMMI", e.code)
                                    )
                                    act.showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
                                }
                                catch (e: Exception) {
                                    act.showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
                                }
                            }
                        } catch (err: Throwable) {
                            act.showErrorScreen(AutoIdErrorCode.RECORDINGS_NOT_COMPLETE)
                            ensureActive()
                        }
                    }
                }
                else -> { /* ignore */
                }
            }
        }
    }

    private fun initLoadingView() {
        loadingView = LoadingView(viewBinding.loading, act)
    }

    private fun initErrorView(act: AutoIdentActivity) {
        errorView = ErrorView(viewBinding.error, act) { action ->
            when (action) {
                ErrorView.BtnAction.RETRY -> {
                    act.increaseStepRetryCounter()
                    act.restartAutoId()
                }
                ErrorView.BtnAction.ABORT -> {
                    act.sendAndLogIncomplete(AutoIdErrorCode.USER_HAS_CANCELLED, AutoIdErrorCode.USER_HAS_CANCELLED.message)
                    act.finish()
                }
            }
        }
    }

    private fun toggleFlashlight() {
        camera.toggleFlashlight()
        val buttonBackgroundRes = if (camera.isFlashlightOn()) R.drawable.pi_ic_flashlight_on else R.drawable.pi_ic_flashlight_off
        viewBinding.dvf.flashlightButton.setImageResource(buttonBackgroundRes)
    }

    private fun uploadPhotos() {
        lifecycleScope.launch {
            try {
                log("docCheck start photo upload")
                act.updateScreen(Screen.LOADING)
                val documentCheck = act.uploadSelfServicePhotos(savedImages, userDocScans)
                act.autoIdentDocument = documentCheck.resultData.doc
                val imageFront = savedImages.find { it.fileName == CameraView.DocumentSide.FRONT.name}
                val imageBack = savedImages.find { it.fileName == CameraView.DocumentSide.FRONT.name}
                lifecycleScope.launch {
                    emmiReporter.send(
                        logEvent = LogEvent.AUTOIDENT_DOCCHECK_UPLOAD,
                        iterationCount = act.getStepIteration(),
                        eventContext = mapOf(
                            "imageWidthFront" to imageFront?.widthPx.toString(),
                            "imageHeightFront" to imageFront?.heightPx.toString(),
                            "imageWidthBack" to imageBack?.widthPx.toString(),
                            "imageHeightBack" to imageBack?.heightPx.toString()
                        ),
                        attemptId = Commons.attemptId,
                        eventStatus = EventStatus.SUCCESS
                    )
                }
                if (documentCheck.isComplete) {
                    act.showStepSuccessAndContinue(Screen.DVF)
                } else {
                    act.showErrorScreen(AutoIdErrorCode.DOCUMENT_NOT_RECOGNIZED)
                }
            } catch (httpException: HttpException) {
                act.sendAndLogIncomplete(
                    AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON,
                    act.getNoteHttpError("EMMI", httpException.code)
                )
                act.showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
            } catch (e: Exception) {
                act.sendAndLogIncomplete(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
                act.showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
            }
        }
    }

    private fun updateUI(screen: Screen) {
        updateViewVisibility(screen)
        updateToolbar(screen)
        when (screen) {
            Screen.DOC_TYPE_SELECTION -> {}
            Screen.DOC_CHECK_FRONT, Screen.DOC_CHECK_BACK -> {
                camera = CameraWrapper(this, viewBinding.camera.piCameraViewFinder)
                cameraView.updateViewPhoto(getDocumentSide(), act.documentType)
                lifecycleScope.launch {
                    emmiReporter.send(
                        logEvent = LogEvent.AUTOIDENT_DOCCHECK_CAMERA_PREVIEW,
                        iterationCount = act.getStepIteration(),
                        eventContext = mapOf(
                            "type" to getDocumentSide().name.lowercase()
                        ),
                        attemptId = Commons.attemptId,
                        eventStatus = EventStatus.SUCCESS
                    )
                }
            }
            Screen.DVF -> {
                camera = CameraWrapper(
                    this,
                    viewBinding.dvf.piCameraViewFinder,
                    CameraWrapper.CameraSettings(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
                        CameraWrapper.defaultCameraSettings.width,
                        CameraWrapper.defaultCameraSettings.height
                    ))
                camera.selectCamera(false)
                dvfView.updateUi()
                dvfView.start()
            }
            Screen.FVLC -> {
                camera = CameraWrapper(this, viewBinding.fvlc.piCameraViewFinder)
                camera.selectCamera(true)
            }
            Screen.STEP_DESCRIPTION -> {
                stepDescriptionView.updateDescriptionView()
                when (act.getCurrentIdentStep()) {
                    Screen.DOC_CHECK_FRONT -> LogEvent.AUTOIDENT_DOCCHECK_DESCRIPTION
                    Screen.DOC_CHECK_BACK -> LogEvent.AUTOIDENT_DOCCHECK_DESCRIPTION
                    Screen.DVF -> LogEvent.AUTOIDENT_DVF_DESCRIPTION
                    Screen.FVLC -> LogEvent.AUTOIDENT_FVLC_DESCRIPTION
                    else -> null
                }?.let { logEvent ->
                    lifecycleScope.launch {
                        emmiReporter.send(
                            logEvent = logEvent,
                            iterationCount = act.getStepIteration(),
                            attemptId = Commons.attemptId,
                            eventStatus = EventStatus.SUCCESS
                        )
                    }
                }
            }
            Screen.STEP_SUCCESS -> stepSuccessView.update()
            Screen.LOADING -> loadingView.update()
            Screen.ERROR -> errorView.update()
            Screen.STATUS_UI -> {
                statusUi.update()
                emmiReporter.send(LogEvent.AUTOIDENT_MACHINE_PHASE_FEEDBACKSCREEN)
            }
            else -> {}
        }
        when (lastScreen) {
            Screen.DVF -> dvfView.stopAnimation()
            else -> {}
        }
        lastScreen = screen
    }

    private fun updateViewVisibility(screen: Screen) {
        viewBinding.docTypeSelection.root.isVisible = screen == Screen.DOC_TYPE_SELECTION
        viewBinding.stepDescription.root.isVisible = screen == Screen.STEP_DESCRIPTION
        viewBinding.stepSuccess.root.isVisible = screen == Screen.STEP_SUCCESS
        viewBinding.camera.root.isVisible = screen == Screen.DOC_CHECK_FRONT || screen == Screen.DOC_CHECK_BACK
        viewBinding.review.root.isVisible = screen == Screen.IMAGE_REVIEW
//        viewBinding.docCheckResult.root.isVisible = screen == Screen.DOC_CHECK_RESULT
        viewBinding.dvf.root.isVisible = screen == Screen.DVF
        viewBinding.fvlc.root.isVisible = screen == Screen.FVLC
        viewBinding.statusui.root.isVisible = screen == Screen.STATUS_UI
        viewBinding.loading.root.isVisible = screen == Screen.LOADING
        viewBinding.error.root.isVisible = screen == Screen.ERROR
    }

    private fun updateToolbar(screen: Screen) = act.showBackButton(
        listOf(
            Screen.DOC_CHECK_FRONT,
            Screen.DOC_CHECK_BACK,
            Screen.DVF,
            Screen.FVLC
        ).contains(screen)) {
        resetStep(act.getCurrentIdentStep())
        act.updateScreen(Screen.STEP_DESCRIPTION)
    }

    private fun resetStep(step: Screen) {
        when (step) {
            Screen.DOC_CHECK_FRONT, Screen.DOC_CHECK_BACK -> {
                log("reset step for Screen: $step")
                savedImages.clear()
            }
            Screen.DVF -> {
                log("reset step for Screen: $step")
                autoIdentService?.cancel("DVF retry")
                dvfView.reset()
                if (camera.isFlashlightOn()) camera.toggleFlashlight()
            }
            Screen.FVLC -> {
                log("reset step for Screen: $step")
                autoIdentService?.cancel("FVLC retry")
                fvLcView.resetView()
            }
            else -> { log("tried reset step for Screen: $step")}
        }
    }
}