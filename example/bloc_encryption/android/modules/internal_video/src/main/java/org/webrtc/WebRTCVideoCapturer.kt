package org.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import androidx.annotation.Keep
import de.post.ident.internal_core.util.log
import org.webrtc.CameraVideoCapturer.CameraSwitchHandler

@Keep
enum class FlashlightState {
    UNAVAILABLE, ON, OFF
}

class WebRTCVideoCapturer(
    val context: Context,
    private var usesFrontCamera: Boolean = true
) : VideoCapturer {

    private var applicationContext: Context? = null
    private var capturerObserver: CapturerObserver? = null
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper
    private var cameraThreadHandler: Handler? = null
    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var currentSession: WebRTCCameraSession? = null
    private val cameraEnumerator: WebRTCCameraEnumerator = WebRTCCameraEnumerator(context)
    private var cameraSwitchEventHandler: CameraSwitchHandler? = null

    private var cameraId = cameraEnumerator.getCameraId(usesFrontCamera)

    private var flashlightState: FlashlightState = FlashlightState.OFF
    var flashlightCallback: FlashlightCallback? = null

    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var captureFramerate: Int = 0

    var isCapturing: Boolean = false
        get() = currentSession?.isRunning ?: false
        private set

    private val eventHandler = object: WebRTCCameraSession.EventHandler {
        override fun onFrameCaptured(session: WebRTCCameraSession, frame: VideoFrame) {
            capturerObserver?.onFrameCaptured(frame)
        }
    }

    private val createSessionCallback = object: WebRTCCameraSession.CreateSessionCallback {
        override fun onDone(session: WebRTCCameraSession) {
            currentSession = session
            flashlightCallback?.onFlashlightStateChanged(getFlashlightState())
            cameraSwitchEventHandler?.onCameraSwitchDone(cameraEnumerator.isFrontFacing(cameraId))
        }
        
        override fun onFailure(msg: String) {
            log("session creation failed")
            cameraSwitchEventHandler?.onCameraSwitchError(msg)
        }
    }

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        applicationContext: Context,
        capturerObserver: CapturerObserver?
    ) {
        this.applicationContext = applicationContext
        this.capturerObserver = capturerObserver
        this.surfaceTextureHelper = surfaceTextureHelper
        this.cameraThreadHandler = surfaceTextureHelper.handler
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        applicationContext?.let {
            this.captureWidth = width
            this.captureHeight = height
            this.captureFramerate = framerate
            createSession()
        } ?: run {
            throw RuntimeException("must call initialize() first")
        }
    }

    fun startCapture() {
        if (captureWidth == 0 || captureHeight == 0 || captureFramerate == 0) {
            throw IllegalStateException("video capture must be started with properties first")
        }
        startCapture(captureWidth, captureHeight, captureFramerate)
    }

    private fun createSession(delay: Long = 0L) {
        cameraThreadHandler?.postDelayed({
            WebRTCCameraSession(
                context,
                cameraManager,
                surfaceTextureHelper,
                cameraId,
                captureWidth,
                captureHeight,
                captureFramerate,
                eventHandler,
                createSessionCallback
            )
        }, delay)
    }

    override fun stopCapture() {
        cameraThreadHandler?.post {
            currentSession?.stop()
            currentSession = null
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        stopCapture()
        startCapture(width, height, framerate)
    }

    fun switchCamera(cameraSwitchHandler: CameraSwitchHandler?) {
        cameraThreadHandler?.post {
            this.cameraSwitchEventHandler = cameraSwitchHandler
            val newCameraId = cameraEnumerator.getCameraId(usesFrontCamera.not())
            if (newCameraId == cameraId) {
                cameraSwitchHandler?.onCameraSwitchError("no other camera to switch to")
            } else {
                usesFrontCamera = usesFrontCamera.not()
                cameraId = newCameraId
                currentSession?.stop()
                currentSession = null
                createSession()
            }
        }
    }

    fun toggleFlashlight() {
        if (flashlightState != FlashlightState.UNAVAILABLE) {
            currentSession?.setFlashlightState(flashlightState == FlashlightState.ON)
            //using this dirty approach for flashlight state since TorchCallback is useless in this scenario. Feel free to improve!
            flashlightState = if (flashlightState == FlashlightState.OFF) FlashlightState.ON else FlashlightState.OFF
            flashlightCallback?.onFlashlightStateChanged(flashlightState)
        }
    }

    fun getFlashlightState() : FlashlightState = if (flashlightAvailable()) flashlightState else FlashlightState.UNAVAILABLE
    private fun flashlightAvailable() = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

    override fun dispose() {
        surfaceTextureHelper.stopListening()
        surfaceTextureHelper.dispose()
    }

    override fun isScreencast() = false

    fun takePicture(takePictureCallback: WebRTCCameraSession.TakePictureCallback) {
        currentSession?.takePicture(takePictureCallback)
    }

}

interface FlashlightCallback {
    fun onFlashlightStateChanged(state: FlashlightState)
}
