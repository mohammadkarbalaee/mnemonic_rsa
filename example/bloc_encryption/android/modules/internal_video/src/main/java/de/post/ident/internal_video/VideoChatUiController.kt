package de.post.ident.internal_video

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import org.webrtc.FlashlightCallback
import org.webrtc.FlashlightState

class VideoChatUiController(
    val context: Context,
    val videoManager: VideoManager
) {
    @Keep
    enum class UiAction {
        UPDATE, ANIMATE_SCREENSHOT
    }

    private val flashlightCallback = object: FlashlightCallback {
        override fun onFlashlightStateChanged(state: FlashlightState) {
            notifyUpdate()
        }
    }

    init {
        videoManager.registerFlashlightCallback(flashlightCallback)
    }

    private var onEventObserver: ((UiAction) -> Unit)? = null

    private val uiHandler = Handler(Looper.getMainLooper())

    private fun notifyUpdate(action: UiAction = UiAction.UPDATE) {
        uiHandler.post {
            onEventObserver?.invoke(action)
        }
    }

    fun setEventObserver(observer: (UiAction) -> Unit) {
        onEventObserver = observer
    }

    fun unsetFlashlightObserver() {
        onEventObserver = null
    }

    var isUploadIndicatorVisible: Boolean = false
        internal set(value) {
            field = value
            notifyUpdate()
        }

    var isReconnectIndicatorVisible: Boolean = false
        internal set(value) {
            field = value
            notifyUpdate()
        }

    fun getFlashlightState() = videoManager.getFlashlightState()

    fun animateScreenshot() { //TODO ???
        notifyUpdate(UiAction.ANIMATE_SCREENSHOT)
    }
}