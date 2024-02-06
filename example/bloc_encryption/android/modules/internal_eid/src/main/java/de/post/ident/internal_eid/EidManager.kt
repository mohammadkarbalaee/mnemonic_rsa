package de.post.ident.internal_eid

import android.net.Uri
import android.nfc.Tag
import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.squareup.moshi.Moshi
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.Commons.caseId
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.util.BasicCaseManager
import de.post.ident.internal_core.util.ConnectionTypeDTO
import de.post.ident.internal_core.util.getNetworkConnectionType
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.ui.EventBus
import de.post.ident.internal_core.util.ui.EventBusSender
import de.post.ident.internal_eid.EidError.GENERAL_ERROR_BACKEND
import de.post.ident.internal_eid.EidError.STATUS_POLLING_RETRIES_EXCEEDED
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EidManager private constructor(private val caseResponse: CaseResponseDTO, private val activity: EidIdentActivity) : CoroutineScope by MainScope() {
    companion object {
        private var instance: EidManager? = null
        fun init(caseResponse: CaseResponseDTO, activity: EidIdentActivity) {
            instance = EidManager(caseResponse, activity)
        }

        fun destroy() {
            instance = null
        }

        fun instance() = checkNotNull(instance)
    }

    private fun getCaseResponse(): CaseResponseDTO = BasicCaseManager.getCaseresponse() ?: caseResponse
    private val emmiService = CoreEmmiService
    private val emmiReporter = EmmiCoreReporter

    internal var attemptId: String? = null

    internal var currentStatus = CurrentStatus.ACTIVE
    @Keep
    enum class CurrentStatus { ACTIVE, ERROR, SUCCESS }

    private val _eidMessage = EventBusSender<EidMessage>()
    private val eidMessage: EventBus<EidMessage> get() = _eidMessage

    private val _uiState = MutableLiveData<EidScreen>()
    var screen: EidScreen?
        get() = _uiState.value
        set(value) {
            _uiState.value = value
            sendGetStatus()
        }

    val uiState: LiveData<EidScreen> get() = _uiState

    private var certInfo: EidCertificateDto? = null
    private var accessRights: EidAccessRightsDto? = null
    private var attemptData: EidAttemptDataDTO = EidAttemptDataDTO()

    private var wasPinChanged = false
    private var simulatorAvailable = false

    private var ausweisApp2Sdk = AusweisAppSdkWrapper(activity) { message ->
        receiveMessage(message)
    }

    suspend fun initAusweisApp2Sdk(isNewAttempt: Boolean, isPinChange: Boolean) {
        if (isNewAttempt) { startAttempt() }

        try {
            val versionInfo: EidVersionInfoDto = sendCommand(EidCommandDto(cmd = EidCommandDto.CommandType.GET_INFO))
            log("received version info: $versionInfo")

            attemptData.eidClientName = versionInfo.ausweisAppTitle
            attemptData.eidClientVersion = versionInfo.ausweisAppVersion

            enterPinEventReceived = null
            if (isPinChange) { // PIN CHANGE
                sendCommand<InsertCardEvent>(EidCommandDto(cmd = EidCommandDto.CommandType.RUN_CHANGE_PIN))
                screen = EnterTransportPinScreen(
                    canRequired = false,
                    onPinCanEntered = { pin, can ->
                        transportPinEntered(pin, can)
                    },
                    onGoToEnterPinClicked = {
                        restartProcess(cancelWorkflow = true, inPinChangeMode = false)
                    })
            } else { // IDENT
                val emmiUrl = Uri.parse(CoreConfig.serverConfig.emmiUrl)
                val tcTokenUrl = "${emmiUrl.scheme}://${emmiUrl.host}/identportalEID/SamlRequest.html?parameter=${Commons.caseId}&attemptId=$attemptId"
                log("tcTokenUrl: $tcTokenUrl")

                accessRights = sendCommand(EidCommandDto(cmd = EidCommandDto.CommandType.RUN_AUTH, tcTokenUrl = tcTokenUrl, developerMode = CoreConfig.enableDevModeEid))
                certInfo = sendCommand(EidCommandDto(EidCommandDto.CommandType.GET_CERTIFICATE))

                val skipped: Boolean

                if (Commons.showEidAccessRightsScreen) {
                    screen = AccessRightsScreen(certInfo, accessRights, onAccept = { acceptRights() })
                    skipped = false
                } else {
                    acceptRights()
                    skipped = true
                }

                val eventContext = mapOf("skipped" to skipped.toString())
                emmiReporter.send(logEvent = LogEvent.EI_DISPLAY_DATA, eventContext = eventContext, attemptId = Commons.attemptId)
            }
        } catch (err: Throwable) {
            log(err)
            handleError(err)
        }
    }

    private fun restartProcess(cancelWorkflow: Boolean, inPinChangeMode: Boolean) {
        launch {
            log("restart process cancelWorkflow: $cancelWorkflow isPinChangeMode: $inPinChangeMode")
            if (cancelWorkflow) {
                try {
                    sendCommand<EidMessage>(EidCommandDto(cmd = EidCommandDto.CommandType.CANCEL))
                } catch (err: Throwable) {
                    log(err)
                    //Ignore cancel error
                }
            }
            initAusweisApp2Sdk(isNewAttempt = false, isPinChange = inPinChangeMode)
            activity.setIsReadingNfc(false)
        }
    }

    private fun restartIdent() {
        log("Restart")
        launch {
            try {
                sendCommandAsync(EidCommandDto(cmd = EidCommandDto.CommandType.CANCEL))
            } catch (err: Throwable) {
                log(err)
            }
            activity.finish()
        }
    }

    private fun acceptRights() {
        launch {
            try {
                sendCommand<InsertCardEvent>(EidCommandDto(EidCommandDto.CommandType.ACCEPT))
                screen = EnterPinScreen(
                    wasPinChanged = wasPinChanged,
                    onPinEntered = { pin -> pinEntered(pin) },
                    onChangePinClicked = { restartProcess(cancelWorkflow = true, inPinChangeMode = true) }
                )

                // always log timestamp, whether user has clicked _or_ is redirected
                attemptData.identDocFieldsApprovedByUserTimestamp = System.currentTimeMillis()

                if (Commons.showEidAccessRightsScreen) {
                    Commons.showEidAccessRightsScreen = false
                }
            } catch (err: Throwable) {
                handleError(err)
            }
        }
    }

    private var enterPinEventReceived: EnterPinEvent? = null

    private fun pinEntered(pin: String) {
        screen = ScanInfoScreen(ScanScreen.IdentContext.IDENT) { pinEnteredScan(pin) }
    }

    private fun pinEnteredScan(pin: String) {
        launch {
            try {
                log("pinEntered wait for EnterPinEvent")
                var scanScreen: ScanScreen? = null
                scanScreen = ScanScreen(identContext = ScanScreen.IdentContext.IDENT,
                    onHelpClicked = {
                        screen = HelpScreen(activity = activity, caseResponse = getCaseResponse(),
                            onRetryClicked = { screen = scanScreen },
                            onBackClicked = { activity.finish() }, attemptId)
                    })
                screen = scanScreen
                val lastPinEvent = enterPinEventReceived
                val enterPinEvent = lastPinEvent ?: waitForSdkResult()
                if (lastPinEvent == null) {
                    attemptData.increaseCardReadCount()
                    activity.setIsReadingNfc(true)
                    scanScreen.showOverlay(true)
                }
                if (enterPinEvent.requireCan) {
                    screen = canInfoScreen

                    log("Start can process")
                    return@launch
                }
                log("PinEnterEvent received. Send pin")

                emmiReporter.send(logEvent = LogEvent.EI_PIN_CHECK, attemptId = Commons.attemptId)
                sendCommandAsync(EidCommandDto(cmd = EidCommandDto.CommandType.SET_PIN, value = if (simulatorAvailable) null else pin))
                waitForPin(scanScreen, pin)
            } catch (error: Throwable) {
                handleError(error)
            }
        }
    }

    private val canInfoScreen = CanInfoScreen(onContinue = { screen = enterCanScreen }, onBackClicked = { activity.finish() })
    private val enterCanScreen = EnterCanScreen { pin, can -> pinCanEntered(pin, can) }

    private suspend fun waitForPin(scanScreen: ScanScreen, lastPin: String) {
        var waitForEvents = true
        while (waitForEvents) {
            log("Wait for pin")
            val message = waitForSdkResult<EidMessage>()
            waitForEvents = false
            when (message) {
                is EnterPinEvent -> {
                    log("Pin invalid")
                    screen = if (message.requireCan) {
                        canInfoScreen
                    } else {
                        EnterPinScreen(
                            attemptsLeft = message.card.retryCounter,
                            onPinEntered = { pin -> pinEntered(pin) },
                            onChangePinClicked = { restartProcess(cancelWorkflow = true, inPinChangeMode = true) },
                            lastPin = lastPin
                        )
                    }
                }
                is EidResultOk -> {
                    log(message.toString())
                    waitForIdentStatusCompletion()
                    currentStatus = CurrentStatus.SUCCESS
                    screen = SuccessScreen(BasicCaseManager.getCaseresponse()?.modules?.identStatus, onCloseClicked = { activity.finishEidWithSuccess() })
                }
                is InsertCardEvent -> {
                    waitForEvents = true
                }
                is ReadEvent -> {
                    waitForEvents = true
                    activity.setIsReadingNfc(message.card != null)
                    scanScreen.showOverlay(message.card != null)
                }
                else -> { /* ignore */ }
            }
        }
        log("wait for pin end")
    }

    private suspend fun waitForIdentStatusCompletion() {
        var counter = 0
        repeat(10) {
            delay(1000)
            try {
                ++counter
                val status = emmiService.getCaseInformationByPath("/cases/${caseResponse.caseId}/eidIdent").caseStatus
                log("retry #$it: ${status.toString()}")

                when (status?.subStatusCode) {
                    SubStatusCodeDTO.OTHER_SUB_STATUS -> throw EidError.OTHER_SUB_STATUS.exception()
                    SubStatusCodeDTO.NOT_SUPPORTED -> throw EidError.CARD_NOT_SUPPORTED.exception()
                    SubStatusCodeDTO.EXPIRED -> throw EidError.CARD_EXPIRED.exception()
                    null -> {}
                }

                when (status?.statusCode) {
                    StatusCodeDTO.ABGESCHLOSSEN -> return
                    StatusCodeDTO.ABGEWIESEN, StatusCodeDTO.ABGELEHNT -> {
                        handleCallbackUrl()
                        return
                    } // Success
                    else -> { /* next loop, case is still in progress */ }
                }
            } catch (err: Throwable) {
                if (err is CaseResponseError && err.resultCode == SdkResultCodes.ERROR_CASE_DONE) {
                    log("case gone!")
                    return // Success, HTTP_GONE -> case is closed
                } else if (counter >= 10 && err is HttpException) {
                    log("error during polling: ", err)
                    throw GENERAL_ERROR_BACKEND.exception(err.code.toString())
                } else {
                    log("error during polling: $err, retrying (${counter})")
                }
            }
        }
        throw STATUS_POLLING_RETRIES_EXCEEDED.exception()
    }

    private suspend fun handleCallbackUrl() {
        try {
            val callbackUrl = emmiService.getCaseInformationByPath("/cases/${caseId}/eidident").modules.identStatusUpdate?.callBackUrl
            activity.setCallbackUrl(callbackUrl)
        } catch (e: Throwable) {
            log("error getting callbackUrl")
        }
    }

    private val transportCanInfoScreen = CanInfoScreen(onContinue = { screen = enterTransportCanScreen }, onBackClicked = { activity.finish() })
    private val enterTransportCanScreen = EnterTransportPinScreen(canRequired = true,
        onPinCanEntered = { pin, can ->
            pinCanEntered(pin, requireNotNull(can), true)
        },
        onGoToEnterPinClicked = {
            restartProcess(cancelWorkflow = true, inPinChangeMode = true)
        }
    )

    private fun transportPinEntered(transportPin: String, can: String?) {
        screen = EnterNewPinScreen(transportPin = transportPin, can = can, onNewPinEntered = { newTransportPin, newPin, newCan ->
            screen = RepeatNewPinScreen(newTransportPin, newPin, newCan) { repeatTransportPin, repeatNewPin, repeatCan ->
                newPinEntered(repeatTransportPin, repeatNewPin, repeatCan) }
        })
    }

    private suspend fun waitForTransportPin(scanScreen: ScanScreen): Boolean {
        var waitForEvents = true
        var success = false
        while (waitForEvents) {
            log("Wait for pin")
            val message = waitForSdkResult<EidMessage>()
            waitForEvents = false
            when (message) {
                is EnterPinEvent -> {
                    log("Pin invalid")
                    screen = if (message.requireCan) {
                        transportCanInfoScreen
                    } else {
                        EnterTransportPinScreen(
                            canRequired = false,
                            attemptsLeft = message.card.retryCounter,
                            onPinCanEntered = { pin, can ->
                                transportPinEntered(pin, can)
                            },
                            onGoToEnterPinClicked = {
                                restartProcess(cancelWorkflow = true, inPinChangeMode = false)
                            }
                        )
                    }
                }
                is EnterNewPinEvent -> {
                    log("received new pin event")
                    success = true
                }
                is InsertCardEvent -> {
                    waitForEvents = true
                }
                is ReadEvent -> {
                    waitForEvents = true
                    // show overlay when card is detected and hide overlay when card is gone
                    activity.setIsReadingNfc(message.card != null)
                    scanScreen.showOverlay(message.card != null)
                }
                else -> { /* ignore */ }
            }
        }
        log("wait for transport pin end")
        return success
    }

    private fun pinCanEntered(pin: String, can: String, isPinChange: Boolean = false) {
        screen = ScanInfoScreen(if (isPinChange) ScanScreen.IdentContext.PIN_CHANGE else ScanScreen.IdentContext.IDENT) {
            pinCanEnteredScan(pin, can, isPinChange) }
    }

    private fun pinCanEnteredScan(pin: String, can: String, isPinChange: Boolean = false) {
        launch {
            try {
                log("pinEntered wait for EnterPinEvent")
                var scanScreen: ScanScreen? = null
                scanScreen = ScanScreen(identContext = if (isPinChange) ScanScreen.IdentContext.PIN_CHANGE else ScanScreen.IdentContext.IDENT,
                    onHelpClicked = {
                        screen = HelpScreen(activity = activity, caseResponse = getCaseResponse(),
                            onRetryClicked = { screen = scanScreen },
                            onBackClicked = { activity.finish() }, attemptId)
                    })
                screen = scanScreen

                if (isPinChange) {
                    screen = EnterNewPinScreen(transportPin = pin, can = can, onNewPinEntered = { newTransportPin, newPin, newCan ->
                        screen = RepeatNewPinScreen(newTransportPin, newPin, newCan) { repeatTransportPin, repeatNewPin, repeatCan ->
                            newPinEntered(repeatTransportPin, repeatNewPin, repeatCan) }
                    })
                } else {
                    val resultCan = sendCommand<EnterPinEvent>(EidCommandDto(cmd = EidCommandDto.CommandType.SET_CAN, value = can))
                    activity.setIsReadingNfc(true)
                    scanScreen.showOverlay(true)

                    if (resultCan.requireCan) {
                        screen = if (isPinChange) transportCanInfoScreen else canInfoScreen
                        return@launch
                    }

                    emmiReporter.send(logEvent = LogEvent.EI_PIN_CHECK, attemptId = Commons.attemptId)
                    sendCommand<EidResultOk>(EidCommandDto(cmd = EidCommandDto.CommandType.SET_PIN, value = pin))
                    waitForIdentStatusCompletion()
                    currentStatus = CurrentStatus.SUCCESS
                    screen = SuccessScreen(identStatus = getCaseResponse().modules.identStatus, onCloseClicked = { activity.finishEidWithSuccess() })
                }
            } catch (err: Throwable) {
                handleError(err)
            }
        }
    }

    private fun newPinEntered(transportPin: String, newPin: String, can: String?) {
        screen = ScanInfoScreen(ScanScreen.IdentContext.PIN_CHANGE) { newPinEnteredScan(transportPin, newPin, can) }
    }

    private fun newPinEnteredScan(transportPin: String, newPin: String, can: String?) {
        launch {
            try {
                var scanScreen: ScanScreen? = null
                scanScreen = ScanScreen(identContext = ScanScreen.IdentContext.PIN_CHANGE,
                    onHelpClicked = {
                        screen = HelpScreen(activity = activity, caseResponse = getCaseResponse(),
                            onRetryClicked = { screen = scanScreen },
                            onBackClicked = { activity.finish() }, attemptId)
                    })
                screen = scanScreen

                emmiReporter.send(
                    logEvent = LogEvent.EI_CHANGE_PIN_CLICKED,
                    attemptId = Commons.attemptId
                )

                if (can != null) {
                    val resultCan = sendCommand<EnterPinEvent>(EidCommandDto(
                        cmd = EidCommandDto.CommandType.SET_CAN,
                        value = if (simulatorAvailable) null else can)
                    )
                    activity.setIsReadingNfc(true)
                    scanScreen.showOverlay(true)

                    if (resultCan.requireCan) {
                        screen = transportCanInfoScreen
                        return@launch
                    }
                }

                val lastPinEvent = enterPinEventReceived
                val enterPinEvent = lastPinEvent ?: waitForSdkResult()
                if (lastPinEvent == null) {
                    activity.setIsReadingNfc(true)
                    scanScreen.showOverlay(true)
                }

                if (enterPinEvent.requireCan) {
                    screen = transportCanInfoScreen
                    log("Start can process")
                    return@launch
                }

                log("PinEnterEvent received. Send pin")
                sendCommandAsync(EidCommandDto(cmd = EidCommandDto.CommandType.SET_PIN, value = if (simulatorAvailable) null else transportPin))
                val transportPinSuccess = waitForTransportPin(scanScreen)

                if (transportPinSuccess.not()) { return@launch }

                val result = sendCommand<ChangePinResultEvent>(EidCommandDto(
                    EidCommandDto.CommandType.SET_NEW_PIN,
                    value = if (simulatorAvailable) null else newPin
                ), tagDetected = {
                    activity.setIsReadingNfc(it)
                    scanScreen.showOverlay(it)
                })

                emmiReporter.send(
                    logEvent = LogEvent.EI_CHANGE_PIN_RESULT,
                    eventContext = mapOf(
                        "success" to result.success.toString()
                    ),
                    attemptId = Commons.attemptId
                )

                if (result.success) {
                    wasPinChanged = true
                    screen = SuccessScreen(getCaseResponse().modules.identStatus, true, onCloseClicked = {
                        restartProcess(cancelWorkflow = false, inPinChangeMode = false)
                    })
                } else {
                    handleError(EidError.PIN_CHANGE_ERROR.exception())
                }
            } catch (err: Throwable) {
                handleError(err)
            }
        }
    }

    private fun handleError(throwable: Throwable) {
        log(throwable)
        currentStatus = CurrentStatus.ERROR
        val eidException = if (throwable is EidException) throwable else EidError.TECHNICAL_ERROR.exception()
        screen = ErrorScreen(activity = activity, caseResponse = getCaseResponse(), eidException = eidException,
            onRetryClicked = { restartIdent() },
            onBackClicked = { activity.finish() }, attemptId
        )

        if (eidException.logAsError) {
            attemptData.errorCodeIdentifier = eidException.errorCodeId.toString()
            attemptData.errorCode = eidException.errorCodeName
            attemptData.errorSource = eidException.errorSource
        }

        launch(Dispatchers.IO) {
            patchAttempt()
        }
    }

    suspend fun updateNfcTag(tag: Tag) {
        ausweisApp2Sdk.updateNfcTag(tag)
    }

    private suspend fun startAttempt() {
        val connTypeAdapter = Moshi.Builder().build().adapter(ConnectionTypeDTO::class.java)
        val connTypeJson = connTypeAdapter.toJson(ConnectionTypeDTO(getNetworkConnectionType(activity)))

        val userFrontendInformation = UserFrontendInformationDTO(additionalData = connTypeJson)
        val attempt = CoreEmmiService.sendCreateAttempt(userFrontendInformation, requireNotNull(Commons.caseId), "eidIdent")
        attemptId = attempt.attemptId
        Commons.attemptId = attemptId
    }

    private suspend fun patchAttempt() {
        if (Commons.caseId != null && attemptId != null && attemptData.isNotEmpty()) {
            try {
                CoreEmmiService.sendAttemptDataEid(Commons.caseId!!, attemptId!!, attemptData)
            } catch (err: Throwable) {
                log(err)
            } finally {
                attemptData = EidAttemptDataDTO() // reset the data whether it was sent successfully or not (fire & forget)
            }
        }
    }

    private suspend fun sendCommandAsync(cmd: EidCommandDto) {
        ausweisApp2Sdk.send(cmd)
    }

    private fun sendGetStatus() {
        launch { ausweisApp2Sdk.send(EidCommandDto(cmd = EidCommandDto.CommandType.GET_STATUS)) }
    }

    private fun insertSimulatedCard() = launch {
        sendCommand<EidMessage>(EidCommandDto(
            EidCommandDto.CommandType.SET_CARD,
            name = "Simulator"
        ))
        log("card inserted into simulator")
    }

    private suspend inline fun <reified T : EidMessage> sendCommand(cmd: EidCommandDto, noinline tagDetected: ((Boolean) -> Unit)? = null) = waitForSdkResult<T>(tagDetected) {
        if (cmd.cmd == EidCommandDto.CommandType.SET_PIN) {
            enterPinEventReceived = null
            log("EnterPinEventReceived: $enterPinEventReceived")
        }
        ausweisApp2Sdk.send(cmd)
    }

    private fun receiveMessage(event: EidMessage) {

        log("##### RECEIVE MESSAGE: $event")

        launch {
            when (event) {
                is InsertCardEvent -> {
                    if (CoreConfig.enableSimEid
                        && event.error == null
                        && simulatorAvailable) insertSimulatedCard()
                    _eidMessage.sendEvent(event)
                }
                is EidAuthEvent -> {
                    if (event.url?.isNotBlank() == true && event.result.minor != EidResultDto.EidResultMinor.PROCESS_CANCELLED) {
                        callRedirectUrl(event.url)
                    }

                    val result = if (event.result.major == EidResultDto.EidResultMajor.RESULT_ERROR) {
                        EidResultError(event.result)
                    } else {
                        attemptData.userFrontendPollingStartTimestamp = System.currentTimeMillis()
                        EidResultOk
                    }

                    if (event.result.major in listOf(
                            EidResultDto.EidResultMajor.RESULT_WARNING,
                            EidResultDto.EidResultMajor.RESULT_NEXT_REQUEST,
                            EidResultDto.EidResultMajor.UNKNOWN
                    )) {
                        emmiReporter.send(LogEvent.EI_MAJOR_UNHANDLED, message = "Governikus returned a major result which currently is not being handled by the SDK. Major: ${event.result.majorRaw} Minor: ${event.result.minorRaw}")
                    }

                    val eventContext = mapOf(
                        "success" to (result is EidResultOk).toString(),
                        "resultMajor" to event.result.majorRaw,
                        "resultMinor" to (event.result.minorRaw ?: "")
                    )
                    emmiReporter.send(logEvent = LogEvent.EI_PIN_RESULT, eventContext = eventContext, attemptId = Commons.attemptId)

                    if (event.result.minor != EidResultDto.EidResultMinor.PROCESS_CANCELLED) {
                        attemptData.userFrontendResultMajor = event.result.majorRaw
                        attemptData.userFrontendResultMinor = event.result.minorRaw
                        patchAttempt()
                    }

                    _eidMessage.sendEvent(result)
                }
                is EnterPinEvent -> {
                    enterPinEventReceived = event
                    attemptData.setPinAttemptsLeftCount(event.card.retryCounter)
                    log("EnterPinEventReceived: $enterPinEventReceived")
                    _eidMessage.sendEvent(event)
                }
                is ReadEvent -> {
                    if (event.card?.deactivated == true) {
                        handleError(EidError.CARD_DEACTIVATED.exception())
                        return@launch
                    } else if (event.card?.retryCounter == 0) {
                        log("retry counter is 0!")
                        handleError(EidError.PIN_STATUS_BLOCKED.exception())
                        return@launch
                    }
                    simulatorAvailable = event.simulated
                    if (simulatorAvailable) log("eID simulator available")
                    _eidMessage.sendEvent(event)
                }
                else -> _eidMessage.sendEvent(event)
            }
        }
    }

    private suspend fun callRedirectUrl(url: String?) {
        url?.let {
            try {
                CoreEmmiService.restApi.get().url(it).execute()
            } catch (err: Exception) {
                log("error calling redirect URL", err)
            }
        }
    }

    private suspend inline fun <reified T : EidMessage> waitForSdkResult(noinline tagDetected: ((Boolean) -> Unit)? = null, noinline sdkCommand: (suspend () -> Unit)? = null): T {
        var observer: ((EidMessage) -> Unit)? = null
        try {
            return suspendCancellableCoroutine { cont ->
                observer = { state ->
                    when (state) {
                        is BadStateEvent -> {
                            cont.resumeWithException(EidError.BAD_STATE.exception())
                        }
                        is EidResultError -> {
                            log(state.result.toString())
                            val error = when (state.result.minor) {
                                EidResultDto.EidResultMinor.CARD_REMOVED -> EidError.CARD_READ_CONNECTION_LOST
                                EidResultDto.EidResultMinor.PROCESS_CANCELLED -> EidError.CARD_READ_CONNECTION_LOST
                                EidResultDto.EidResultMinor.INTERNAL_ERROR -> EidError.INTERNAL_ERROR
                                EidResultDto.EidResultMinor.TRUSTED_CHANNEL_ESTABLISHMENT_FAILED -> EidError.TRUSTED_CHANNEL_FAILURE
                                EidResultDto.EidResultMinor.PARAMETER_ERROR -> EidError.PARAMETER_ERROR
                                EidResultDto.EidResultMinor.WRONG_API_ERROR -> EidError.CARD_NOT_SUPPORTED
                                EidResultDto.EidResultMinor.VALIDITY_VERIFICATION_FAILED -> EidError.CARD_EXPIRED
//                                EidResultDto.EidResultMinor.COMMUNICATION_ERROR -> EidError.GENERAL_ERROR_BACKEND
                                EidResultDto.EidResultMinor.NO_PERMISSION -> EidError.AUTHENTICATION_ERROR
                                EidResultDto.EidResultMinor.UNKNOWN_ERROR -> EidError.INTERNAL_ERROR
                                else -> EidError.RESULT_MINOR_ERROR
                            }
                            cont.resumeWithException(error.exception())
                        }
                        is EidErrorMessage -> {
                            cont.resumeWithException(state.error)
                        }
                        is T -> { // order is important (errors above, other messages below!)
                            if (cont.isActive) {
                                cont.resume(state)
                            }
                        }
                        is ReadEvent -> {
                            if (cont.isActive) {
                                tagDetected?.invoke(state.card != null)
                            }
                        }
                        else -> { /* ignore, we do not wait for the event here */ }
                    }
                }
                launch {
                    eidMessage.subscribe(requireNotNull(observer))
                    sdkCommand?.invoke()

                    //currently we do not want the read to time out
                    /*delay(DEFAULT_TIMEOUT_MS)
                    eidMessage.unsubscribe(observer)
                    cont.resumeWithException(EidTimeoutException())*/
                }
            }
        } finally {
            log("suspendCoroutine finished")
            observer?.let { eidMessage.unsubscribe(it) }
        }
    }

    fun enableNfcDispatcher() {
        ausweisApp2Sdk.enableNfcDispatcher()
    }

    fun disableNfcDispatcher() {
        ausweisApp2Sdk.disableNfcDispatcher()
    }

    fun destroy() {
        ausweisApp2Sdk.unbind()
        EidManager.destroy()
    }
}