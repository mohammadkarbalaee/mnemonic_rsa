package de.post.ident.internal_video.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.feedback.FeedbackFragment
import de.post.ident.internal_core.rest.IdentMethodDTO
import de.post.ident.internal_core.process_description.ProcessDescriptionFragment
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.permission.PermissionFragmentParameter
import de.post.ident.internal_core.permission.UserPermission
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.ModuleMetaData
import de.post.ident.internal_core.util.*
import de.post.ident.internal_video.*
import de.post.ident.internal_video.databinding.PiInfoBottomSheetBinding
import de.post.ident.internal_video.util.EmmiVideoReporter
import kotlinx.coroutines.launch

// Needed to detect if the activity was destroyed by the system and got recovered.
// In this case we want to show an error screen.
private var isVideoIdentActivityDestroyedBecauseOfLowMemory = false

class VideoIdentActivity : BaseModuleActivity() {

    private lateinit var videoManager: VideoManager
    private val emmiReporter by lazy { EmmiVideoReporter(videoManager) }
    private lateinit var toolbar: MaterialToolbar

    override val moduleMetaData: ModuleMetaData by lazy {
        val permissionList = mutableListOf(UserPermission.CAMERA, UserPermission.MICROPHONE, UserPermission.NOTIFICATION)
        val drawableList = arrayListOf(R.drawable.pi_pd_passports, R.drawable.pi_pd_livechat, R.drawable.pi_pd_tan)
        val titleList = checkNotNull(getCaseResponse().modules.processDescription?.subtitle)

        val pageDataList: List<ProcessDescriptionFragment.PageData> = titleList.mapIndexedNotNull { index, title ->
            if (index < drawableList.size) {
                ProcessDescriptionFragment.PageData(title, drawableList[index])
            } else {
                null
            }
        }

        ModuleMetaData(
                PermissionFragmentParameter(IdentMethod.VIDEO, permissionList, null),
                ProcessDescriptionFragment.ProcessDescriptionData(getProcessDescriptionData(), pageDataList, IdentMethodDTO.VIDEO)
        )
    }

