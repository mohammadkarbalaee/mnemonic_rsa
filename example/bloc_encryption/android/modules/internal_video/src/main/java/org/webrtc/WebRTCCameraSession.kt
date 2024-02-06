package org.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Range
import android.util.Size
import android.view.Surface
import de.post.ident.internal_core.camera.CameraImageUtils
import de.post.ident.internal_core.util.log
import de.post.ident.internal_video.webrtc.ImageData
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import java.util.concurrent.TimeUnit

class WebRTCCameraSession(
    val applicationContext: Context,
    val cameraManager: CameraManager,
    val surfaceTextureHelper: SurfaceTextureHelper,
    val cameraId: String,
    val width: Int,
    val height: Int,
    val framerate: Int,
    val eventHandler: EventHandler,
    val createSessionCallback: CreateSessionCallback
) : CameraSession {
    private lateinit var cameraDevice: CameraDevice
    private var captureSession: CameraCaptureSession? = null
    private lateinit var previewSurface: Surface
    private lateinit var cameraCharacteristics: CameraCharacteristics
    private var cameraOrientation: Int = 0
    private var isFrontFacing: Boolean = true
    private var fpsUnitFactor = 0
    private lateinit var captureFormat: CaptureFormat
    private val cameraThreadHandler: Handler = Handler()

    private val camera2ResolutionHistogram: Histogram = Histogram.createEnumeration(
        "WebRTC.Android.Camera2.Resolution",
        CameraEnumerationAndroid.COMMON_RESOLUTIONS.size
    )
    private val camera2StartTimeMsHistogram: Histogram = Histogram.createCounts(
        "WebRTC.Android.Camera2.StartTimeMs",
        1,
        10000,
        50
    )
    private val camera2StopTimeMsHistogram: Histogram = Histogram.createCounts(
        "WebRTC.Android.Camera2.StopTimeMs",
        1,
        10000,
        50
    )
    private var firstFrameReported = false
    private val constructionTimeNs: Long = System.nanoTime()

    private val frameOrientation: Int
        get() {
            var rotation = CameraSession.getDeviceOrientation(applicationContext)
            if (!isFrontFacing) {
                rotation = 360 - rotation
            }
            return (cameraOrientation + rotation) % 360
        }

    var isRunning = false
        private set

    private val stillPictureSize = Size(1200, 900)
    private val imageReader = ImageReader.newInstance(
        stillPictureSize.width,
        stillPictureSize.height,
        ImageFormat.YUV_420_888,
        1
    ).apply {
        setOnImageAvailableListener({ imageReader ->
            try {
                val image = imageReader?.acquireLatestImage()
                log("got image: ${image?.width}x${image?.height}")
                image?.let { img ->
                    takePictureCallback?.onImageProperties(CameraImageUtils.getImageProperties(img))
                    CameraImageUtils.imageToByteArray(img)?.let { bytes ->
                        takePictureCallback?.onPictureTaken(
                            ImageData(
                                bytes,
                                image.width,
                                image.height
                            )
                        )
                    } ?: run {
                        takePictureCallback?.onPictureFailed("bytes were null")
                    }
                } ?: run {
                    takePictureCallback?.onPictureFailed("image was null")
                }
                image?.close()
            } catch (e: Throwable) {
                takePictureCallback?.onPictureFailed("processing of image from imageReader failed: ${e.message}")
            }
        }, cameraThreadHandler)
    }
    private var takePictureCallback: TakePictureCallback? = null

    private val captureSessionCallback = object: CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            this@WebRTCCameraSession.captureSession = session
            try {
                val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    set(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(
                            captureFormat.framerate.min / fpsUnitFactor,
                            captureFormat.framerate.max / fpsUnitFactor
                        )
                    )
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.CONTROL_AE_LOCK, false)
                    WebRTCCameraSessionHelper.chooseStabilizationMode(this, cameraCharacteristics)
                    WebRTCCameraSessionHelper.chooseFocusMode(this, cameraCharacteristics)
                    addTarget(previewSurface)
                }

                session.setRepeatingRequest(
                    captureRequestBuilder.build(),
                    null,
                    cameraThreadHandler
                )
            } catch (e: Exception) {
                log("failed to start capture request: $e")
                return
            }

            surfaceTextureHelper.startListening { frame ->
                if (isRunning) {
                    if (!firstFrameReported) {
                        firstFrameReported = true
                        camera2StartTimeMsHistogram.addSample(
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - constructionTimeNs).toInt()
                        )
                    }
                    val modifiedFrame = VideoFrame(
                        CameraSession.createTextureBufferWithModifiedTransformMatrix(
                            frame.buffer as TextureBufferImpl,
                            isFrontFacing,
                            -cameraOrientation
                        ),
                        frameOrientation,
                        frame.timestampNs
                    )
                    eventHandler.onFrameCaptured(this@WebRTCCameraSession, modifiedFrame)
                    modifiedFrame.release()
                } else {
                    log("camera not running")
                }
            }

            createSessionCallback.onDone(this@WebRTCCameraSession)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            log("onConfigureFailed")
            session.close()
        }
    }

    private val cameraStateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            log("opened device id:${camera.id} with capture format:${captureFormat.width}x${captureFormat.height}")
            cameraDevice = camera
            surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height)
            previewSurface = Surface(surfaceTextureHelper.surfaceTexture)
            try {
                camera.createCaptureSession(
                    arrayListOf(
                        previewSurface,
                        imageReader.surface
                    ),
                    captureSessionCallback,
                    cameraThreadHandler
                )
            } catch (e: CameraAccessException) {
                log("failed to create capture session: $e")
            }
        }

        override fun onDisconnected(p0: CameraDevice) {
            log("disconnected device id:${p0.id}")
            stop()
        }

        override fun onError(cameraDevice: CameraDevice, errorCode: Int) {
                val errorMsg = when (errorCode) {
                    ERROR_CAMERA_IN_USE -> "camera device is already in use."
                    ERROR_MAX_CAMERAS_IN_USE -> "camera device could not be opened because there are too many other open camera devices."
                    ERROR_CAMERA_DISABLED -> "camera device could not be opened due to a device policy."
                    ERROR_CAMERA_DEVICE -> "camera device has encountered a fatal error."
                    ERROR_CAMERA_SERVICE -> "camera service has encountered a fatal error."
                    else -> "unknown camera error: $errorCode"
                }
            log("onError device id:${cameraDevice.id} error:$errorMsg")
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            log("closed device id:${camera.id}")
        }
    }

    init {
        start()
    }

    private fun start() {
        log("start camera session")
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        cameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) as Int
        isFrontFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        findCaptureFormat()
        openCamera()
    }

    private fun findCaptureFormat() {
        val fpsRanges: Array<Range<Int>> = cameraCharacteristics.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        ) as Array<Range<Int>>
        fpsUnitFactor = WebRTCCameraSessionHelper.getFpsUnitFactor(fpsRanges)
        log("fps unit factor $fpsUnitFactor")
        val framerateRanges = WebRTCCameraSessionHelper.convertFramerates(fpsRanges, fpsUnitFactor)
        val sizes = WebRTCCameraSessionHelper.getSupportedSizes(cameraCharacteristics)
        log("available preview sizes: $sizes")
        log("available fps ranges: $framerateRanges")
        if (framerateRanges.isEmpty().not() && !sizes.isEmpty()) {
            val bestFpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(
                framerateRanges,
                framerate
            )
            val bestSize = CameraEnumerationAndroid.getClosestSupportedSize(sizes, width, height)
            log("bestSize $bestSize")
            CameraEnumerationAndroid.reportCameraResolution(camera2ResolutionHistogram, bestSize)
            captureFormat = CaptureFormat(bestSize.width, bestSize.height, bestFpsRange)
            log("using capture format: $captureFormat")
        } else {
            log("no supported capture formats.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            cameraManager.openCamera(
                cameraId,
                cameraStateCallback,
                cameraThreadHandler
            )
            isRunning = true
        } catch (e: CameraAccessException) {
            log("failed to open camera: $e")
        }
    }

    override fun stop() {
        if (isRunning) {
            log("stop camera session")
            val stopStartTime = System.nanoTime()
            isRunning = false
            surfaceTextureHelper.stopListening()
            captureSession?.close()
            captureSession = null
            previewSurface.release()
            imageReader.surface.release()
            imageReader.close()
            cameraDevice.close()
            camera2StopTimeMsHistogram.addSample(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime).toInt()
            )
        }
    }

    fun getCameraProperties(context: Context) : String {
        val enumerator = WebRTCCameraEnumerator(context)
        return StringBuilder().apply {
            append("usedCamera:$cameraId, ")
            append("cameras:${enumerator.getCameraIds().size}, ")
            append("lensDirection:${if (enumerator.isBackFacing(cameraId)) "back" else "front"}")
        }.toString()
    }

    fun takePicture(takePictureCallback: TakePictureCallback) {
        this.takePictureCallback = takePictureCallback
        cameraThreadHandler.post {
            log("start still capture")
            try {
                takePictureCallback.onCameraProperties(getCameraProperties(applicationContext))
                val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.JPEG_ORIENTATION, cameraOrientation)
                    addTarget(imageReader.surface)
                }

                val stillCaptureCallback = object: CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        super.onCaptureFailed(session, request, failure)
                        takePictureCallback.onPictureFailed("still capture failed")
                    }
                }

                captureSession?.capture(
                    captureRequestBuilder.build(),
                    stillCaptureCallback,
                    cameraThreadHandler
                )
            } catch (e: Throwable) {
                takePictureCallback.onPictureFailed("still capture request failed: ${e.message}")
            }
        }
    }

    fun setFlashlightState(flashlightOn: Boolean) {
        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            set(CaptureRequest.FLASH_MODE, if (flashlightOn) CaptureRequest.FLASH_MODE_OFF else CaptureRequest.FLASH_MODE_TORCH)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            addTarget(previewSurface)
            captureSession?.setRepeatingRequest(this.build(), null, null)
        }
    }

    interface CreateSessionCallback {
        fun onDone(session: WebRTCCameraSession)
        fun onFailure(msg: String)
    }

    interface EventHandler {
        fun onFrameCaptured(session: WebRTCCameraSession, frame: VideoFrame)
    }

    interface TakePictureCallback {
        fun onPictureTaken(imageData: ImageData)
        fun onPictureFailed(errorMsg: String)
        fun onCameraProperties(cameraProperties: String)
        fun onImageProperties(imageProperties: String)
    }
}