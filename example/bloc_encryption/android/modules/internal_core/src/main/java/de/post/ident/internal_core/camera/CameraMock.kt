package de.post.ident.internal_core.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.R
import de.post.ident.internal_core.util.log
import java.io.ByteArrayOutputStream
import java.io.File

object CameraMock {

    private lateinit var imageName: String
    var docCheckImageCounter = 1
    var dvfImageCounter = 0
    var dvfImages = 30
    val FVLC_PATH = "/fvlc"
    val DVF_PATH = "/dvf"

    fun init() {
        resetAttempt()
    }

    private fun resetAttempt() {
        docCheckImageCounter = 1
        dvfImageCounter = 0
    }

    fun getFrame(identStep: String, faceDirection: String? = "center"): ByteArray {
        val directory = if (identStep != "DVF") FVLC_PATH else DVF_PATH
        val fileName = if (identStep != "DVF") faceDirection.toString().lowercase() else dvfImageCounter++
        if (dvfImageCounter >= dvfImages) resetAttempt()
        try {
            val f = File(Environment.getExternalStorageDirectory().path + "$directory/$fileName.jpg")
            log("frame path: ${f.path}")
            return BitmapFactory.decodeFile(f.path).toJpegByteArray()
        } catch (e: Exception) {
            log("identStep $identStep image with name: $fileName not found")
            // used for test automation (websocket disconnect)
            return ByteArray(0)
        }
    }

    fun getDocCheckImage(): CameraWrapper.ResultImage {
        imageName = when (docCheckImageCounter) {
            1 -> "front"
            2 -> "back"
            else -> {"front"}
        }
        docCheckImageCounter++
        return try {
            val f = File(Environment.getExternalStorageDirectory().path + "/docCheck/$imageName.jpg")
            val bitmap = BitmapFactory.decodeFile(f.path)
            val byteArray = bitmap.toJpegByteArray()
            CameraWrapper.ResultImage(bitmap, byteArray, imageName)
        } catch (e: Exception) {
            log("docCheck $imageName image not found")
            // used for test automation (ml server error handling)
            getDefaultResultImage()
        }
    }

    fun getReferenceImage(): ByteArray {
        return try {
            val f = File(Environment.getExternalStorageDirectory().path + "/docCheck/referenceImage.jpg")
            val bitmap = BitmapFactory.decodeFile(f.path)
            bitmap.toJpegByteArray()
        } catch (e: Exception) {
            log("referenceImage $imageName image not found")
            // used for test automation (ml server error handling)
            ByteArray(0)
        }
    }

    fun getDvfImage(identStep: String): ByteArray {
        return getFrame(identStep)
    }

    private fun getDefaultResultImage(): CameraWrapper.ResultImage {
        val bitmap = R.drawable.pi_ic_slider_result_0.toDrawable().toBitmap(width = 320, height = 240)
        val byteArray = bitmap.toWebpByteArray()
        return CameraWrapper.ResultImage(bitmap, byteArray, imageName)
    }

    private fun Bitmap.toJpegByteArray(): ByteArray {
        val output = ByteArrayOutputStream().apply { this@toJpegByteArray.compress(Bitmap.CompressFormat.JPEG,
            Commons.previewFrameQuality,this) }
        return output.toByteArray()
    }

    private fun Bitmap.toWebpByteArray(): ByteArray {
        val output = ByteArrayOutputStream().apply { this@toWebpByteArray.compress(Bitmap.CompressFormat.WEBP,
            Commons.previewFrameQuality,this) }
        return output.toByteArray()
    }

}