package de.post.ident.internal_core.camera

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.os.Handler
import android.os.Looper
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.common.util.concurrent.ListenableFuture
import de.post.ident.internal_core.BuildConfig
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.util.log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraWrapper(private val fragment: Fragment, private val cameraView: PreviewView, private val cameraSettings: CameraSettings = defaultCameraSettings) {
    data class ResultImage(
        val bitmap: Bitmap,
        val jpegDataArray: ByteArray,
        var filename: String? = null) {
        protected fun finalize() {
            log("recycle bitmap")
            bitmap.recycle()
        }
    }

    data class CameraSettings(val cameraCaptureMode: Int, val width: Int, val height: Int)

    companion object {
        /**
         * max size of the jpeg image returned from takePicture()
         */
        val maxImageSizeInBytes = Commons.fotoSizeKb * 1024

        /**
         * Used to get the first sample size for reading data from camera.
         */
        val maxPixelWidth = Commons.fotoMaxWidth
        var defaultCameraSettings: CameraSettings = CameraSettings(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, 960, 1280)
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview

    init {
        log("init camera")
        cameraView.post {
            initCamera(cameraSettings)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initCamera(cameraSettings: CameraSettings) {
        try {
            cameraProviderFuture = ProcessCameraProvider.getInstance(fragment.requireContext())
        } catch (e: IllegalStateException) {
            fragment.activity?.finish()
        }

        cameraProviderFuture.addListener({
            log("listener callback")
            val rotation = cameraView.display?.rotation ?: 0
            log("display rotation: $rotation")
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .setDefaultResolution(android.util.Size(cameraSettings.width, cameraSettings.height))
                .setTargetRotation(rotation)
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(cameraSettings.cameraCaptureMode)
                .setTargetRotation(rotation)
                .setTargetResolution(android.util.Size(cameraSettings.width, cameraSettings.height))
                .build()

            videoCapture = VideoCapture.Builder().build()

            bindViewToLifecycle()

        }, ContextCompat.getMainExecutor(fragment.requireContext()))
    }

    private fun bindViewToLifecycle(isVideo: Boolean = false) {
        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera depending on use case
            if (isVideo) {
                cameraProvider.bindToLifecycle(fragment, cameraSelector, preview, videoCapture)
            } else {
                cameraProvider.bindToLifecycle(fragment, cameraSelector, preview, imageCapture)
            }
            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(cameraView.surfaceProvider)

        } catch (exc: Exception) {
            log("Use case binding failed", exc)
        }
    }

    suspend fun getDocCheckImage(useCameraMock: Boolean? = false, filename: String? = null): ResultImage {
        log("useCameraMock: $useCameraMock debugVersion: ${BuildConfig.DEBUG}")
        return if (useCameraMock == true && BuildConfig.DEBUG) CameraMock.getDocCheckImage() else takePictureAndResize(filename)
    }

    suspend fun getDvfImage(useCameraMock: Boolean? = false, currentIdentStep: String): ByteArray {
        log("useCameraMock: $useCameraMock debugVersion: ${BuildConfig.DEBUG}")
        return if (useCameraMock == true && BuildConfig.DEBUG) {
            CameraMock.getDvfImage(currentIdentStep)
        } else {
            try {
                val image = takePictureAndConvert()
                CameraImageUtils.convertBitmapToJpegByteArray(image)
            } catch (e: ImageCaptureException) {
                log("error getting dvf image: ${e.message}")
                ByteArray(0)
            }
        }
    }

    fun getReferenceImageMock() = CameraMock.getReferenceImage()

    suspend fun getFrameGrab(useCameraMock: Boolean? = false, faceDirection: String? = "center", currentIdentStep: String): ByteArray {
        log("faceDirection $faceDirection identStep: $currentIdentStep")
        return if (useCameraMock == true && BuildConfig.DEBUG) {
            CameraMock.getFrame(currentIdentStep, faceDirection)
        } else {
            try {
                val image = grabFrame()
                CameraImageUtils.convertBitmapToJpegByteArray(image)
            } catch (e: Throwable) {
                log("error getting frame: ${e.message}")
                ByteArray(0)
            }
        }
    }


    suspend fun takePictureAndResize(filename: String? = "") = suspendCoroutine<ResultImage> { cont ->
        val startTime = System.currentTimeMillis()
        log("try taking picture")
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
            override fun onCaptureSuccess(image: ImageProxy) {
                val endTime = System.currentTimeMillis()
                log("picture received. Time: ${endTime - startTime} ms")
                val img = checkNotNull(image.image)
                log("Image format: ${img.format}")
                val resultImage = logTime { CameraImageUtils.convertImageToBitmapAndResize(img, maxImageSizeInBytes, maxPixelWidth, image.imageInfo.rotationDegrees) }
                resultImage.filename = filename
                image.close()
                cont.resume(resultImage)
            }
            override fun onError(exception: ImageCaptureException) {
                log(exception)
                log("error taking photo")
                cont.resumeWithException(exception)
            }
        })
    }

    private suspend fun takePictureAndConvert() = suspendCoroutine<Bitmap> { cont ->
        val startTime = System.currentTimeMillis()
        log("try taking picture")
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
            override fun onCaptureSuccess(image: ImageProxy) {
                val endTime = System.currentTimeMillis()
                log("picture received. Time: ${endTime - startTime} ms")
                val img = checkNotNull(image.image)
                log("Image format: ${img.format}")
                val resultImage = logTime { CameraImageUtils.convertImageToBitmap(img, image.imageInfo.rotationDegrees) }
                image.close()
                cont.resume(resultImage)
            }
            override fun onError(exception: ImageCaptureException) {
                log(exception)
                log("error taking photo")
                cont.resumeWithException(exception)
            }
        })
    }

    suspend fun grabFrame() = suspendCoroutine<Bitmap> { cont ->
        log("try grabbing frame")
        logTime {
            val frame = cameraView.bitmap

            if (frame != null) {
                log("frame specs - width: ${frame.width} height: ${frame.height} byteCount: ${frame.byteCount}")
                cont.resume(frame)
            } else {
                log("error grabbing frame")
                cont.resumeWithException(Throwable("frame was null"))
            }
        }
    }

    @SuppressLint("RestrictedApi", "MissingPermission")
    fun startVideo(file: File, onVideoSaved: (File) -> Unit, onErrorListener: (Throwable) -> Unit) {
        bindViewToLifecycle(true)
        val fileOptions: VideoCapture.OutputFileOptions = VideoCapture.OutputFileOptions.Builder(file).build()
        videoCapture.startRecording(fileOptions, cameraExecutor, object : VideoCapture.OnVideoSavedCallback {
            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                log("saved video: ${outputFileResults.savedUri}")
                try {
                    onVideoSaved(file)
                } catch (error: Throwable) {
                    Handler(Looper.getMainLooper()).post {
                        onErrorListener(error)
                    }
                }
                file.delete()
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                log(message, cause)
                onErrorListener(cause ?: Throwable(message))
                file.delete()
            }
        })
    }

    @SuppressLint("RestrictedApi")
    fun stopVideo() {
        videoCapture.stopRecording()
    }

    fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        initCamera(cameraSettings)
    }

    fun selectCamera(isFrontCamera: Boolean) {
        cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        initCamera(cameraSettings)
    }

    @SuppressLint("RestrictedApi")
    fun toggleFlashlight() {
        if (imageCapture.camera?.cameraControl != null) {
            try {
                if (imageCapture.camera?.cameraInfo?.hasFlashUnit() == true) {
                    if (imageCapture.camera?.cameraInfo?.torchState?.value == TorchState.OFF) {
                        imageCapture.camera?.cameraControl?.enableTorch(true)
                    } else {
                        imageCapture.camera?.cameraControl?.enableTorch(false)
                    }
                }
            } catch (err: Throwable) {
                log("Toggling flashlight failed!", err)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun isFlashlightOn(): Boolean {
        if (imageCapture.camera?.cameraControl != null) {
            try {
                if (imageCapture.camera?.cameraInfo?.hasFlashUnit() == true) {
                    return imageCapture.camera?.cameraInfo?.torchState?.value != TorchState.OFF
                    }
            } catch (err: Throwable) {
                log("Toggling flashlight failed!", err)
            }
        }
        return false
    }
}

fun <T> logTime(block: () -> T): T {
    val startTime = System.currentTimeMillis()
    val rv = block()
    val stopTime = System.currentTimeMillis()
    log("Time: ${stopTime - startTime} ms")
    return rv
}

object CameraImageUtils {

    fun rotateAndCropBitmapRelative(image: CameraWrapper.ResultImage, x: Float, y: Float, width: Float, height: Float, rotation: Float): CameraWrapper.ResultImage {
        val bitmap = image.bitmap
        val resultBitmap = rotateAndCropBitmap(bitmap, x * bitmap.width.toFloat(), y * bitmap.height.toFloat(), width * bitmap.width.toFloat(), height * bitmap.height.toFloat(), rotation)
        val jpegArray = ByteArrayOutputStream()
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, Commons.fotoQuality, jpegArray)
        return CameraWrapper.ResultImage(resultBitmap, jpegArray.toByteArray())
    }

    fun convertBitmapToByteArray(bitmap: Bitmap) : ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        bitmap.recycle()
        return byteArray
    }

    private fun rotateAndCropBitmap(image: Bitmap, x: Float, y: Float, width: Float, height: Float, rotation: Float): Bitmap {
        val target = Bitmap.createBitmap(width.toInt(), height.toInt(), image.config)
        val paint = Paint().apply {
            isFilterBitmap = true
        }
        Canvas(target).apply {
            translate(-x, -y) // translation

            // rotation center
            val w2 = image.width.toFloat() / 2f
            val h2 = image.height.toFloat() / 2f
            translate(w2, h2)
            rotate(rotation)
            translate(-w2, -h2)
            // rotation end

            drawBitmap(image, 0f, 0f, paint)
        }
        return target
    }

    fun convertImageToBitmapAndResize(image: Image, maxSizeBytes: Int, maxPixel: Int, outputRotationDegree: Int): CameraWrapper.ResultImage {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        log("raw data from camera: ${data.size / 1024} Kb format: ${image.format} size: ${image.width}x${image.height}")
        return convertJpegBytesToBitmap(data, image.width, maxSizeBytes, maxPixel, outputRotationDegree)
    }

    fun convertImageToBitmap(image: Image, outputRotationDegree: Int): Bitmap {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        log("raw data from camera: ${data.size / 1024} Kb format: ${image.format} size: ${image.width}x${image.height}")
        val transformationMatrix = Matrix().apply {
            postRotate(outputRotationDegree.toFloat())
        }
        val options = BitmapFactory.Options().apply { transformationMatrix }
        return logTime {
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
                ?: throw IOException("Unable to decode camera data!")
        }
    }

    private fun convertJpegBytesToBitmap(data: ByteArray, originWidth: Int, maxSizeBytes: Int, maxPixel: Int, outputRotationDegree: Int): CameraWrapper.ResultImage {
        val output = ByteArrayOutputStream()
        var tmp: Bitmap? = null
        val transformationMatrix = Matrix().apply {
            postRotate(outputRotationDegree.toFloat())
        }
        val options = BitmapFactory.Options().apply { inSampleSize = originWidth / maxPixel }
        val sampled: Bitmap = logTime { BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: throw IOException("Unable to decode camera data!") }
        log("First sampled bytecount: ${sampled.byteCount / 1024} kB sampleSize: ${options.inSampleSize}")

        var preScale = 1f
        do { // try to reach target jpeg image size
            val startTime = System.currentTimeMillis()
            output.reset()
            log("Rotate image: $outputRotationDegree ° pre scale: ${preScale}")
            if (tmp != null && tmp !== sampled) tmp.recycle()
            transformationMatrix.preScale(preScale, preScale)
            tmp = Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, transformationMatrix, true)
                    ?: throw IOException("Unable to rotate camera data!")
            tmp.compress(Bitmap.CompressFormat.JPEG, Commons.fotoQuality, output)
            val size = output.size()
            val endTime = System.currentTimeMillis()
            log("jpeg size: ${size / 1024} kB ${tmp.width}x${tmp.height} bitmap size: ${tmp.byteCount / 1024} kB")
            log("duration: ${endTime - startTime} ms")
            preScale -= 0.1f
        } while (size > maxSizeBytes)
        val jpegDataArray = output.toByteArray()
        output.close()
        if (sampled != tmp) sampled.recycle()
        val bitmap = checkNotNull(tmp)
        log("Converted to bitmap: ${bitmap.byteCount / 1024} Kb ${bitmap.width}x${bitmap.height}")
        //log("In res: ${image.width}x${image.height} -> ${options.outWidth}x${options.outHeight}")
        return CameraWrapper.ResultImage(bitmap, jpegDataArray)
    }

    fun resizeAndConvertBitmapToJpegByteArray(bm: Bitmap, newWidth: Int): ByteArray {
        log("Bitmap before: width: ${bm.width} height: ${bm.height} byteCount: ${bm.byteCount}")
        val width = bm.width
        val scale = newWidth.toFloat() / width
        val matrix = Matrix()
        matrix.postScale(-scale, scale, bm.width / 2f, bm.height / 2f )
        val resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, bm.height, matrix, true)
        log("Bitmap after manipulation: width: ${resizedBitmap.width} height: ${resizedBitmap.height} byteCount: ${resizedBitmap.byteCount}")
        val output = ByteArrayOutputStream().apply { logTime { resizedBitmap.compress(Bitmap.CompressFormat.JPEG,Commons.previewFrameQuality,this) } }
        log("byteArrayStream size ${output.size()}")
        bm.recycle()
        return output.toByteArray()
    }

    fun convertBitmapToJpegByteArray(bm: Bitmap): ByteArray {
        log("Bitmap width: ${bm.width} height: ${bm.height} byteCount: ${bm.byteCount}")
        val output = ByteArrayOutputStream().apply { logTime { bm.compress(Bitmap.CompressFormat.JPEG,Commons.previewFrameQuality,this) } }
        log("byteArrayStream size ${output.size()}")
        bm.recycle()
        return output.toByteArray()
    }

    fun imageToByteArray(image: Image): ByteArray? {
        var data: ByteArray? = null
        if (image.format == ImageFormat.JPEG) {
            log("image is JPEG")
            val planes = image.planes
            val buffer = planes[0].buffer
            data = ByteArray(buffer.capacity())
            buffer[data]
        } else if (image.format == ImageFormat.YUV_420_888) {
            log("image is YUV420888")
            data = nv21ToJpeg(
                yuvToNv21(image),
                image.width,
                image.height
            )
        } else {
            log("image is ${image.format}")
        }
        return data
    }

    fun getImageProperties(image: Image) = try {
            StringBuilder().apply {
                append("dimensions:${image.width}x${image.height}, ")
                append("format:${
                    when (image.format) {
                        ImageFormat.YUV_420_888 -> "YUV_420_888"
                        ImageFormat.YUV_422_888 -> "YUV_422_888"
                        ImageFormat.YUV_444_888 -> "YUV_444_888"
                        ImageFormat.NV16 -> "NV16"
                        ImageFormat.NV21 -> "NV21"
                        else -> image.format.toString()
                    }
                }, ", )
                append("timestamp:${image.timestamp}, ")
                append("planes:${image.planes.size}, ")
                append("yPixelStride:${image.planes[0].pixelStride}, ")
                append("yRowStride:${image.planes[0].rowStride}, ")
                append("uPixelStride:${image.planes[1].pixelStride}, ")
                append("uRowStride:${image.planes[1].rowStride}, ")
                append("vPixelStride:${image.planes[2].pixelStride}, ")
                append("vRowStride:${image.planes[2].rowStride}")
            }.toString()
        } catch (e: Throwable) {
            "getting image properties failed"
        }

    private fun yuvToNv21(image: Image) : ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        //check if format is valid
        if (image.planes.size != 3) throw IllegalArgumentException("number of planes invalid")
        if (yPlane.pixelStride != 1) throw IllegalArgumentException("Y plane invalid")
        if (uPlane.pixelStride != vPlane.pixelStride) throw IllegalArgumentException("U and V pixel strides don't match")
        if (uPlane.rowStride != vPlane.rowStride) throw IllegalArgumentException("U and V row strides don't match")

        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        var rowStride = image.planes[0].rowStride

        //copy Y plane
        var pos = 0
        if (rowStride == width) {
            yBuffer[nv21, 0, ySize]
            pos += ySize
        } else {
            var yBufferPos = -rowStride // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride
                yBuffer.position(yBufferPos)
                yBuffer[nv21, pos, width]
                pos += width
            }
        }

        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride

        //check for U and V plane overlap
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            val savedVPixel = vBuffer[1]
            try {
                vBuffer.put(1, (savedVPixel as BigInteger).inv() as Byte)
                if (uBuffer[0] == (savedVPixel as BigInteger).inv() as Byte) {
                    vBuffer.put(1, savedVPixel as Byte)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer[nv21, ySize, 1]
                    uBuffer[nv21, ySize + 1, uBuffer.remaining()]

                    return nv21
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            vBuffer.put(1, savedVPixel)
        }

        // overlap check failed, copy U and V pixel by pixel
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }

        return nv21
    }

    private fun nv21ToJpeg(nv21: ByteArray, width: Int, height: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 90, out)
        return out.toByteArray()
    }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()    // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data)   // Copy the buffer into a byte array
    return data // Return the byte array
}
