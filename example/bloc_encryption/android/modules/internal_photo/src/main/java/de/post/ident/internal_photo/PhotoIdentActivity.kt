package de.post.ident.internal_photo

import android.os.Bundle
import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.feedback.FeedbackFragment
import de.post.ident.internal_core.process_description.ProcessDescriptionFragment
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.permission.PermissionFragmentParameter
import de.post.ident.internal_core.permission.UserPermission
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.ModuleMetaData
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import de.post.ident.internal_core.util.showAlertDialog
import de.post.ident.internal_core.util.showChoiceDialog
import kotlinx.coroutines.launch
import java.net.HttpURLConnection

@Keep
enum class Screen {
    CAMERA, PREVIEW, OVERVIEW, UPLOAD_STATUS, UPLOAD_UI
}

class PhotoIdentActivity : BaseModuleActivity() {

    private val _currentScreen = MutableLiveData<Screen>()
    val currentScreen: LiveData<Screen> get() = _currentScreen
    private var showFeedback = false
    private val emmiService = CoreEmmiService

    fun updateScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    override val moduleMetaData: ModuleMetaData by lazy {
        val permissionList = mutableListOf(UserPermission.CAMERA, UserPermission.MICROPHONE)
        val drawableList = arrayListOf(R.drawable.pi_pd_photo, R.drawable.pi_pd_photo_portrait, R.drawable.pi_pd_send)
        val titleList = checkNotNull(getCaseResponse().modules.processDescription?.subtitle)

        val pageDataList: List<ProcessDescriptionFragment.PageData> =
            titleList.mapIndexedNotNull { index, title ->
                if (index < drawableList.size) {
                    ProcessDescriptionFragment.PageData(title, drawableList[index])
                } else {
                    null
                }
            }

        ModuleMetaData(
            PermissionFragmentParameter(IdentMethod.PHOTO, permissionList, null),
            ProcessDescriptionFragment.ProcessDescriptionData(getProcessDescriptionData(), pageDataList, IdentMethodDTO.PHOTO)
        )
    }

    override fun permissionsGranted() {
        lifecycleScope.launch {
            sendTermsAccepted()
            showFragment(PhotoIdentFragment.newInstance(getCaseResponse()))
        }
        hideContinueButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initContinueButton(viewBinding.btnContinueStandard) { showFragment(PhotoIdentFragment.newInstance(getCaseResponse())) }
        showFeedback = true
    }

    override fun onBackPressed() {
        when (currentScreen.value) {
            Screen.OVERVIEW -> showCancelDialog()
            Screen.PREVIEW -> updateScreen(Screen.CAMERA)
            Screen.CAMERA -> updateScreen(Screen.OVERVIEW)
            Screen.UPLOAD_UI -> {}
            Screen.UPLOAD_STATUS -> super.onBackPressed()
            null -> super.onBackPressed()
        }
    }

    fun updateCaseResponse() {
        lifecycleScope.launch {
            try {
                val caseResponse =
                    CoreEmmiService.getPhotoIdentInformationByCaseId(getCaseResponse().caseId)
                showFragment(PhotoIdentFragment.newInstance(caseResponse))
            } catch (err: Throwable) {
                if (err is HttpException) {
                    when (err.code) {
                        HttpURLConnection.HTTP_GONE -> {
                            showAlertDialog(
                                this@PhotoIdentActivity, LocalizedStrings.getString("case_id_gone")
                            ) { finishSdkWithSuccess() }
                        }
                        else -> log("Error occurred during refresh in upload status", err)
                    }
                } else {
                    log("Error occurred during refresh in upload status", err)
                }
            }
        }
    }

    private fun showCancelDialog() {
        showChoiceDialog(
            context = this,
            title = LocalizedStrings.getString("process_cancel_dialog_title"),
            msg = LocalizedStrings.getString("cancel_message_default"),
            positiveButton = LocalizedStrings.getString("default_btn_yes"),
            onPositive = {
                super.onBackPressed()
            },
            negativeButton = LocalizedStrings.getString("default_btn_no")
        )
    }

    fun finishPhotoWithSuccess() {
        log("photoIdent with success")
        val customerFeedbackModule = getCaseResponse().modules.customerFeedback
        if (customerFeedbackModule != null && showFeedback) {
            showFragment(FeedbackFragment.newInstance(customerFeedbackModule))
        } else {
            finishSdkWithSuccess()
        }
    }
}