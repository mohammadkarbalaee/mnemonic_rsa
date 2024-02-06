package de.post.ident.internal_autoid

import android.content.Context
import android.content.Intent
import android.os.*
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.Keep
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.Moshi
import de.post.ident.internal_autoid.ui.*
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.camera.CameraMock
import de.post.ident.internal_core.permission.PermissionFragmentParameter
import de.post.ident.internal_core.permission.UserPermission
import de.post.ident.internal_core.process_description.ProcessDescriptionFragment
import de.post.ident.internal_core.reporting.AutoIdErrorCode
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.rest.CoreEmmiService.sendCreateAttempt
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.ModuleMetaData
import de.post.ident.internal_core.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoIdentActivity : BaseModuleActivity() {

    @Keep
    enum class Screen {
        DOC_TYPE_SELECTION, STEP_DESCRIPTION, STEP_SUCCESS, DOC_CHECK_FRONT, DOC_CHECK_BACK, IMAGE_REVIEW, DOC_CHECK_RESULT, DVF, FVLC, STATUS_UI, LOADING, ERROR
    }

    private var currentIdentStep = Screen.DOC_CHECK_FRONT
    fun getCurrentIdentStep() = currentIdentStep
    fun updateCurrentIdentStep(identStep: Screen) {
        log("current Ident Step: $identStep")
        currentIdentStep = identStep
        resetStepRetryCounter()
    }

    val MAX_STEP_RETRIES = 3
    private var stepIterationCounter: Int = 1
    fun getStepIteration() = stepIterationCounter
    fun increaseStepRetryCounter() = ++stepIterationCounter
    private fun resetStepRetryCounter() { stepIterationCounter = 1 }

    val MAX_MPFINISH_RETRIES = 3
    private var mpFinishRetryCounter: Int = 0
    private fun increaseMpFinishRetryCounter() = ++mpFinishRetryCounter

    private lateinit var referenceImage : ByteArray
    fun getReferenceImage() = referenceImage
    fun setReferenceImage(image: ByteArray) = run { referenceImage = image }

    private val _currentFaceDirection = MutableLiveData<FaceDirection>()
    val currentFaceDirection: LiveData<FaceDirection> get() = _currentFaceDirection
    fun updateFaceDirection(faceDirection: FaceDirection) {
        _currentFaceDirection.value = faceDirection
    }

    private val _currentScreen = MutableLiveData<Screen>()
    val currentScreen: LiveData<Screen> get() = _currentScreen
    fun updateScreen(screen: Screen) {
        _currentScreen.value = screen
    }
    var status: AutoIdStatus = AutoIdStatus.RESULT_DECLINED
    private fun setStatus(status: AutoIdStatus, userFeedback: String? = null) {
        this.status = status
        this.status.userFeedback = userFeedback
    }

    private val emmiService: CoreEmmiService = CoreEmmiService
    private val emmiReporter: EmmiAutoIdReporter = EmmiAutoIdReporter
    private lateinit var prefsManager: AutoIdPrefsManager

    private lateinit var caseId: String
    private lateinit var autoId: String
    private lateinit var attempt: AttemptResponseDTO
    private lateinit var attemptId: String
    lateinit var autoIdentDocument: AutoIdentDocumentDTO
    var documentType: DocumentType = DocumentType.ID_CARD

    override fun permissionsGranted() {
        checkDataConsent()
    }
    override val moduleMetaData: ModuleMetaData by lazy {
        val permissionList = mutableListOf(UserPermission.CAMERA)
        val drawableList = arrayListOf(R.drawable.pi_pd_photo, R.drawable.pi_pd_dvf_step, R.drawable.pi_pd_fvlc_step)
        val titleList = checkNotNull(getCaseResponse().modules.processDescription?.subtitle)

        val pageDataList: List<ProcessDescriptionFragment.PageData> =
            titleList.mapIndexedNotNull { index, title ->
                if (index < drawableList.size) {
                    ProcessDescriptionFragment.PageData(title, drawableList[index])
                } else {
                    null
                }
            }

        ModuleMetaData(
            PermissionFragmentParameter(
                IdentMethod.AUTOID, permissionList, null),
            ProcessDescriptionFragment.ProcessDescriptionData(
                getProcessDescriptionData(), pageDataList, IdentMethodDTO.AUTOID
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?)  {
        super.onCreate(savedInstanceState)

        initContinueButton(viewBinding.btnContinueStandard) { checkDataConsent() }
        prefsManager = AutoIdPrefsManager(this)
    }

    private fun checkDataConsent() {
        try {
            caseId = checkNotNull(Commons.caseId)
            if (prefsManager.isTermsAccepted(caseId)) {
                lifecycleScope.launch { startIdentification() }
            } else {
                showChoiceDialog(this,
                    LocalizedStrings.getString("dialog_autoid_data_consent_title"),
                    HtmlCompat.fromHtml(
                        LocalizedStrings.getString("dialog_autoid_data_consent_text"),
                        HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
                    LocalizedStrings.getString("dialog_autoid_data_consent_agree"),
                    LocalizedStrings.getString("default_btn_cancel"),
                    false,
                    {
                        lifecycleScope.launch {
                            try {
                                emmiReporter.send(
                                    logEvent = LogEvent.AUTOIDENT_TERMS,
                                    eventContext = mapOf("accepted" to true.toString()),
                                    flush = true,
                                    machinePhase = false,
                                    eventStatus = EventStatus.SUCCESS
                                )
                                prefsManager.setTermsAccepted(caseId)
                                hideContinueButtons()
                                startIdentification()
                            } catch (e: Exception) {
                                showAlertDialog(this@AutoIdentActivity, e.getUserMessage(), onFinish = { onBackPressed() })
                            }
                        }
                    },
                    {
                        lifecycleScope.launch {
                            try {
                                emmiReporter.send(
                                    logEvent = LogEvent.AUTOIDENT_TERMS,
                                    eventContext = mapOf("accepted" to false.toString()),
                                    flush = true,
                                    machinePhase = false,
                                    eventStatus = EventStatus.ERROR
                                )
                                hideContinueButtons(continueBtnVisibility = View.VISIBLE)
                            } catch (e: Exception) {
                                showAlertDialog(this@AutoIdentActivity, e.getUserMessage())
                            }
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            showAlertDialog(this, e.getUserMessage())
        }
    }

    suspend fun startIdentification() {
        try {
            val identStatus = emmiService.getIdentStatus(caseId)
            when (identStatus.statusCode) {
                701, 750 -> {
                    sendTermsAccepted()
                    startNewAttempt()
                    if (BuildConfig.CAMERA_MOCK) CameraMock.init()
                }
                else -> handleReentryCase()
            }
        } catch (e: Throwable) {
            try {
                emmiService.sendIncomplete(caseId, attemptId, AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON, internalNote = "incomplete because of technical reason: ${e.javaClass}")
                logIncompleteReason(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON, "error starting new Identification")
                showAlertDialog(this, e.getUserMessage())
            } catch (e: Throwable) {
                log("error sending incomplete")
            }
        }
    }

    fun restartAutoId() {
        try {
            if (BuildConfig.CAMERA_MOCK) CameraMock.init()
            if (currentIdentStep == Screen.DOC_CHECK_FRONT || currentIdentStep == Screen.DOC_CHECK_BACK) {
                updateCurrentIdentStep(Screen.DOC_CHECK_FRONT)
                startNewAttempt()
            } else showFragment(AutoIdentFragment.newInstance(AutoIdentData(caseId, attemptId, autoId)))
        } catch (e: Exception) {
            showAlertDialog(this, e.getUserMessage())
        }
    }

    private fun startNewAttempt() {
        lifecycleScope.launch {
            try {
                attempt = createAttempt()
                Commons.attemptId = attempt.attemptId
                attemptId = checkNotNull(attempt.attemptId)
                autoId = checkNotNull(attempt.autoId)
                showFragment(AutoIdentFragment.newInstance(AutoIdentData(caseId, attemptId, autoId)))
            } catch (httpException: HttpException) {
                if (httpException.code == 409) { //ident completed but case still being processed
                    log("attempt creation failed, ident $caseId already completed")
                    handleReentryCase()
                } else {
                    showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
                }
            } catch (e: Throwable) {
                showErrorScreen(AutoIdErrorCode.INCOMPLETE_OTHER_TECHNICAL_REASON)
            }
        }
    }

    fun showErrorScreen(errorCode: AutoIdErrorCode) {
        try {
            emmiReporter.send(
                logEvent = LogEvent.AUTOIDENT_MACHINE_ERROR_SCREEN,
                eventContext = mapOf("errorCode" to errorCode.id),
                flush = true,
                eventStatus = EventStatus.ERROR
            )
        } catch (e: Throwable) {
            log("error sending AUTOIDENT_MACHINE_ERROR_SCREEN to EMMI")
        } finally {
            log("is retry of current step: $currentIdentStep allowed: ${isRetryAllowed()}")
            updateScreen(Screen.ERROR)
        }
    }

    fun showStepSuccessAndContinue(stepContinue: Screen) {
        updateCurrentIdentStep(stepContinue)
        updateScreen(Screen.STEP_SUCCESS)
        Handler(Looper.getMainLooper()).postDelayed({
            updateScreen(Screen.STEP_DESCRIPTION)
        }, 2500)
    }

    fun showBackButton(show: Boolean, onClick: () -> Unit) = showBackButton(
        viewBinding.root.findViewById(R.id.toolbar_actionbar),
        show,
        onClick
    )

    fun hideContinueButton() = hideContinueButtons()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentScreen.value) {
            Screen.STATUS_UI -> finishAutoIdWithSuccess()
            Screen.DOC_TYPE_SELECTION -> showCancelDialog()
            Screen.IMAGE_REVIEW -> showCancelDialog()
            Screen.STEP_DESCRIPTION -> showCancelDialog()
            Screen.DVF -> showCancelDialog()
            Screen.FVLC -> showCancelDialog()
            Screen.DOC_CHECK_FRONT -> showCancelDialog()
            Screen.DOC_CHECK_BACK -> showCancelDialog()
            Screen.DOC_CHECK_RESULT -> showCancelDialog()
            else -> super.onBackPressed()
        }
    }

    private suspend fun createAttempt(): AttemptResponseDTO {
        val connTypeAdapter = Moshi.Builder().build().adapter(ConnectionTypeDTO::class.java)
        val connTypeJson = connTypeAdapter.toJson(ConnectionTypeDTO(getNetworkConnectionType(this)))

        val userFrontendInformation = UserFrontendInformationDTO(additionalData = connTypeJson)
        return sendCreateAttempt(userFrontendInformation, caseId, "autoIdent")
    }

    fun finishSdkWithTechnicalError() {
        val resultIntent = Intent()
        setResult(SdkResultCodes.RESULT_TECHNICAL_ERROR.id, resultIntent)
        finish()
    }

    fun finishSdk() {
        val resultIntent = Intent()
        setResult(SdkResultCodes.RESULT_OK.id, resultIntent)
        finish()
    }

    fun vibratePhone() {
        val v = (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(200,
                VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(200)
        }
    }

    fun setKeepScreenOn(keepScreenOn: Boolean) = lifecycleScope.launch(Dispatchers.Main) {
        window.apply {
            if (keepScreenOn) {
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun sendMpFinish(caseId: String, attemptId: String) {
        lifecycleScope.launch {
            try {
                val mpFinish = emmiService.mpFinish(caseId, attemptId)
                log("mpFinish ${mpFinish.httpStatusCode}")
                when (mpFinish.httpStatusCode) {
                    200 -> {
                        emmiReporter.send(
                            logEvent = LogEvent.AUTOIDENT_MACHINE_PHASE_SUCCESS,
                            flush = true,
                            attemptId = attemptId,
                            eventStatus = EventStatus.SUCCESS
                        )
                        getIdentStatus(caseId)
                    }
                    202 -> {
                        if (mpFinishRetryCounter < MAX_MPFINISH_RETRIES) {
                            delay(1000) //wait before retry
                            sendMpFinish(caseId, attemptId)
                            increaseMpFinishRetryCounter()
                            log("mpFinish retry #$mpFinishRetryCounter")
                        } else {
                            log("mpFinish retries exceeded")
                            sendAndLogIncomplete(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
                            getIdentStatus(caseId)
                        }
                    }
                }
                log("finish autoident ${mpFinish.statusCode}")
            } catch (e: Throwable) {
                e.printStackTrace()
                sendAndLogIncomplete(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
                setStatus(AutoIdStatus.RESULT_INCOMPLETE, LocalizedStrings.getString("doc_check_dataNotRead"))
                showErrorScreen(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
            }
        }
    }

    fun getIdentStatus(caseId: String) {
        lifecycleScope.launch {
            try {
                val identStatus = emmiService.getIdentStatus(caseId)
                log("ident status code:${identStatus.statusCode} subCode:${identStatus.subStatusCode}")
                when (identStatus.statusCode) {
                    750 -> { //incomplete
                        updateCallbackUrl()
                        setStatus(AutoIdStatus.RESULT_INCOMPLETE, identStatus.userFeedback)
                        updateScreen(Screen.ERROR)
                    }
                    720, in 730..749 -> { //agent required
                        updateCallbackUrl()
                        setStatus(AutoIdStatus.RESULT_AGENT_REQUIRED, identStatus.userFeedback)
                        updateScreen(Screen.STATUS_UI)
                    }
                    760, 761 -> { // declined
                        updateCallbackUrl()
                        setStatus(AutoIdStatus.RESULT_DECLINED, identStatus.userFeedback)
                        when (identStatus.subStatusCode) {
                            11, //fraud
                            15 -> { //abuse
                                updateScreen(Screen.STATUS_UI)
                            }
                            else -> {
                                updateScreen(Screen.STATUS_UI)
                            }
                        }
                    }
                    770, 771 -> { //success
                        setStatus(AutoIdStatus.RESULT_SUCCESS)
                        updateScreen(Screen.STATUS_UI)
                    }
                    else -> {
                        showErrorScreen(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                showErrorScreen(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
            }
        }
    }

    private fun handleReentryCase() {
        lifecycleScope.launch {
            try {
                val identStatus = emmiService.getIdentStatus(caseId)
                log("ident status code:${identStatus.statusCode}")
                showFragment(AutoIdentFragment.newInstance(AutoIdentData(caseId, "", "")))
                when (identStatus.statusCode) {
                    760, 761 -> { // declined
                        setStatus(AutoIdStatus.REENTRY_INCOMPLETE, identStatus.userFeedback)
                        updateScreen(Screen.ERROR)
                        updateCurrentIdentStep(Screen.ERROR)
                    }
                    720, in 730..749 -> { //agent required
                        setStatus(AutoIdStatus.REENTRY_IN_PROGRESS)
                        updateScreen(Screen.STATUS_UI)
                        updateCurrentIdentStep(Screen.STATUS_UI)
                    }
                    770, 771 -> { //success
                        setStatus(AutoIdStatus.REENTRY_SUCCESS, identStatus.userFeedback)
                        updateScreen(Screen.STATUS_UI)
                        updateCurrentIdentStep(Screen.STATUS_UI)
                    }
                    else -> {
                        showErrorScreen(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                showErrorScreen(AutoIdErrorCode.BACKEND_CANNOT_FINISH)
            }
        }
    }

    private suspend fun updateCallbackUrl() {
        try {
            val callbackUrl = emmiService.getCaseInformationByPath("/cases/${caseId}/autoIdent").modules.identStatus?.callbackUrl
            log("callbackUrl: $callbackUrl")
            setCallbackUrl(callbackUrl)
        } catch (e: Throwable) {
            log("fetching callbackUrl failed")
        }
    }

    suspend fun sendUserDocScans(): UserDocScanDTO {
        return emmiService.sendUserDocScans(caseId, attemptId, documentType.UserEntryDocTypeMajorAndConstructionCode)
    }

    suspend fun uploadSelfServicePhotos(savedImages: List<CoreEmmiService.FileUploadItem>, userDoc: UserDocScanDTO): AutoIdentCheckDocumentDTO {
        userDoc.documentType = documentType.UserEntryDocTypeMajorAndConstructionCode
        return emmiService.sendDocumentCheck(
            caseId,
            autoId,
            attemptId,
            userDoc,
            userDoc.docImage1MediaRecordId,
            userDoc.docImage2MediaRecordId,
            savedImages
        )
    }

    fun getNoteTimeout(backend: String, timeoutSeconds: Int) = "${getCurrentIdentStep().name}: no response from $backend, timeout after ${timeoutSeconds}s"
    fun getNoteHttpError(backend: String, httpError: Int) = "${getCurrentIdentStep().name}: http error $httpError from $backend"
    fun getNoteWsError(retryCount: Int) = "${getCurrentIdentStep().name}: ws connection lost, abort after $retryCount retries"
    fun sendAndLogIncomplete(
        errorCode: AutoIdErrorCode,
        internalNote: String? = null,
    ) = GlobalScope.launch(Dispatchers.IO) {
        loop@ for (i in 1..3 ) {
            try {
                val result = emmiService.sendIncomplete(caseId, attemptId, errorCode, internalNote)
                when (result.httpStatusCode) {
                    204 -> {
                        log("HttpStatusCode 204 - incomplete send")
                        logIncompleteReason(
                            errorCode,
                            internalNote ?: "incomplete was send without internal note"
                        )
                        return@launch
                    }
                    else -> {
                        delay(1000)
                        continue@loop
                    }
                }
            } catch (e: Exception) {
                log("error while sending incomplete - retry number $i")
            }
        }
    }

    private fun logIncompleteReason(
        errorCode: AutoIdErrorCode,
        message: String
    ) = lifecycleScope.launch(Dispatchers.IO) {
        try {
            emmiReporter.send(
                logEvent = LogEvent.AUTOIDENT_MACHINE_INCOMPLETE,
                eventContext = mapOf(
                    "errorCodeId" to errorCode.id,
                    "message" to message
                ),
                flush = true,
                eventStatus = EventStatus.ERROR
            )
            log("autoident incomplete logged")
        } catch (e: Exception) {
            log("error while sending incomplete reason")
        }
    }

    private fun showCancelDialog() {
        showChoiceDialog(
            context = this,
            title = LocalizedStrings.getString("process_cancel_dialog_title"),
            msg = LocalizedStrings.getString("cancel_message_default"),
            positiveButton = LocalizedStrings.getString("default_btn_yes"),
            onPositive = {
                GlobalScope.launch {
                    sendAndLogIncomplete(AutoIdErrorCode.USER_HAS_CANCELLED, AutoIdErrorCode.USER_HAS_CANCELLED.message)
                }
                Toast.makeText(this, LocalizedStrings.getString("autoid_process_cancelled"), Toast.LENGTH_SHORT).show()
                super.onBackPressed()
            },
            negativeButton = LocalizedStrings.getString("default_btn_no")
        )
    }

    private fun finishAutoIdWithSuccess() {
        finishSdkWithSuccess()
    }

    private fun isRetryAllowed() = getStepIteration() < MAX_STEP_RETRIES
}