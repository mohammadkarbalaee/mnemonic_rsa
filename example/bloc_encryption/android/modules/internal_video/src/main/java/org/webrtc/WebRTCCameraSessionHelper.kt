package org.webrtc

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build.VERSION
import android.util.Range
import de.post.ident.internal_core.util.log
import org.webrtc.CameraEnumerationAndroid.CaptureFormat.FramerateRange

object WebRTCCameraSessionHelper {

    fun getFpsUnitFactor(fpsRanges: Array<Range<Int>>): Int {
        return if (fpsRanges.isEmpty()) {
            1000
        } else {
            if ((fpsRanges[0].upper as Int) < 1000) 1000 else 1
        }
    }

    fun convertFramerates(
        arrayRanges: Array<Range<Int>>,
        unitFactor: Int
    ): List<FramerateRange?> {
        val ranges: MutableList<FramerateRange?> = arrayListOf()
        for (element in arrayRanges) {
            val range: Range<Int> = element
            ranges.add(
                FramerateRange(
                    range.lower as Int * unitFactor,
                    range.upper as Int * unitFactor
                )
            )
        }
        return ranges
    }

    fun getSupportedSizes(cameraCharacteristics: CameraCharacteristics): List<Size?> {
        val supportLevel = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) as Int
        val nativeSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(SurfaceTexture::class.java)
        val sizes = convertSizes(nativeSizes)
        return if (VERSION.SDK_INT < 22 && supportLevel == 2) {
            val activeArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val filteredSizes: java.util.ArrayList<Size?> = arrayListOf()
            val sizesIterator: Iterator<*> = sizes.iterator()
            while (sizesIterator.hasNext()) {
                val size = sizesIterator.next() as Size
                if (activeArraySize!!.width() * size.height == activeArraySize.height() * size.width) {
                    filteredSizes.add(size)
                }
            }
            log("supported sizes (filtered): $filteredSizes")
            filteredSizes
        } else {
            log("supported sizes: $sizes")
            sizes
        }
    }

    private fun convertSizes(cameraSizes: Array<android.util.Size>): List<Size?> {
        val sizes: MutableList<Size?> = arrayListOf()
        for (element in cameraSizes) {
            sizes.add(Size(element.width, element.height))
        }
        log("converted sizes: $sizes")
        return sizes
    }

    fun chooseStabilizationMode(
        captureRequestBuilder: CaptureRequest.Builder,
        cameraCharacteristics: CameraCharacteristics?
    ) {
        val availableOpticalStabilization = cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) as IntArray
        var availableVideoStabilization: IntArray
        var counter: Int
        var mode: Int
        if (availableOpticalStabilization.isNotEmpty()) {
            availableVideoStabilization = availableOpticalStabilization
            counter = 0
            while (counter < availableOpticalStabilization.size) {
                mode = availableVideoStabilization[counter]
                if (mode == 1) {
                    captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 1)
                    captureRequestBuilder.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        0
                    )
                    log("using optical stabilization.")
                    return
                }
                ++counter
            }
        }
        availableVideoStabilization = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) as IntArray
        counter = availableVideoStabilization.size
        mode = 0
        while (mode < counter) {
            val modeX = availableVideoStabilization[mode]
            if (modeX == 1) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 1)
                captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0)
                log("using video stabilization.")
                return
            }
            ++mode
        }
        log("stabilization not available.")
    }

    fun chooseFocusMode(
        captureRequestBuilder: CaptureRequest.Builder,
        cameraCharacteristics: CameraCharacteristics?
    ) {
        val availableFocusModes = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) as IntArray
        for (element in availableFocusModes) {
            if (element == 3) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 3)
                log("using continuous video auto-focus.")
                return
            }
        }
        log("auto-focus is not available.")
    }
}