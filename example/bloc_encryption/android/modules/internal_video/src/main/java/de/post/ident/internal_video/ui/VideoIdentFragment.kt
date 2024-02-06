package de.post.ident.internal_video.ui

import android.content.Context
import android.media.MediaActionSound
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.rest.getUserMessage
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showChoiceDialog
import de.post.ident.internal_core.util.ui.recyclerview.BaseCell
import de.post.ident.internal_core.util.ui.recyclerview.CellRecyclerAdapter
import de.post.ident.internal_core.util.ui.recyclerview.ViewBindingCell
import de.post.ident.internal_core.util.ui.subscribeForEvent
import de.post.ident.internal_video.*
import de.post.ident.internal_video.databinding.*
import de.post.ident.internal_video.util.EmmiVideoReporter
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.webrtc.FlashlightState

fun View.setVisible(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.GONE
}

class VideoContainer(
    private val emmiReporter: EmmiVideoReporter,
    private val binding: PiVideoContainerBinding,
    private val videoChatUiController: VideoChatUiController,
    private val videoManager: VideoManager
    ) {

    private var allowFlashlight = false
    private var cameraIsMuted = false

    init {
        binding.connectionText.text = LocalizedStrings.getString("waiting_line_establish_connection")
        binding.actionText.text = LocalizedStrings.getString("err_video_reconnection")

        binding.videoSwitchCam.setOnClickListener {
            emmiReporter.send(LogEvent.MR_CAMERA_SWITCH)
            videoManager.switchCamera()
        }

        binding.videoPowerSwitchCam.setOnClickListener {
            cameraIsMuted = !cameraIsMuted
            val eventContext = mapOf("cameraIsMuted" to cameraIsMuted.toString())
            emmiReporter.send(LogEvent.MR_CAMERA_MUTE, eventContext = eventContext)
            videoManager.toggleCamera(binding)
        }

        binding.videoToggleFlashlight.setOnClickListener {
            emmiReporter.send(LogEvent.MR_FLASHLIGHT_TOGGLE)
            videoManager.toggleFlashlight()
        }
        videoChatUiController.setEventObserver {
            when (it) {
                VideoChatUiController.UiAction.UPDATE -> updateUI()
                VideoChatUiController.UiAction.ANIMATE_SCREENSHOT -> animateScreenshot()
            }
        }

        updateUI()
    }

    fun updateAgentName(name: String) {
        binding.connectionText.text = name
        binding.connectionText.setVisible(true)
    }

    fun connectionProgress(isVisible: Boolean) {
        binding.agentProcessIndicator.setVisible(isVisible)
        binding.connectionText.setVisible(isVisible)
    }

    fun enablePowerSwitch(isVisible: Boolean) {
        binding.videoPowerSwitchCam.isEnabled = isVisible
        binding.videoPowerSwitchCam.isVisible = isVisible
    }

    fun setVideochatPreconditions() {
        binding.videocontainerUser.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.pi_transparent))
        binding.videoSwitchCam.isVisible = true
    }

    fun allowFlashlight(isVisible: Boolean) {
        allowFlashlight = isVisible
    }

    private fun animateScreenshot() {
        binding.screenshotAnimation.isVisible = true
        binding.screenshotAnimation.alpha = 0F
        binding.screenshotAnimation.animate().alpha(1F).setDuration(200L).withEndAction {
            binding.screenshotAnimation.animate().setDuration(200L).alpha(0F).withEndAction {
                binding.screenshotAnimation.isVisible = false
            }
        }
        MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
    }

    private fun updateUI() {
        // upload indicator
        binding.uploadIndicator.setVisible(videoChatUiController.isUploadIndicatorVisible)
        // reconnect indicator
        binding.actionInformationContainer.setVisible(videoChatUiController.isReconnectIndicatorVisible)

        // Update flashlight button state
        if (allowFlashlight) {
            val flashlightState = videoChatUiController.getFlashlightState()
            log("update flashlight button: $flashlightState")
            binding.videoToggleFlashlight.setVisible(flashlightState != FlashlightState.UNAVAILABLE)
            val buttonBackgroundRes = if (flashlightState == FlashlightState.ON) R.drawable.pi_circle_yellow else R.drawable.pi_flashlight_button_bg_transparent
            binding.videoToggleFlashlight.setBackgroundResource(buttonBackgroundRes)
        }
    }

    fun destroy() = videoChatUiController.unsetFlashlightObserver()
}

class AgentMessageCell(val message: String) : ViewBindingCell<PiChatRowAgentBinding>() {
    override fun bindView(viewHolder: PiChatRowAgentBinding) {
        viewHolder.chatRowMessage.text = message
    }

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean): PiChatRowAgentBinding {
        return PiChatRowAgentBinding.inflate(inflater, parent, attachToParent)
    }
}

class ZipMessageCell(val message: String) : ViewBindingCell<PiChatRowZipBinding>() {
    override fun bindView(viewHolder: PiChatRowZipBinding) {
        viewHolder.chatRowMessage.text = message
    }

    override fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean): PiChatRowZipBinding {
        return PiChatRowZipBinding.inflate(inflater, parent, attachToParent)
    }
}

class ChatContainer(private val binding: PiChatOverlayBinding, private val onSendMessage: (String) -> Unit) {
    private val messageCellList = mutableListOf<BaseCell<*>>()
    private val adapter = CellRecyclerAdapter(messageCellList)

    init {
        binding.piChatList.adapter = adapter
    }

    init {
        binding.piChatMessageSend.text = LocalizedStrings.getString("default_btn_send")
        binding.piChatMessage.editText?.hint = LocalizedStrings.getString("agent_message_hint")
        binding.piChatMessageSend.setOnClickListener {
            binding.piChatMessage.editText?.let {
                val message = it.text.toString()
                onSendMessage(message)
            }
        }
    }