    override fun permissionsGranted() {
        launch {
            loadingContinueButton(viewBinding.btnContinueStandard)
            lifecycleScope.launch { sendTermsAccepted() }
            startVideoProcess()
            hideContinueButtons()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (VideoChatService.isHangupIntent(intent)) {
            log("Hangup from notification received")
            handleCloseChat()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initVideoManager()
        super.onCreate(savedInstanceState)
        toolbar = findViewById(R.id.toolbar_actionbar)
        toolbar.inflateMenu(R.menu.pi_menu_calling)
        toolbar.setOnMenuItemClickListener { menu ->
            when (menu.itemId) {
                R.id.menu_end_call -> {
                    handleCloseChat()
                    true
                }
                R.id.menu_open_chat -> {
                    videoManager.showChatOverlay(true)
                    true
                }
                R.id.menu_close_chat -> {
                    videoManager.showChatOverlay(false)
                    true
                }
                else -> false
            }
        }

        // Causes SDK is not initialized Exceptions - LocalizedStrings.kt needs refactoring
        if (CoreConfig.isInitialized) {
            updateToolbar()
        }

        initContinueButton(viewBinding.btnContinueStandard) { startVideoProcess() }

        if (isVideoIdentActivityDestroyedBecauseOfLowMemory) {
            // Activity was recovered from low memory.
            isVideoIdentActivityDestroyedBecauseOfLowMemory = false
            showVideochatAbortedFragment(AbortReason.ACTIVITY_DESTROYED_LOW_MEMORY)
            hideContinueButtons()
        }
    }

    override fun onPause() {
        lifecycleScope.launch {
            videoManager.appPaused(true)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            videoManager.appPaused(false)
        }
    }

    override fun onStop() {
        videoManager.onAppInBackground()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        videoManager.onAppInForeground()
    }

    private fun initVideoManager() {
        if (CoreConfig.isInitialized) {
            VideoManager.init(getCaseResponse())
            videoManager = VideoManager.instance()
            videoManager.currentState.observe(this) { updateToolbar() }
            videoManager.isChatOverlayVisible.observe(this) { updateToolbar() }
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun updateToolbar() {

        toolbar.menu.findItem(R.id.menu_end_call).apply {
            title = LocalizedStrings.getString("end_call")
            icon?.setTint(ContextCompat.getColor(this@VideoIdentActivity, R.color.pi_exit_icon_color_on_primary_brand_color))
            isVisible = videoManager.currentState.value?.canHangup ?: false
        }

        toolbar.menu.findItem(R.id.menu_open_chat).apply {
            title = LocalizedStrings.getString("agent_message_menu_option_chat")
            icon?.setTint(ContextCompat.getColor(this@VideoIdentActivity, R.color.pi_icon_color_on_primary_brand_color))
            val chatAllowed = videoManager.currentState.value?.canOpenChat ?: false
            isVisible = chatAllowed && (videoManager.isChatOverlayVisible.value?.not() ?: false)
        }

        toolbar.menu.findItem(R.id.menu_close_chat).apply {
            title = LocalizedStrings.getString("agent_message_menu_option_chat_close")
            icon?.setTint(ContextCompat.getColor(this@VideoIdentActivity, R.color.pi_icon_color_on_primary_brand_color))
            val chatAllowed = videoManager.currentState.value?.canOpenChat ?: false
            isVisible = chatAllowed && (videoManager.isChatOverlayVisible.value ?: false)
        }
    }

    private fun showToolbar(show: Boolean) {
        viewBinding.toolbar.appBar.isVisible = show
    }

    fun showWaitingQueueInfo() = showBottomSheet(
            LocalizedStrings.getString("waiting_queue_info_title"),
            LocalizedStrings.getString("waiting_queue_info_message"),
            LocalizedStrings.getString("default_btn_ok")
        )

    private fun showBottomSheet(
        titleText: String,
        msgText: String,
        buttonText: String
    ) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val viewBinding = PiInfoBottomSheetBinding.inflate(LayoutInflater.from(this))
        bottomSheetDialog.setContentView(viewBinding.root)

        viewBinding.titleText.text = titleText
        viewBinding.msgText.text = msgText
        viewBinding.button.text = buttonText
        viewBinding.button.setOnClickListener { bottomSheetDialog.dismiss() }
        if (CoreConfig.isSdk) viewBinding.root.setBackgroundResource(R.color.pi_white)

        bottomSheetDialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoManager.destroy()
        if (isFinishing.not()) {
            // this should only happen when the system tries to save memory
            isVideoIdentActivityDestroyedBecauseOfLowMemory = true
        }
    }

    suspend fun startVideoProcess(isFirstTry: Boolean = true) {
        //create new video attempt

        try {
            val caseResponse = getCaseResponse()
            val sspList = caseResponse.modules.selfServicePhoto?.records ?: emptyList()
            val sspPath = caseResponse.modules.selfServicePhoto?.postRecords?.target
            val sspValid = caseResponse.modules.selfServicePhoto?.isValid() ?: false && sspList.isNotEmpty() && sspPath.isNullOrEmpty().not()

            videoManager.startAttempt()

            emmiReporter.send(LogEvent.CR_SSP_MODULES_CHECK_RESULT, eventStatus = if (sspValid) EventStatus.SUCCESS else EventStatus.ERROR)
            emmiReporter.send(LogEvent.CR_USA_MODULES_CHECK_RESULT, eventStatus = if (caseResponse.modules.userSelfAssessment?.isValid() == true) EventStatus.SUCCESS else EventStatus.ERROR)

            if (sspValid && isFirstTry) {
                showToolbar(false)
                showFragment(OCRFragment.newInstance(sspList, checkNotNull(sspPath)))
            } else {
                showToolbar(true)
                showFragment(VideoIdentFragment.newInstance())
            }
        } catch (err: Throwable) {
            log("starting video process failed", err)
            if (isFinishing.not()) showAlertDialog(this, LocalizedStrings.getString(R.string.err_dialog_technical_error)) { finish() }
        }
    }

    fun ocrFragmentFinished() {
        val usaModule = getCaseResponse().modules.userSelfAssessment
        val usaValid = usaModule?.isValid() ?: false
        if (usaValid && usaModule != null) {
            showFragment(OCRUSAFragment.newInstance(usaModule))
        } else {
            usaFragmentFinished()
        }
        showToolbar(true)
    }

    fun usaFragmentFinished() {
        showFragment(VideoIdentFragment.newInstance())
    }

    fun showVideochatAbortedFragment(reason: AbortReason) {
        showFragment(VideochatAbortedFragment.newInstance(reason))
    }

    fun showVideochatSuccessFragment() {
        showFragment(VideochatSuccessFragment.newInstance())
    }

    override fun onBackPressed() {
        when (videoManager.currentState.value) {
            VideoState.MAKEUP_ROOM,
            VideoState.CANCELED_VERIFY_PROCESS,
            VideoState.CALL_ENDED_BY_USER,
            VideoState.CALL_ENDED_BY_AGENT,
            null -> super.onBackPressed()
            else -> handleCloseChat()
        }
    }

    private fun handleCloseChat() {
        when (videoManager.currentState.value) {
            VideoState.END_CHAT -> videoManager.endCall()
            VideoState.CALL_ENDED_SUCCESSFULLY -> finishSdkWithSuccess()
            else -> {
                showChoiceDialog(
                        context = this,
                        title = LocalizedStrings.getString("process_cancel_dialog_title"),
                        msg = LocalizedStrings.getString("dialog_cancel_call_message"),
                        positiveButton = LocalizedStrings.getString("default_btn_yes"),
                        onPositive = {
                            videoManager.endCall(true)
                            showVideochatAbortedFragment(AbortReason.ENDED_BY_USER)
                        },
                        negativeButton = LocalizedStrings.getString("default_btn_no")
                )
            }
        }
    }

    fun finishVideochatWithSuccess() {
        videoManager.endCall()
        val customerFeedbackModule = getCaseResponse().modules.customerFeedback
        if (customerFeedbackModule != null) {
            showFragment(FeedbackFragment.newInstance(customerFeedbackModule, videoManager.novomindChatId))
        } else {
            finishSdkWithSuccess()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        setResult(resultCode, data)

        if (resultCode == SdkResultCodes.RESULT_OK.id && requestCode == WebviewActivity.WEBVIEW_ACTIVITY_REQUEST) {
            finishSdkWithSuccess()
        }
    }
}
