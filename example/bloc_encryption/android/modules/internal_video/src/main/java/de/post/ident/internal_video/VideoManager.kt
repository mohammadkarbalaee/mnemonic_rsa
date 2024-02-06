package de.post.ident.internal_video

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.squareup.moshi.Moshi
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.camera.CameraWrapper
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.util.ConnectionTypeDTO
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.getNetworkConnectionType
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.ui.EventBus
import de.post.ident.internal_core.util.ui.EventBusSender
import de.post.ident.internal_video.databinding.PiVideoContainerBinding
import de.post.ident.internal_video.rest.*
import de.post.ident.internal_video.ui.*
import de.post.ident.internal_video.util.EmmiVideoReporter
import de.post.ident.internal_video.webrtc.ImageData
import de.post.ident.internal_video.webrtc.WebRTCManager
import kotlinx.coroutines.*
import org.webrtc.FlashlightCallback
import org.webrtc.SurfaceViewRenderer
import org.webrtc.WebRTCCameraSession
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.util.LinkedList
import java.util.Queue
import kotlin.reflect.KClass

@Keep
enum class VideoState(val progress: Int, val fragmentClass: KClass<out Fragment>, val canHangup: Boolean = false, val canOpenChat: Boolean = false) {
    MAKEUP_ROOM(-1, MakeupRoomFragment::class),
    WAITING_FOR_AGENT(-1, WaitingRoomFragment::class, canHangup = true),
    INCOMING_CALL(0, IncomingCallFragment::class, canHangup = true, canOpenChat = true),
    DECLARATION_OF_CONSENT(12, CaptureDataFragment::class, canHangup = true, canOpenChat = true),
    CAPTURE_SCREENSHOT_IDCARD_FRONT(24, CaptureDataFragment::class, canHangup = true, canOpenChat = true),
    CAPTURE_SCREENSHOT_IDCARD_BACK(36, CaptureDataFragment::class, canHangup = true, canOpenChat = true),
    CAPTURE_SCREENSHOT_OF_FACE(48, CaptureDataFragment::class, canHangup = true, canOpenChat = true),
    GRAB_IDCARD_NUMBER(60, VerifyUserDataFragment::class, canHangup = true, canOpenChat = true),
    FILL_IDCARD_DATA(72, VerifyUserDataFragment::class, canHangup = true, canOpenChat = true),
    SEND_TAN(84, EnterTanFragment::class, canHangup = true, canOpenChat = true),
    END_CHAT(100, GoodbyeFragment::class, canHangup = true, canOpenChat = true),

    // chat failed / succeeded
    CANCELED_VERIFY_PROCESS(-1, VideochatAbortedFragment::class),
    CALL_ENDED_BY_ERROR(-1, VideochatAbortedFragment::class),
    CALL_ENDED_BY_AGENT(-1, VideochatAbortedFragment::class),
    CALL_ENDED_BY_USER(-1, VideochatAbortedFragment::class),
    CALL_ENDED_SUCCESSFULLY(-1, VideochatSuccessFragment::class)
}

sealed class NovomindEvent {
    data class ChatMessage(val msg: String, val isAgent: Boolean) : NovomindEvent()
    data class WorkflowUserData(val userData: ChatChangeMessageDTO.WorkflowUserDataDTO) : NovomindEvent()
    data class TanResult(val result: Boolean) : NovomindEvent()
    data class WaitingTime(val timeSeconds: Int) : NovomindEvent()
    object NoAgentAvailable : NovomindEvent()
    object TimeoutInQueue : NovomindEvent()
}

class VideoManager private constructor(val caseResponse: CaseResponseDTO) : CoroutineScope by MainScope() {
    companion object {
        private var instance: VideoManager? = null
        fun init(caseResponse: CaseResponseDTO) {
            instance = VideoManager(caseResponse)
        }

        fun isInitiated() = instance != null

        fun destroy() {
            instance?.endCall(true)
            instance = null
        }

        fun instance() = checkNotNull(instance)
    }

    private val context = CoreConfig.appContext

    private val emmiService = CoreEmmiService
    private val novomindRestService = NovomindRestService
    private val emmiReporter = EmmiVideoReporter(this)
    private var novomindManager: NovomindChatManager =
            if (CoreConfig.appConfig.enableWebSockets) WebSocketManager(emmiReporter) else PollManager(emmiReporter)
    lateinit var webRTCManager: WebRTCManager
    lateinit var uiController: VideoChatUiController