    fun addMessage(message: NovomindEvent.ChatMessage) {
        messageCellList.add(0, if (message.isAgent) AgentMessageCell(message.msg) else ZipMessageCell(message.msg))
        adapter.notifyItemInserted(0)
        binding.piChatList.scrollToPosition(0)
    }

    fun clearText() {
        binding.piChatMessage.editText?.setText("")
    }
}

class VideoIdentFragment(private val isRecoveredBySystem: Boolean = true) : BaseVideoFragment() {
    // Prevents crashing when fragment is killed by the system because of low memory.
    // The VideoFragment is recreated and destroyed again too fast.

    companion object {
        fun newInstance(): VideoIdentFragment = VideoIdentFragment(false)
    }

    private var currentFragment: Fragment? = null
    private lateinit var viewBinding: PiFragmentVideoIdentBinding
    private lateinit var videoContainer: VideoContainer
    private lateinit var chatOverlay: ChatContainer

    init {
        if (VideoManager.isInitiated()) videoManager.currentState.observe(this) {
            updateUi(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = PiFragmentVideoIdentBinding.inflate(inflater, container, false)
        if (isRecoveredBySystem) {
            // When fragment was recovered we do not want to initialize icelink becaus we get destroyed again too fast.
            return viewBinding.root
        }

        videoManager.initVideoViews(
            viewBinding.contentPaneVideo.localview,
            viewBinding.contentPaneVideo.remoteview
        )

        val uiController = videoManager.createUiController()
        videoContainer = VideoContainer(emmiReporter, viewBinding.contentPaneVideo, uiController, videoManager)
        chatOverlay = ChatContainer(viewBinding.piChatOverlay) { message ->
            if (message.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        videoManager.sendZipMessage(message)
                        chatOverlay.clearText()
                    } catch (err: Throwable) {
                        ensureActive()
                        Toast.makeText(requireContext(), err.getUserMessage(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        videoManager.isChatOverlayVisible.observe(viewLifecycleOwner) { visible ->
            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            viewBinding.piChatOverlay.root.isVisible = visible // make chat overlay visible or invisible
            if (visible) {
                viewBinding.piChatOverlay.piChatMessage.editText?.requestFocus()
                imm.showSoftInput(viewBinding.piChatOverlay.piChatMessage.editText, InputMethodManager.SHOW_IMPLICIT)
            } else { // hide keyboard
                imm.hideSoftInputFromWindow(viewBinding.root.windowToken, 0)
            }
        }

        videoManager.novomindEventBus.subscribeForEvent(viewLifecycleOwner) { msg: NovomindEvent.ChatMessage ->
            emmiReporter.send(LogEvent.VC_AGENT_MESSAGE)
            chatOverlay.addMessage(msg)
            if (videoManager.isChatOverlayVisible.value == false) {
                showChoiceDialog(
                        context = requireContext(),
                        title = LocalizedStrings.getString("agent_message_dialog_title"),
                        msg = msg.msg,
                        positiveButton = LocalizedStrings.getString("agent_message_dialog_answer_button_text"),
                        onPositive = {
                            videoManager.showChatOverlay(true)
                        },
                        negativeButton = LocalizedStrings.getString(R.string.default_btn_ok),
                )
            }
        }

        videoManager.currentState.value?.let { updateUi(it) }

        return viewBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
//        videoManager.destroyStreamController()
        if (this::videoContainer.isInitialized) videoContainer.destroy()
    }

    private fun updateUi(videoState: VideoState) {
        videoContainer.connectionProgress(false)
        videoManager.agentName?.let { videoContainer.updateAgentName(it) }
        when (videoState) {
            VideoState.WAITING_FOR_AGENT -> videoContainer.enablePowerSwitch(true)
            VideoState.INCOMING_CALL -> {
                videoContainer.enablePowerSwitch(false)
                videoContainer.setVideochatPreconditions()
                videoContainer.allowFlashlight(true)
                videoContainer.connectionProgress(true)
            }
            VideoState.DECLARATION_OF_CONSENT -> viewBinding.contentPaneVideo.agentPlaceholderOverlay.visibility = View.INVISIBLE
            VideoState.CANCELED_VERIFY_PROCESS,
            VideoState.CALL_ENDED_BY_USER,
            VideoState.CALL_ENDED_BY_AGENT -> {
                (requireActivity() as VideoIdentActivity).showVideochatAbortedFragment(AbortReason.ENDED_BY_AGENT)
                return
            }
            VideoState.CALL_ENDED_BY_ERROR -> {
                (requireActivity() as VideoIdentActivity).showVideochatAbortedFragment(videoManager.abortReason ?: AbortReason.ENDED_BY_AGENT)
                return
            }
            VideoState.CALL_ENDED_SUCCESSFULLY -> {
                (requireActivity() as VideoIdentActivity).showVideochatSuccessFragment()
                return
            }
            else -> {}
        }

        //TODO reset resetWaitingLineTimeout in service (AppMonitor) FragmentVideo.java:373

        viewBinding.progressBar.visibility = if (videoState.progress >= 0) View.VISIBLE else View.GONE
        viewBinding.progressBar.progress = videoState.progress
        val currentClass = currentFragment?.let { it::class }
        if (currentClass != videoState.fragmentClass) {
            log("Change fragment to: ${videoState.fragmentClass.simpleName}")
            childFragmentManager.commit {
                val newFragment = videoState.fragmentClass.java.newInstance()
                replace(viewBinding.contentPaneDetails.id, newFragment)
                currentFragment = newFragment
            }
        }
    }
}