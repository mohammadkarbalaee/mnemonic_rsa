package org.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import de.post.ident.internal_core.util.log

class WebRTCCameraEnumerator(val context: Context) {

    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun getCameraIds(): Array<String> {
        return try {
            cameraManager.cameraIdList
        } catch (e: Exception) {
            log("camera access exception: $e")
            arrayOf("")
        }
    }

    fun isFrontFacing(cameraId: String): Boolean {
        val characteristics = getCameraCharacteristics(cameraId)
        return characteristics != null && characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
    }

    fun isBackFacing(cameraId: String): Boolean {
        val characteristics = getCameraCharacteristics(cameraId)
        return characteristics != null && characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

    fun getCameraId(isFrontCamera: Boolean) : String {
        getCameraIds().forEach { cameraId ->
            val cameraCapabilities = getCameraCharacteristics(cameraId)?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            if (cameraCapabilities?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true) {
                if ((isFrontCamera && isFrontFacing(cameraId)) || (isFrontCamera.not() && isBackFacing(cameraId))) {
                    return cameraId
                }
            }
        }

        return "0"
    }

    private fun getCameraCharacteristics(cameraId: String) = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Throwable) {
            log("camera access exception: $e")
            null
        }
}