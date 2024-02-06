package de.post.ident.internal_video.webrtc

import android.content.Context
import android.widget.Toast
import de.post.ident.internal_core.rest.VideoQualityDTO
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_video.rest.NovomindChatManager
import de.post.ident.internal_video.rest.NovomindRestService
import org.webrtc.*
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.PeerConnectionState
import org.webrtc.PeerConnection.RTCConfiguration

class RTCClient(
    private val context: Context,
    novomindChatManager: NovomindChatManager,
    novomindRestService: NovomindRestService,
    private val videoQualitySettings: VideoQualityDTO
) {
    private val audioTrackId = "audio0"
    private val videoTrackId = "video0"
    private val streamId = "stream0"

    private val rootEglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoSource: VideoSource? = null
    private val videoCapturer: WebRTCVideoCapturer by lazy { WebRTCVideoCapturer(context) }
    private var connectionInitiated = false
    private val signalingHelper by lazy {
        SignalingHelper(
            novomindChatManager,
            novomindRestService
        )
    }
    private val rtcCertificate by lazy {
        try {
            RtcCertificatePem.generateCertificate()
        } catch (err: Throwable) {
            log(err)
            null
        }
    }

    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    private var localAudioTrack : AudioTrack? = null
    private var localVideoTrack : VideoTrack? = null
    private var usesFrontCamera = true
    var isCapturing: Boolean = false
        get() = videoCapturer.isCapturing
        private set
    var videoCapturerInitiated: Boolean = false
        private set
    var localVideoView: SurfaceViewRenderer? = null
        set(value) {
            field = value
            value?.let { initSurfaceView(it, true) }
        }
    var remoteVideoView: SurfaceViewRenderer? = null
        set(value) {
            field = value
            value?.let { initSurfaceView(it) }
        }

    private val peerConnectionObserver = object: PeerConnection.Observer {
        override fun onIceCandidate(iceCandidate: IceCandidate?) {
            log("onIceCandidate: ${iceCandidate?.serverUrl}")
            signalingHelper.sendIceCandidate(iceCandidate)
        }

        override fun onIceConnectionChange(iceConnectionState: IceConnectionState?) {
            log("onIceConnectionChange: ${iceConnectionState?.name}")
            when (iceConnectionState) {
                IceConnectionState.CLOSED -> {
                    if (peerConnection?.iceGatheringState() == IceGatheringState.COMPLETE) {
                        disposeConnection()
                    }
                }
                IceConnectionState.FAILED -> peerConnection?.restartIce()
                else -> return
            }
        }

        override fun onIceGatheringChange(iceGatheringState: IceGatheringState?) {
            log("onIceGatheringChange: ${iceGatheringState?.name}")
            when (iceGatheringState) {
                IceGatheringState.COMPLETE -> {
                    if (peerConnection?.iceConnectionState() == IceConnectionState.CLOSED) {
                        disposeConnection()
                    }
                }
                else -> {}
            }
        }

        override fun onAddStream(mediaStream: MediaStream?) {
            log("onAddStream: ${mediaStream?.videoTracks?.size}")
            mediaStream?.videoTracks?.get(0)?.addSink(remoteVideoView)
        }

        override fun onRemoveStream(mediaStream: MediaStream?) {
            log("onRemoveStream: ${mediaStream?.videoTracks?.size}")
            mediaStream?.videoTracks?.get(0)?.removeSink(remoteVideoView)
        }

        override fun onStandardizedIceConnectionChange(newState: IceConnectionState?) {
            super.onStandardizedIceConnectionChange(newState)
            log("onStandardizedIceConnectionChange: $newState")
            when (newState) {
                IceConnectionState.FAILED -> peerConnection?.restartIce()
                else -> return
            }
        }

        override fun onConnectionChange(newState: PeerConnectionState?) {
            super.onConnectionChange(newState)
            log("onConnectionChange: $newState")
            when (newState) {
                PeerConnectionState.FAILED -> peerConnection?.restartIce()
                else -> return
            }
        }

        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
            super.onSelectedCandidatePairChanged(event)
            log("onSelectedCandidatePairChanged: local ${event?.local?.sdp} remote ${event?.remote?.sdp}")
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            super.onAddTrack(receiver, mediaStreams)
            log("onAddTrack: ${receiver?.track()}, ${mediaStreams?.size}")
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            super.onRemoveTrack(receiver)
            log("onRemoveTrack: ${receiver?.track()}")
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            super.onTrack(transceiver)
            log("onTrack: ${transceiver?.receiver}")
        }

        override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>?) = log("onIceCandidatesRemoved")
        override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) = log("onSignalingChange: ${signalingState?.name}")
        override fun onIceConnectionReceivingChange(change: Boolean) = log("onIceConnectionReceivingChange: $change")
        override fun onDataChannel(dataChannel: DataChannel?) = log("onDataChannel")
        override fun onRenegotiationNeeded() = log("onRenegotiationNeeded")
    }

    init {
        initPeerConnectionFactory(context)
    }

    private fun initPeerConnectionFactory(context: Context) = PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(true)
        .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
        .createInitializationOptions()
    )

    private fun buildPeerConnectionFactory() = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

    private fun buildPeerConnection(
        observer: PeerConnection.Observer,
        config: RTCConfiguration,
    ): PeerConnection? {
        val peerConnection = peerConnectionFactory?.createPeerConnection(
            config,
            observer
        )
        log("peerConnection created!")
        connectionInitiated = true
        return peerConnection
    }

    private fun initSurfaceView(view: SurfaceViewRenderer, mirror: Boolean = false) = view.run {
        setMirror(mirror)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun initLocalVideoCapture() {
        if (peerConnectionFactory == null) {
            peerConnectionFactory = buildPeerConnectionFactory()
            localAudioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
            localVideoSource = peerConnectionFactory?.createVideoSource(false)
        }
        if (localVideoView != null && remoteVideoView != null) {
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext).let {
                videoCapturer.initialize(it, context, localVideoSource?.capturerObserver)
                videoCapturer.startCapture(videoQualitySettings.width, videoQualitySettings.height, videoQualitySettings.frameRate)
                localAudioTrack = peerConnectionFactory?.createAudioTrack(audioTrackId, localAudioSource)
                localVideoTrack = peerConnectionFactory?.createVideoTrack(videoTrackId, localVideoSource)?.apply {
                    setEnabled(true)
                    addSink(localVideoView)
                }
                videoCapturerInitiated = true
                log("local video capture started")
            }
        }
    }

    fun disposeLocalVideoCapture() {
        log("stop local video capture")
        if (videoCapturerInitiated) {
            stopLocalVideoCapture()
            videoCapturer.dispose()
            videoCapturerInitiated = false
            log("local video capture stopped")
        }
    }

    fun startLocalVideoCapture() {
        if (videoCapturerInitiated) {
            videoCapturer.startCapture()
        } else {
            initLocalVideoCapture()
        }
    }

    fun stopLocalVideoCapture() = videoCapturer.stopCapture()

    fun takePicture(takePictureCallback: WebRTCCameraSession.TakePictureCallback) =
        videoCapturer.takePicture(takePictureCallback)

    fun connect(iceServers: List<PeerConnection.IceServer>) {
        val config = RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            iceCandidatePoolSize = 1
            certificate = rtcCertificate
        }

        peerConnection?.dispose()
        peerConnection = buildPeerConnection(peerConnectionObserver, config)
        peerConnection?.addTrack(localAudioTrack, listOf(streamId))
        peerConnection?.addTrack(localVideoTrack, listOf(streamId))
    }

    fun disconnect() {
        if(peerConnection?.connectionState() == PeerConnectionState.CONNECTED) {
            peerConnection?.close()
            log("disconnected")
        }
    }

    fun disposeConnection() {
        log("dispose connection")
        if (connectionInitiated) {
            localAudioSource?.dispose()
            localVideoSource?.dispose()
            localAudioTrack?.dispose()
            localVideoTrack?.dispose()
            localVideoView?.release()
            remoteVideoView?.release()
            rootEglBase.releaseSurface()
            rootEglBase.release()
            peerConnection?.dispose()
            peerConnection = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            connectionInitiated = false
        }
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        log("answer")
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                log("PeerConnection.answer.createAnswer: success!")
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) = log("createAnswer.onSetFailure: $p0")
                    override fun onSetSuccess() = log("createAnswer.onSetSuccess")
                    override fun onCreateSuccess(p0: SessionDescription?) = log("createAnswer.onCreateSuccess: Description $p0")
                    override fun onCreateFailure(p0: String?) = log("createAnswer.onCreateFailureLocal: $p0")
                }, sessionDescription)
                sdpObserver.onCreateSuccess(sessionDescription)
            }

            override fun onCreateFailure(errorMsg: String?) = log("onCreateFailure: $errorMsg")
        }, MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        })
    }

    fun answer() = peerConnection?.answer(object: SdpObserver{
        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
            log("answer.onCreateSuccess")
            signalingHelper.sendAnswer(sessionDescription)
        }

        override fun onSetSuccess() {
            log("answer.onSetSuccess")
            handleQueuedRemoteIceCandidates()
        }
        override fun onCreateFailure(sessionDescription: String?) = log("answer.onCreateFailure: $sessionDescription")
        override fun onSetFailure(errorMsg: String?) = log("answer.onSetFailure: $errorMsg")
    })

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) = peerConnection?.setRemoteDescription(
        object : SdpObserver {
            override fun onCreateSuccess(sessionDescription1: SessionDescription?) = log("onRemoteSessionReceived.onCreateSuccess: $sessionDescription1")
            override fun onSetFailure(errorMsg: String?) = log("onRemoteSessionReceived.onSetFailure: $errorMsg")
            override fun onSetSuccess() = log("onRemoteSessionReceived.onSetSuccess")
            override fun onCreateFailure(errorMsg: String?) = log("onRemoteSessionReceived.onCreateFailure: $errorMsg")
        }, sessionDescription)

    fun addRemoteIceCandidate(iceCandidate: IceCandidate) {
        log("peerConnection state: ${peerConnection?.connectionState()}")
        if (peerConnection?.connectionState() != PeerConnectionState.CLOSED
            && peerConnection?.connectionState() != PeerConnectionState.FAILED
            && peerConnection?.localDescription != null
            && peerConnection?.remoteDescription != null) {
            handleQueuedRemoteIceCandidates()
            peerConnection?.addIceCandidate(iceCandidate)
            log("remote candidate added: ${iceCandidate.serverUrl}")
        } else {
            queueRemoteIceCandidate(iceCandidate)
        }
    }

    private fun queueRemoteIceCandidate(iceCandidate: IceCandidate) {
        queuedRemoteCandidates.add(iceCandidate)
        log("remote candidate queued: ${iceCandidate.sdp}")
    }

    private fun handleQueuedRemoteIceCandidates() {
        peerConnection?.let { peerConn ->
            queuedRemoteCandidates.forEach { candidate ->
                peerConn.addIceCandidate(candidate)
                log("queued remote candidate added: ${candidate.sdp}")
            }
            queuedRemoteCandidates.clear()
        } ?: run {
            log("peerConnection not ready for handling of queued remote ice candidates")
        }
    }

    fun switchCamera() = videoCapturer.switchCamera(object: CameraVideoCapturer.CameraSwitchHandler {
        override fun onCameraSwitchDone(front: Boolean) {
            log("camera switched to ${if(front) "front" else "back"}")
            usesFrontCamera = front
            localVideoView?.setMirror(front)
        }
        override fun onCameraSwitchError(errorMsg: String?) {
            log("camera switch failed: $errorMsg")
            Toast.makeText(context, LocalizedStrings.getString("eid_error_unknown0"), Toast.LENGTH_SHORT).show()
        }
    })
    fun toggleFlashlight() = videoCapturer.toggleFlashlight()
    fun getFlashlightState() = videoCapturer.getFlashlightState()

    fun registerFlashlightCallback(callback: FlashlightCallback) {
        videoCapturer.flashlightCallback = callback
    }

    fun unregisterFlashlightCallback() {
        videoCapturer.flashlightCallback = null
    }
}
