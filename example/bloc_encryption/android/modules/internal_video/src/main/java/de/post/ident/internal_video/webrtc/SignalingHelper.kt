package de.post.ident.internal_video.webrtc

import de.post.ident.internal_core.util.log
import de.post.ident.internal_video.rest.AnswerDataDTO
import de.post.ident.internal_video.rest.CandidateDataDTO
import de.post.ident.internal_video.rest.NovomindChatManager
import de.post.ident.internal_video.rest.NovomindRestService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.webrtc.*

class SignalingHelper (
    private val novomindChatManager: NovomindChatManager,
    private val novomindRestService: NovomindRestService
) : CoroutineScope by MainScope() {

    fun sendAnswer(sessionDescription: SessionDescription?) {
        sessionDescription?.let { sdp ->
            launch {
                try {
                    novomindRestService.webRtcSendAnswer(
                        novomindChatManager.chatId,
                        novomindChatManager.token,
                        AnswerDataDTO("answer", sdp.description)
                    )
                    log("answer sent!")
                } catch (err: Throwable) {
                    log("exception was caught: ${err.cause}")
                }
            }
        } ?: run {
            log("sessionDescription is null!")
        }
    }

    fun sendIceCandidate(iceCandidate: IceCandidate?) {
        iceCandidate?.let { candidate ->
            launch {
                try {
                    novomindRestService.webRtcSendCandidate(
                        novomindChatManager.chatId,
                        novomindChatManager.token,
                        CandidateDataDTO(
                            "candidate",
                            CandidateDataDTO.CandidateDataCandidateDTO(
                                candidate.sdp,
                                candidate.sdpMLineIndex
                            )
                        )
                    )
                    log("candidate sent!")
                } catch (err: Throwable) {
                    log("exception was caught: ${err.cause}")
                }
            }
        } ?: run {
            log("candidate is null")
        }
    }
}