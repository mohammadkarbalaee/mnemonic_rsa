package de.post.ident.internal_autoid

import androidx.annotation.Keep
import com.google.protobuf.Any
import com.google.protobuf.Internal
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.MessageLite
import de.deutschepost.pi.mlservices.protobuf.*
import de.post.ident.internal_autoid.rest.DelphiRestService
import de.post.ident.internal_autoid.ui.FaceDirection
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.camera.CameraWrapper
import de.post.ident.internal_core.reporting.AutoIdErrorCode
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.util.log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Exception
import kotlin.Int
import kotlin.OptIn
import kotlin.String
import kotlin.Throwable
import kotlin.checkNotNull
import kotlin.to


// ML-Services WebSocket API - https://confluence.clear-mail.de/wiki/display/ATARI/ML-Services+WebSocket+API#MLServicesWebSocketAPI-WSVersionv2.0(AutoIdent)
class WebsocketManager(
    val caseId: String,
    val attemptId: String,
    val autoId: String,
    var wsListener: WsListener? = null
    ) {

    private var websocket: WebSocket? = null
    private var isRunning = true
    private lateinit var cameraWrapper: CameraWrapper
    private lateinit var act: AutoIdentActivity
    private lateinit var referenceImage: ByteArray
    private lateinit var nextImage: ByteArray
    private var isImageReady: Boolean = false
    private val urlBase = "type.googleapis.com/websocket."
    private val WEBSOCKET_OK = 1000

    var frameCount: Int = 0
        private set

    private val emmiReporter: EmmiAutoIdReporter = EmmiAutoIdReporter

    private var instance: WebsocketManager? = null
    fun init(act: AutoIdentActivity, cameraWrapper: CameraWrapper, referenceImage: ByteArray) {
        this.act = act
        this.cameraWrapper = cameraWrapper
        this.referenceImage = referenceImage
        instance = WebsocketManager(caseId, attemptId, autoId)
        websocket = createWebSocket(webSocketListener)
    }

    fun destroy() {
        instance = null
    }

    fun instance() = checkNotNull(instance)

    private val webSocketListener: WebSocketListener = object : WebSocketListener() {

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            isRunning = false
            destroy()
            log("onClosed websocket closing $reason $code")
            if (code != WEBSOCKET_OK) showErrorScreen()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            isRunning = true
            log("websocket is running ${isRunning}")
            log(response.toString())
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)

            val response = CommandResponse.parseFrom(bytes.toByteArray())
            log("websocket response: ${response.data.typeUrl}")

            when (response.data.typeUrl) {
                Command.DVF_RESPONSE.id -> {
                    log("DvfCommandResponse" + response.data.unpack(DvfCommandResponse::class.java).toString())
                    val image = takePhoto()
                    log("DvfAddImage size in byte ${image?.size}")

                    val commandDvf = DvfAddImageCommandMessage.newBuilder()
                        .setImage(com.google.protobuf.ByteString.copyFrom(image))
                        .build()

                    val commandWrapperDvf = wrapCommand(commandDvf)
                    websocket?.send(commandWrapperDvf.toByteString().toByteArray().toByteString())
                    frameCount++
                }

                Command.DVF_FINISHED.id -> {
                    val responseData = response.data.unpack(DvfFinishedResponse::class.java)
                    log("DvfFinishedResponse :${Command.DVF_FINISHED.id} ${responseData.isComplete}")
                    if (cameraWrapper.isFlashlightOn()) cameraWrapper.toggleFlashlight()
                    if (responseData.isComplete) stopDvFRun() else showErrorScreen()
                }

                Command.FVLC_CHALLENGE.id -> {
                    val unpack = response.data.unpack(FvLcChallengeResponse::class.java)
                    calculateFaceDirection(unpack)
                    waitForFrameGrab()
                    val image = nextImage
                    grabFrame(act.currentFaceDirection.value)
                    log("image size: ${nextImage.size}")

                    val command = FvLcSetCandidateCommandMessage.newBuilder()
                        .setImage(com.google.protobuf.ByteString.copyFrom(image))
                        .build()
                    val commandWrapper = wrapCommand(command)
                    websocket?.send(commandWrapper.toByteString().toByteArray().toByteString())
                    frameCount++
                }

                Command.FVLC_FINISHED.id -> {
                    val responseData = response.data.unpack(FvLcFinishedResponse::class.java)
                    log("FvLcFinishedResponse :${Command.FVLC_FINISHED.id} $responseData")
                    act.vibratePhone()
                    if (responseData.isComplete) stopFvLcRun() else showErrorScreen()
                }
                else -> log("else: unreadable response from ml server")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            log("onMessage text delphi $text")
        }

        override fun equals(other: kotlin.Any?): Boolean {
            log("onMessage text equals $other")
            return super.equals(other)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            isRunning = false
            destroy()
            log("onMessage onClosing $webSocket, $code, $reason")
            if (code != WEBSOCKET_OK) showErrorScreen()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            destroy()
            log("onFailure websocket: is running: $isRunning message: ${response?.message} ${t.message}")
            wsListener?.onWsFailure()
            if (isRunning) showErrorScreen()
            isRunning = false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showErrorScreen() {
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                act.showErrorScreen(AutoIdErrorCode.WS_CONNECTION_PROBLEM)
            }
        }
    }

    fun wrapCommand(command: MessageLite): CommandWrapper {
        log("wrapCommand ${urlBase + command.javaClass.simpleName}")
        return CommandWrapper.newBuilder()
            .setMajorVersion(2)
            .setMinorVersion(0)
            .setCommand((Any.newBuilder()
                    .setTypeUrl(urlBase + command.javaClass.simpleName)
                    .setValue(command.toByteString())))
            .build()
    }

    private fun calculateFaceDirection(unpack: FvLcChallengeResponse) {
        when  {
            unpack.tiltChallenge.name == "UP" && unpack.panChallenge.name == "CENTER_PAN"  -> {updateFaceDirection(FaceDirection.TOP)}
            unpack.tiltChallenge.name == "CENTER_TILT" && unpack.panChallenge.name == "RIGHT"  -> {updateFaceDirection(FaceDirection.RIGHT)}
            unpack.tiltChallenge.name == "CENTER_TILT" && unpack.panChallenge.name == "LEFT"  -> {updateFaceDirection(FaceDirection.LEFT)}
            unpack.tiltChallenge.name == "CENTER_TILT" && unpack.panChallenge.name == "CENTER_PAN"  -> {updateFaceDirection(FaceDirection.CENTER)}
            unpack.tiltChallenge.name == "DOWN" && unpack.panChallenge.name == "CENTER_PAN"  -> {updateFaceDirection(FaceDirection.DOWN)}
        }
        log("${Command.FVLC_CHALLENGE.id}: " + unpack)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateFaceDirection(faceDirection: FaceDirection) {
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                act.updateFaceDirection(faceDirection)
            }
        }
    }

    private fun createWebSocket(listener: WebSocketListener): WebSocket {
        val targetUrl = "wss://" + CoreConfig.serverConfig.mlServerUrl + "ml-services/public/ws/v2.0/"
        val request: Request = Request.Builder().url(targetUrl).build()
        return DelphiRestService.restApi.newWebSocket(request, listener)
    }

    fun startDvfRun(dvf: DvfNextRunResponseDTO, autoIdentDocument: AutoIdentDocumentDTO) {
        val refImage = getReferenceImage()
        val command = DvfSetReferenceCommandMessage.newBuilder()
            .setAttemptId(attemptId.toLong())
            .setIdentId(autoId.toLong())
            .setCaseId(caseId)
            .setDocCodePdc(autoIdentDocument.codePdc)
            .setDvfReferenceImageMediaRecordId(dvf.referenceImageMediaRecordId.toLong())
            .setDvfSequenceMediaRecordId(dvf.sequenceMediaRecordId.toLong())
            .setDvfReferenceImage(com.google.protobuf.ByteString.copyFrom(refImage))
            .build()

        val commandWrapper = wrapCommand(command)
        act.setKeepScreenOn(true)

        websocket?.send(commandWrapper.toByteString().toByteArray().toByteString())
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun stopDvFRun() {
        isRunning = false
        log("websocket is running ${isRunning}")
        val flashLightOn = cameraWrapper.isFlashlightOn()
        if (flashLightOn) cameraWrapper.toggleFlashlight()
        GlobalScope.launch {
            emmiReporter.send(
                logEvent = LogEvent.AUTOIDENT_DVF_SUCCESS,
                iterationCount = act.getStepIteration(),
                eventContext = mapOf("flashLightOn" to flashLightOn.toString()),
                attemptId = attemptId,
                flush = true,
                eventStatus = EventStatus.SUCCESS
            )
            withContext(Dispatchers.Main) {
                act.showStepSuccessAndContinue(AutoIdentActivity.Screen.FVLC)
            }
        }
        act.setKeepScreenOn(false)
        cancel("DVF run was finished")
    }

    fun cancel(reason: String) {
        isRunning = false
        wsListener = null
        websocket?.close(1000, "Client: $reason was reason for cancelled websocket conntection")
        instance?.destroy()
    }

    fun startFvLcRun(fvlcrun: FvlcNextRunResponseDTO) {
        grabFrame()
        val refImage = getReferenceImage()
        val command = FvLcSetReferenceCommandMessage.newBuilder()
            .setAttemptId(attemptId.toLong())
            .setIdentId(autoId.toLong())
            .setCaseId(caseId)
            .setReferenceImageMediaRecordId(fvlcrun.referenceImageMediaRecordId.toLong())
            .setSequenceMediaRecordId(fvlcrun.sequenceMediaRecordId.toLong())
            .setReferenceImage(com.google.protobuf.ByteString.copyFrom(refImage))
            .build()

        val commandWrapper = wrapCommand(command)
        act.setKeepScreenOn(true)

        log("wrapCommand ${urlBase + command.javaClass.simpleName}")
        websocket?.send(commandWrapper.toByteString().toByteArray().toByteString())
    }

    private fun getReferenceImage(): ByteArray {
        return if (BuildConfig.CAMERA_MOCK && de.post.ident.internal_core.BuildConfig.DEBUG) {
            cameraWrapper.getReferenceImageMock()
        } else referenceImage
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun stopFvLcRun() {
        isRunning = false
        log("websocket is running: ${isRunning}")
        GlobalScope.launch {
            emmiReporter.send(
                logEvent = LogEvent.AUTOIDENT_FVLC_SUCCESS,
                iterationCount = act.getStepIteration(),
                flush = true,
                attemptId = attemptId,
                eventStatus = EventStatus.SUCCESS
            )
            withContext(Dispatchers.IO) {
                act.sendMpFinish(caseId, attemptId) //TODO check if in correct place
            }
        }
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                act.updateScreen(AutoIdentActivity.Screen.LOADING)
            }
        }
        act.setKeepScreenOn(false)
        cancel("FVLC run was finished")
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun takePhoto() : ByteArray? {
        isImageReady= false
        var image: ByteArray? = null
        try {
            GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    image = cameraWrapper.getDvfImage(useCameraMock = BuildConfig.CAMERA_MOCK, currentIdentStep = act.getCurrentIdentStep().name)
                    isImageReady = true
                }
            }
            while (!isImageReady) {
                Thread.sleep(10)
            }

        } catch (e: Exception) {
            nextImage = referenceImage
        }
        return image
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun grabFrame(faceDirection: FaceDirection? = FaceDirection.CENTER) {
        isImageReady= false
        try {
            GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    nextImage = cameraWrapper.getFrameGrab(useCameraMock = BuildConfig.CAMERA_MOCK, faceDirection = faceDirection?.name, act.getCurrentIdentStep().name)
                    isImageReady = true
                }
            }
        } catch (e: Exception) {
            log("exception while grabbing frames")
            nextImage = referenceImage
        }
    }

    fun waitForFrameGrab() {
        while (!isImageReady) {
            Thread.sleep(1)
        }
    }

    interface WsListener {
        fun onWsFailure()
    }

    @Keep
    enum class Command(val id: String) {
        FVLC_CHALLENGE("type.googleapis.com/websocket.FvLcChallengeResponse"),
        FVLC_FINISHED("type.googleapis.com/websocket.FvLcFinishedResponse"),
        DVF_FINISHED("type.googleapis.com/websocket.DvfFinishedResponse"),
        DVF_RESPONSE("type.googleapis.com/websocket.DvfCommandResponse"),
    }

    @Throws(InvalidProtocolBufferException::class)
    fun <T : MessageLite> Any.unpack(clazz: Class<T>): T {
        val defaultInstance = Internal.getDefaultInstance(clazz)
        @Suppress("UNCHECKED_CAST")
        return defaultInstance.parserForType.parseFrom(value) as T
    }
}