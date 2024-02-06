package de.post.ident.internal_video.webrtc

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.post.ident.internal_core.camera.CameraImageUtils
import de.post.ident.internal_core.rest.VideoQualityDTO
import de.post.ident.internal_core.util.log
import de.post.ident.internal_video.rest.NovomindChatManager
import de.post.ident.internal_video.rest.NovomindRestService
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebRTCManager(
    context: Context,
    novomindChatManager: NovomindChatManager,
    novomindRestService: NovomindRestService,
    videoQualitySettings: VideoQualityDTO
) {
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter: JsonAdapter<Map<String, Any>>
            = moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    var client: RTCClient? = RTCClient(context, novomindChatManager, novomindRestService, videoQualitySettings)

    var localVideoView: SurfaceViewRenderer? = null

    fun initSurfaceViews(
        localVideoView: SurfaceViewRenderer,
        remoteVideoView: SurfaceViewRenderer
    ) {
        client?.localVideoView = localVideoView
        client?.remoteVideoView = remoteVideoView
        this.localVideoView = localVideoView
    }

    fun endCall() = client?.apply {
            disposeLocalVideoCapture()
            disconnect()
            disposeConnection()
        }

    suspend fun grabFrame() = suspendCoroutine { cont ->
        log("try grabbing frame")
        localVideoView?.addFrameListener(object: EglRenderer.FrameListener {
            override fun onFrame(bitmap: Bitmap?) {
                Handler(Looper.getMainLooper()).post {
                    localVideoView?.removeFrameListener(this)
                }
                try {
                    bitmap?.let {
                        val bytes = CameraImageUtils.convertBitmapToByteArray(it)
                        val frame = ImageData(
                            bytes,
                            it.width,
                            it.height,
                            true
                        )
                        log("frame specs - width: ${frame.width} height: ${frame.height} byteCount: ${frame.data.size}")
                        cont.resume(frame)
                    } ?: run {
                        log("frame was empty")
                        cont.resume(null)
                    }
                } catch (e: Throwable) {
                    log("error grabbing frame")
                    cont.resumeWithException(e)
                }
            }
        }, 1f)
    }

    fun takePicture(takePictureCallback: WebRTCCameraSession.TakePictureCallback) = client?.takePicture(takePictureCallback)

    fun handleWebRTCEvent(eventData: String) {
        log("handle WebRTC event: $eventData")
        val json = checkNotNull(jsonAdapter.fromJson(eventData))

        when (json["type"]) {
            "offer" -> onWebRTCOffer(json)
            "candidate" -> onWebRTCCandidate(json)
            "disconnect" -> onWebRTCDisconnect()
        }
    }

    private fun onWebRTCOffer(data: Map<String, Any>) {
        log("offer received!")
        val config = data["config"] as Map<*, *>
        val iceServersJson = config["iceServers"] as List<*>
        val iceServers = mutableListOf<IceServer>()
        iceServersJson.forEach { server ->
            if (server is Map<*, *>) {
                val url = server["urls"] as String
                val iceServer: IceServer
                if (url.startsWith("turn:")) {
                     iceServer = createTurnServer(
                         url,
                         server["username"] as String,
                         server["credential"] as String
                     )
                } else {
                    iceServer = createStunServer(url)
                }
                iceServers.add(iceServer)
                log("new ICE server: url ${iceServer.urls}, user ${iceServer.username}")
            }
        }

        val remoteSessionDescription = SessionDescription(
            SessionDescription.Type.OFFER,
            data["sdp"] as String
        )

        client?.let { client ->
            if (client.videoCapturerInitiated.not()) {
                client.initLocalVideoCapture()
            } else if (client.isCapturing.not()) {
                client.startLocalVideoCapture()
            }
            client.connect(iceServers)
            client.onRemoteSessionReceived(remoteSessionDescription)
            client.answer()
        }
    }

    private val paramTransport = "?transport="

    private fun createTurnServer(
        url: String,
        username: String,
        credential: String
    ) : IceServer {
        var iceUrl = url
        if (iceUrl.contains(paramTransport).not()) {
            iceUrl = StringBuilder(iceUrl)
                .append(paramTransport)
                .append("tcp")
                .toString()
        }
        return IceServer.builder(iceUrl)
            .setUsername(username)
            .setPassword(credential)
            .createIceServer()
    }

    private fun createStunServer(url: String) : IceServer {
        var iceUrl = url
        if (iceUrl.contains(paramTransport).not()) {
            iceUrl = StringBuilder(iceUrl)
                .append(paramTransport)
                .append("udp")
                .toString()
        }
        return IceServer.builder(iceUrl).createIceServer()
    }

    private fun onWebRTCCandidate(data: Map<String, Any>) {
        log("ice candidate received!")
        val candidateJson = data["candidate"] as Map<*, *>
        val sdpMid = candidateJson["sdpMid"] as String
        val sdpMLineIndex = (candidateJson["sdpMLineIndex"] as Double).toInt()
        val sdpCandidate = candidateJson["candidate"] as String

        if (sdpCandidate.isNotEmpty()) {
            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate)
            client?.addRemoteIceCandidate(candidate)
        }
    }

    private fun onWebRTCDisconnect() {
        log("disconnect received!")
        client?.disconnect()
    }
}

data class ImageData(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val isScreenGrab: Boolean = false,
    val cropCurrentDocument: Boolean = false,
    val tags: MutableMap<String, String> = mutableMapOf()
) {
    companion object {
        const val TAG_ERROR_MSG = "ERROR_MSG"
        const val TAG_IMG_PROPS = "IMG_PROPS"
        const val TAG_CAMERA_PROPS = "CAMERA_PROPS"
    }
}