    private val _currentState = BackgroundCachingMutableLiveData<VideoState>()
    val currentState: LiveData<VideoState> get() = _currentState

    var waitingTimeInfo: String = ""
    var waitingTime: Int = 0

    var agentName: String? = null
    var abortReason: AbortReason? = null

    private val _isChatOverlayVisible = MutableLiveData(false)
    val isChatOverlayVisible: LiveData<Boolean> get() = _isChatOverlayVisible

    private val _novomindEventBus = EventBusSender<NovomindEvent>()
    val novomindEventBus: EventBus<NovomindEvent> = _novomindEventBus

    internal var attemptId: String? = null
    internal var launchId: String? = null
    internal val novomindChatId: Int get() = novomindManager.chatId

    private var videoChatService: VideoChatService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            videoChatService = (service as VideoChatService.VideoChatServiceBinder).service
        }
        override fun onServiceDisconnected(arg0: ComponentName) {}
    }

    private val videoQualitySettings = VideoQualityDTO(320, 15, 640, 480)

    init {
        initNovomindManager()
        initWebRTCManager()
        _currentState.observeForever { state ->
            if (state != VideoState.WAITING_FOR_AGENT) {
                videoChatService?.cancelBackgroundQueueHandling()
            }
        }
    }

    private fun initNovomindManager() {
        novomindManager.subscribe { event ->
            log("Event received: $event")
            when (event.type) {
                ChatChangeTypeDTO.ChatChangeType.ChatChangeInitNack -> {
                    // call was disconnected unexpectedly
                    notifyConnectionClosed((event.message as ChatChangeMessageDTO.Default).message.toInt())
                }
                ChatChangeTypeDTO.ChatChangeType.ChatChangeChatstep -> {
                    if (event.message is ChatChangeMessageDTO.Default) {
                        if (event.chatstepType == ChatChangeTypeDTO.ChatStepType.CHAT_MESSAGE) {
                            val message = HtmlCompat.fromHtml(event.message.message, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                            log("received chat message: $message")
                            _novomindEventBus.sendEvent(NovomindEvent.ChatMessage(message, true))
                        } else if (event.chatstepType == ChatChangeTypeDTO.ChatStepType.TIMEOUT_IN_QUEUE ||
                                event.chatstepType == ChatChangeTypeDTO.ChatStepType.CLOSED_BROWSER ||
                                event.chatstepType == ChatChangeTypeDTO.ChatStepType.AGENT_BLOCKED_USER) {
                            notifyConnectionClosed(event.chatstepType)
                        }
                    }
                }
                ChatChangeTypeDTO.ChatChangeType.ChatChangeMetainformation -> {
                    when (event.message) {
                        is ChatChangeMessageDTO.WorkflowUpdateAgentNameDTO -> {
                            agentName = event.message.workflowUpdateAgentName
                            log("Agent name: $agentName")
                            emmiReporter.send(LogEvent.VC_AGENT_NAME)
                        }
                        is ChatChangeMessageDTO.WorkflowStateDTO -> handleState(event.message.workflowActiveState)
                        is ChatChangeMessageDTO.WaitingLineDTO -> {
                            log("Waiting time seconds: ${event.message.waitingLine.timeSeconds}")
                            _novomindEventBus.sendEvent(NovomindEvent.WaitingTime(event.message.waitingLine.timeSeconds))
                            emmiReporter.send(LogEvent.VC_WAITING_TIME, eventContext = mapOf("waitingTimeSeconds" to event.message.waitingLine.timeSeconds.toString()))
                        }
                        is ChatChangeMessageDTO.WorkflowUserDataDTO -> {
                            log("User data: ${event.message}")
                            _novomindEventBus.sendEvent(NovomindEvent.WorkflowUserData(event.message))
                        }
                        is ChatChangeMessageDTO.WorkflowTanResult -> {
                            log("TAN correct: ${event.message.correctTan}")
                            emmiReporter.send(LogEvent.VC_TAN_CHECK, eventContext = mapOf("isTanValid" to event.message.correctTan.toString()))
                            _novomindEventBus.sendEvent(NovomindEvent.TanResult(event.message.correctTan))
                        }
                        is ChatChangeMessageDTO.WorkflowTanTransmitted -> {
                            log("TAN transmitted")
                            emmiReporter.send(LogEvent.VC_TAN_TRANSMITTED)
                        }
                        is ChatChangeMessageDTO.WorkflowTakeScreenshot -> {
                            val simulated = event.message.simulated
                            log("Take screenshot - simulated: $simulated")

                            takePicture(simulated)
                        }
                        is ChatChangeMessageDTO.WorkflowDocumentExtracted -> {
                            log("crop document: ${event.message.crop}")
//                            imageCapturer.cropCurrentDocument = event.message.crop //TODO crop not implemented yet
                        }
                        is ChatChangeMessageDTO.WorkflowCancelledProcess -> {
                            if (event.message.workflowCancelledReason != null) abortReason = AbortReason.valueOf(event.message.workflowCancelledReason) else AbortReason.ENDED_BY_AGENT
                            log("cancelled process")
                            endCall()
                        }
                        is ChatChangeMessageDTO.WorkflowSigningRedirectToken -> {
                            log("received signing redirect token: ${event.message.signingRedirectToken}")
                            emmiReporter.send(LogEvent.VC_REDIRECT_TOKEN)
                            Commons.signingRedirectToken = event.message.signingRedirectToken
                        }
                        else -> log("Unhandled message: ${event.message}")
                    }
                }
                ChatChangeTypeDTO.ChatChangeType.WebRtcChange -> {
                    if (event.data != null) {
                        webRTCManager.handleWebRTCEvent(event.data)
                    }
                }
                ChatChangeTypeDTO.ChatChangeType.ChatChangeStopPolling,
                ChatChangeTypeDTO.ChatChangeType.ChatChangeStop -> {
                    // call was disconnected unexpectedly
                    notifyConnectionClosed(0)
                }
                ChatChangeTypeDTO.ChatChangeType.WebSocketStoppedNoFallback -> endCall()
                ChatChangeTypeDTO.ChatChangeType.WebSocketStopped -> {
                    emmiReporter.send(LogEvent.VC_WEBSOCKET_FALLBACK)

                    // save values before switching manager
                    val chatId = novomindManager.chatId
                    val chatToken = novomindManager.token

                    novomindManager = PollManager(emmiReporter)
                    initNovomindManager()
                    novomindManager.start(chatId, chatToken)
                }
                else -> {
                    // ignore event
                }
            }
        }
    }

    private fun initWebRTCManager() {
        webRTCManager = WebRTCManager(
            context.applicationContext,
            novomindManager,
            novomindRestService,
            videoQualitySettings
        )
    }

    fun initVideoViews(
        localVideoView: SurfaceViewRenderer,
        remoteVideoView: SurfaceViewRenderer
    ) {
        webRTCManager.initSurfaceViews(localVideoView, remoteVideoView)
        webRTCManager.client?.initLocalVideoCapture()
    }

    fun createUiController() = VideoChatUiController(context, this).also { uiController = it }

    private fun notifyConnectionClosed(type: Int?) {
        when (type) {
            ChatChangeTypeDTO.ChatStepType.TIMEOUT_IN_QUEUE -> _novomindEventBus.sendEvent(NovomindEvent.TimeoutInQueue)

            ChatChangeTypeDTO.ChatStepType.NO_AGENT_AVAILABLE,
            ChatChangeTypeDTO.ChatStepType.CALLCENTER_CLOSED -> _novomindEventBus.sendEvent(NovomindEvent.NoAgentAvailable)

            ChatChangeTypeDTO.ChatStepType.NO_CHAT_PERMISSION,
            ChatChangeTypeDTO.ChatStepType.CLOSED_BROWSER,
            ChatChangeTypeDTO.ChatStepType.AGENT_BLOCKED_USER -> endCall()

            else -> endCall() //TODO check for websocket fallback (NovomindRestImpl:notifyConnectionClosed())
        }
    }

    private fun takePicture(simulated: Boolean) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val takePictureCallback = object: WebRTCCameraSession.TakePictureCallback {
                    var cameraProperties: String = ""
                    var imageProperties: String = ""

                    override fun onPictureTaken(imageData: ImageData) {
                        log("picture taken: ${imageData.width}x${imageData.height} @ ${imageData.data.size} bytes")
                        imageData.tags[ImageData.TAG_CAMERA_PROPS] = cameraProperties
                        imageData.tags[ImageData.TAG_IMG_PROPS] = imageProperties
                        scope.launch {
                            processPicture(imageData)
                        }
                    }

                    override fun onPictureFailed(errorMsg: String) {
                        log(errorMsg)
                        scope.launch {
                            log("taking picture failed, using screen grab as fallback")
                            webRTCManager.grabFrame()?.let { imageData ->
                                imageData.tags[ImageData.TAG_ERROR_MSG] = errorMsg
                                imageData.tags[ImageData.TAG_CAMERA_PROPS] = cameraProperties
                                imageData.tags[ImageData.TAG_IMG_PROPS] = imageProperties
                                processPicture(imageData)
                            }
                        }
                    }

                    override fun onCameraProperties(cameraProperties: String) {
                        this.cameraProperties = cameraProperties
                    }

                    override fun onImageProperties(imageProperties: String) {
                        this.imageProperties = imageProperties
                    }
                }
                uiController.animateScreenshot()
                if (simulated) {
                    log("screenshot was simulated")
                } else {
                    webRTCManager.takePicture(takePictureCallback)
                }
            } catch (e: Throwable) {
                log("picture capture failed!", e)
            }
        }
    }

    private suspend fun processPicture(imageData: ImageData?) {
        log("process screenshot")
        imageData?.let { image ->
            log("pause video")
            webRTCManager.client?.stopLocalVideoCapture()
            val timeout = async {
                // resume stream in case upload takes longer than 5 seconds
                delay(5000)
                log("resume video")
                webRTCManager.client?.startLocalVideoCapture()
            }
            timeout.await()
            try {
                log("upload screenshot")
                val timeStampStartUpload = System.currentTimeMillis()
                novomindRestService.uploadScreenshot(
                    image.data,
                    novomindManager.chatId,
                    novomindManager.token
                )
                val eventContext = mutableMapOf(
                    "fileSizeBytes" to image.data.size.toString(),
                    "width" to image.width.toString(),
                    "height" to image.height.toString(),
                    "simulated" to false.toString(),
                    "documentExtracted" to image.cropCurrentDocument.toString(),
                    "isScreenGrab" to image.isScreenGrab.toString(),
                    "uploadTime" to (System.currentTimeMillis() - timeStampStartUpload).toString()
                )
                image.tags[ImageData.TAG_ERROR_MSG]?.let { eventContext["errorMsg"] = it }
                image.tags[ImageData.TAG_IMG_PROPS]?.let { eventContext["imageProperties"] = it }
                image.tags[ImageData.TAG_CAMERA_PROPS]?.let { eventContext["cameraProperties"] = it }
                emmiReporter.send(
                    logEvent = LogEvent.PHOTO_QUALITY,
                    eventContext = eventContext
                )
                emmiReporter.flush()
            } catch (e: Throwable) {
                e.printStackTrace()
                log("screenshot upload failed")
            } finally {
                timeout.cancelAndJoin()
            }
            log("screenshot processing done")
        } ?: run {
            log("screenshot was empty")
            emmiReporter.send(LogEvent.PHOTO_QUALITY)
        }
    }

    private fun handleState(state: ChatChangeMessageDTO.WorkflowState) {
        when (state) {
            ChatChangeMessageDTO.WorkflowState.INCOMING_CALL -> {
                _currentState.value = VideoState.INCOMING_CALL
                _novomindEventBus.sendEvent(NovomindEvent.WaitingTime(0))
                emmiReporter.send(LogEvent.VC_CONNECT)
                startWebRtcConnection()
            }
            ChatChangeMessageDTO.WorkflowState.DECLARATION_OF_CONSENT -> {
                _currentState.value = VideoState.DECLARATION_OF_CONSENT
            }
            ChatChangeMessageDTO.WorkflowState.CAPTURE_SCREENSHOT_IDCARD_FRONT -> {
                _currentState.value = VideoState.CAPTURE_SCREENSHOT_IDCARD_FRONT
            }
            ChatChangeMessageDTO.WorkflowState.CAPTURE_SCREENSHOT_IDCARD_BACK -> {
                _currentState.value = VideoState.CAPTURE_SCREENSHOT_IDCARD_BACK
            }
            ChatChangeMessageDTO.WorkflowState.CAPTURE_SCREENSHOT_OF_FACE -> {
                _currentState.value = VideoState.CAPTURE_SCREENSHOT_OF_FACE
            }
            ChatChangeMessageDTO.WorkflowState.GRAB_IDCARD_NUMBER -> {
                _currentState.value = VideoState.GRAB_IDCARD_NUMBER
            }
            ChatChangeMessageDTO.WorkflowState.FILL_IDCARD_DATA -> {
                _currentState.value = VideoState.FILL_IDCARD_DATA
            }
            ChatChangeMessageDTO.WorkflowState.SEND_TAN -> {
                _currentState.value = VideoState.SEND_TAN
            }
            ChatChangeMessageDTO.WorkflowState.END_CHAT -> {
                _currentState.value = VideoState.END_CHAT
            }
            ChatChangeMessageDTO.WorkflowState.CANCELED_VERIFY_PROCESS -> {
                _currentState.value = VideoState.CANCELED_VERIFY_PROCESS
                endCall()
            }
            else -> log("Unhandled state: $state")
        }
    }

    private fun startWebRtcConnection() {
        launch {
            try {
                if (webRTCManager.client?.isCapturing == false) webRTCManager.client?.startLocalVideoCapture()
                emmiReporter.send(LogEvent.VC_WEBRTC_CONNECT)
                novomindRestService.webRtcSendConnect(novomindManager.chatId, novomindManager.token, ConnectDataDTO(resolution = "low"))
                novomindRestService.sendCanTakeScreenshots(novomindManager.chatId, novomindManager.token)
            } catch (err: Throwable) {
                log("sending connect events failed", err)
                endCall()
            }
        }
    }

    suspend fun sendTan(tan: String) = novomindRestService.sendTan(novomindManager.chatId, novomindManager.token, tan)

    suspend fun startAttempt() {
        val appConfig = CoreConfig.appConfig
        val featureSet = listOf(
                "pauseStreamWhileUpload:${appConfig.pauseStreamWhileUpload}",
                "enableWebSockets:${appConfig.enableWebSockets}",
                "bandwidthAdaptation:${appConfig.enableBandwidthAdaptation}",
                "selfServicePhoto:${caseResponse.modules.selfServicePhoto?.isValid() ?: false}",
                "usa:${caseResponse.modules.userSelfAssessment?.isValid() ?: false}"
        )

        val connTypeAdapter = Moshi.Builder().build().adapter(ConnectionTypeDTO::class.java)
        val connTypeJson = connTypeAdapter.toJson(ConnectionTypeDTO(getNetworkConnectionType(context)))

        val userFrontendInformation = UserFrontendInformationDTO(userFrontendFeatureSet = featureSet, additionalData = connTypeJson)

        val attempt = emmiService.sendCreateAttempt(userFrontendInformation, requireNotNull(Commons.caseId), "videoIdent")
        attemptId = attempt.attemptId
        Commons.attemptId = attemptId

        emmiService.sendAttemptDataMakeupRoom(requireNotNull(Commons.caseId), requireNotNull(attemptId),
                MakeupRoomDataDTO(makeupRoomStart = System.currentTimeMillis()))

        _currentState.value = VideoState.MAKEUP_ROOM
    }

    suspend fun prepareCall(selectedLanguageCode: String) {
        val serviceCenterInfo = emmiService.getServiceCenterInfo(caseResponse.callcenterCategory, Commons.caseId)

        if (serviceCenterInfo.status.not() || serviceCenterInfo.isInBusinessTime.not()) {
            throw NoAgentAvailableError(LocalizedStrings.getString("dialog_no_agent_available"))
        }

        emmiService.sendAttemptDataMakeupRoom(requireNotNull(Commons.caseId), requireNotNull(attemptId),
                MakeupRoomDataDTO(
                        preferredUserLanguage = selectedLanguageCode,
                        userHasCalled = true,
                        userHasCalledTimestamp = System.currentTimeMillis(),
                        makeupRoomEnd = System.currentTimeMillis()
                ))

        val result = emmiService.getChatLaunchId(checkNotNull(Commons.caseId), checkNotNull(attemptId))
        launchId = result.chatLaunchId

        emmiReporter.send(logEvent = LogEvent.VC_LAUNCH_ID, eventStatus = EventStatus.SUCCESS, message = "chatLaunchId received")

        waitingTimeInfo = serviceCenterInfo.waitingTimeInfo ?: ""
        _novomindEventBus.sendEvent(NovomindEvent.WaitingTime(-1))

        startCall(selectedLanguageCode)

        _currentState.value = VideoState.WAITING_FOR_AGENT
        startService()
    }

    private fun startService() {
        VideoChatService.start(context)
        Intent(context, VideoChatService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun stopService() {
        try {
            context.unbindService(connection)
        } catch (e: java.lang.Exception) {
            log("unbinding of VideoChatService failed")
        }
        VideoChatService.stop(context)
    }

    fun onAppInBackground() {
        if (_currentState.value == VideoState.WAITING_FOR_AGENT) {
            videoChatService?.startBackgroundQueueHandling()
        }
    }

    fun onAppInForeground() {
        if (_currentState.value == VideoState.WAITING_FOR_AGENT) {
            videoChatService?.cancelBackgroundQueueHandling()
        }
    }

    private suspend fun startCall(selectedLanguageCode: String) {
        val chatRequestInfo = ChatRequestInfoDTO(
                bcId = caseResponse.bcId,
                bcName = caseResponse.bcName,
                chatLaunchId = launchId,
        )
        val postData = ChatRequestDataDTO(
                nickname = if (CoreConfig.isSdk) "Android-SDK" else "Android-App",
                category = caseResponse.callcenterCategory ?: "",
                language = selectedLanguageCode,
                channel = "VIDEO",
                info = chatRequestInfo
        )
        val chatResponse = novomindRestService.requestChatId(postData)

        log("chat: $chatResponse")
        novomindManager.start(chatResponse.chatId, chatResponse.token)
    }

    fun endCall(userCancelled: Boolean = false) {
        if (novomindManager.isRunning) {
            launch {
                try {
                    if (novomindManager.token.isNotEmpty()) {
                        emmiReporter.send(LogEvent.VC_WEBRTC_DISCONNECT)
                        novomindRestService.stop(novomindManager.chatId, novomindManager.token)
                    }
                } catch (err: Throwable) {
                    log("sending stop event failed", err)
                }
            }
            novomindManager.stop()
        }

        if (abortReason != null) {
            _currentState.postValue(VideoState.CALL_ENDED_BY_ERROR)
        } else if (currentState.value == VideoState.END_CHAT) {
                _currentState.postValue(VideoState.CALL_ENDED_SUCCESSFULLY)
            } else if (userCancelled) {
                    _currentState.postValue(VideoState.CALL_ENDED_BY_USER)
                } else {
                    _currentState.postValue(VideoState.CALL_ENDED_BY_AGENT)
                }


        agentName = null
        waitingTimeInfo = ""

        val eventContext = mapOf("serverState" to (currentState.value?.name ?: ""))
        emmiReporter.send(LogEvent.VC_DISCONNECT, eventContext = eventContext)
        emmiReporter.flush()

        webRTCManager.endCall()
        stopService()
    }

    suspend fun appPaused(paused: Boolean) {
        if (paused) {
            webRTCManager.client?.disposeLocalVideoCapture()
        } else {
            webRTCManager.client?.initLocalVideoCapture()
        }
        _currentState.appInBackground.value = paused
        if (novomindManager.isRunning) {
            try {
                if (_currentState.value == VideoState.WAITING_FOR_AGENT) {
                    emmiReporter.send(LogEvent.VC_WAITING_ROOM_BACKGROUND, eventContext = mapOf(
                        "background" to paused.toString(),
                        "displayedWaitingtime" to waitingTime.toString(),
                        "chatId" to novomindManager.chatId.toString()))
                    emmiReporter.flush()
                } else {
                    emmiReporter.send(LogEvent.VC_LOCAL_STREAM_MUTING, eventContext = mapOf("appPause" to paused.toString()))
                    emmiReporter.flush()
                }

                novomindRestService.sendAppPause(novomindManager.chatId, novomindManager.token, paused)
            } catch (err: Throwable) {
                log("sending app pause event failed", err)
            }
        }
    }

    fun toggleCamera(binding: PiVideoContainerBinding) {
        if (webRTCManager.client?.isCapturing == true) {
            binding.videoPowerSwitchCam.setImageResource(R.drawable.pi_ic_camera_on)
            binding.videoSwitchCam.isVisible = false
            binding.videocontainerUser.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.pi_black))
            webRTCManager.client?.stopLocalVideoCapture()
        } else {
            webRTCManager.client?.startLocalVideoCapture()
            binding.videoPowerSwitchCam.setImageResource(R.drawable.pi_ic_camera_off)
            binding.videoSwitchCam.isVisible = true
            binding.videocontainerUser.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.pi_transparent))
        }
    }

    fun setSpeakerActive(speakerActive: Boolean) {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            isSpeakerphoneOn = speakerActive
            mode = AudioManager.MODE_IN_COMMUNICATION
        }
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(speakerActive)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(speakerActive)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(speakerActive)
    }

    fun switchCamera() = webRTCManager.client?.switchCamera()
    fun toggleFlashlight() = webRTCManager.client?.toggleFlashlight()
    fun getFlashlightState() = webRTCManager.client?.getFlashlightState()
    fun registerFlashlightCallback(callback: FlashlightCallback) = webRTCManager.client?.registerFlashlightCallback(callback)

    /**
     * @see [ATARI-27420](https://tasktracker.clear-mail.de/tracker/browse/ATARI-27420)
     * @see [ATARI-29828](https://tasktracker.clear-mail.de/tracker/browse/ATARI-29828)
     *
     * @param timeSeconds waiting time in seconds from interface
     * @return Int containing 'formatted' waiting time in _minutes_
     */
    fun convertWaitingTimeToMinutes(timeSeconds: Int): Int {
        waitingTime = when (timeSeconds) {
            in 0 .. 60 -> 1
            in 61 .. 300 -> timeSeconds / 60
            in 301 .. 600 -> 10
            in 601 .. 900 -> 15
            in 901 .. 1200 -> 20
            in 1201 .. 1500 -> 25
            else -> 30
        }
        return waitingTime
    }

    // Move to Activity (Maybe to companion class) because is just UI no business logic
    fun showChatOverlay(isVisible: Boolean) {
        if (isVisible) {
            emmiReporter.send(LogEvent.VC_TEXT_CHAT)
        }
        _isChatOverlayVisible.value = isVisible
    }

    suspend fun sendZipMessage(msg: String) {
        novomindRestService.sendChatMessage(novomindManager.chatId, novomindManager.token, msg)
        _novomindEventBus.sendEvent(NovomindEvent.ChatMessage(msg, false))
        emmiReporter.send(LogEvent.VC_USER_MESSAGE)
    }

    suspend fun uploadSelfServicePhotos(pathTemplate: String, savedImages: List<CoreEmmiService.FileUploadItem>) {
        val path = pathTemplate.replace("{ATTEMPT_ID}", checkNotNull(attemptId))
        emmiService.uploadPhoto(path, savedImages)
    }

    suspend fun uploadSelfServicePhotoCrop(image: CameraWrapper.ResultImage): SelfServicePhotoCropResponseDto {
        return emmiService.uploadPhotoCrop(checkNotNull(Commons.caseId), checkNotNull(attemptId), image.jpegDataArray)
    }
}

class BackgroundCachingMutableLiveData<T> : MutableLiveData<T>() {
    private val cachedValuesToSet: Queue<T?> = LinkedList()
    private val cachedValuesToPost: Queue<T?> = LinkedList()
    var appInBackground = MutableLiveData(false)

    init {
        appInBackground.observeForever { inBackground ->
            if (inBackground.not()) {
                while (appInBackground.value == false) postValue(cachedValuesToPost.poll() ?: break)
                while (appInBackground.value == false) setValue(cachedValuesToSet.poll() ?: break)
            }
        }
    }

    override fun postValue(value: T?) {
        if (appInBackground.value == true) {
            cachedValuesToPost.add(value)
        } else {
            super.postValue(value)
        }
    }

    override fun setValue(value: T?) {
        if (appInBackground.value == true) {
            cachedValuesToSet.add(value)
        } else {
            super.setValue(value)
        }
    }
}