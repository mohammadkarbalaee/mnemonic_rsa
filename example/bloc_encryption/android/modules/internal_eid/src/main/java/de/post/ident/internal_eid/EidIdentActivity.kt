package de.post.ident.internal_eid

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.RemoteException
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.feedback.FeedbackFragment
import de.post.ident.internal_core.rest.IdentMethodDTO
import de.post.ident.internal_core.process_description.ProcessDescriptionFragment
import de.post.ident.internal_core.reporting.IdentMethod
import de.post.ident.internal_core.permission.PermissionFragmentParameter
import de.post.ident.internal_core.permission.PermissionHelpData
import de.post.ident.internal_core.permission.UserPermission
import de.post.ident.internal_core.start.BaseModuleActivity
import de.post.ident.internal_core.start.ModuleMetaData
import de.post.ident.internal_core.util.*
import kotlinx.coroutines.launch

class EidIdentActivity : BaseModuleActivity() {

    private var _eidManager: EidManager? = null
    private val eidManager: EidManager get() = checkNotNull(_eidManager)
    private var isReading = false
    fun getIsReadingNfc(): Boolean = isReading
    fun setIsReadingNfc(isReadingNfc: Boolean) { isReading = isReadingNfc }

    override val moduleMetaData: ModuleMetaData by lazy {
        val permissionList = mutableListOf(UserPermission.NFC)
        val drawableList = arrayListOf(R.drawable.pi_pd_eid_card_logo, R.drawable.pi_pd_eid_pin, R.drawable.pi_pd_eid_nfc)
        val titleList = checkNotNull(getCaseResponse().modules.processDescription?.subtitle)

        val pageDataList: List<ProcessDescriptionFragment.PageData> = titleList.mapIndexedNotNull { index, title ->
            if (index < drawableList.size) {
                ProcessDescriptionFragment.PageData(title, drawableList[index])
            } else {
                null
            }
        }

        ModuleMetaData(
                PermissionFragmentParameter(IdentMethod.EID, permissionList, PermissionHelpData(LocalizedStrings.getString("eid_nfc_description_title"), LocalizedStrings.getString("eid_nfc_description_subtitle"))),
                ProcessDescriptionFragment.ProcessDescriptionData(getCaseResponse(), pageDataList, IdentMethodDTO.EID)
        )
    }

    //currently not used -> eID requires no runtime permissions
    override fun permissionsGranted() { lifecycleScope.launch { sendTermsAccepted() }}

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            EidManager.init(getCaseResponse(), this)
        } catch (e: Exception) {
            log("eid manager init failed, finishing activity", e)
            finish()
            return
        } finally {
            super.onCreate(savedInstanceState)
        }
        _eidManager = EidManager.instance()


        initContinueButton(viewBinding.btnContinueStandard) {
            eidManager.initAusweisApp2Sdk(isNewAttempt = true, isPinChange = true)
            showFragment(EidFragment())
        }
        initContinueButton(viewBinding.btnContinueAlternative) {
            eidManager.initAusweisApp2Sdk(isNewAttempt = true, isPinChange = false)
            showFragment(EidFragment())
        }

        if (CoreConfig.isInitialized) {
            viewBinding.btnContinueStandard.text = LocalizedStrings.getString("eid_button_5_digits")
            viewBinding.btnContinueAlternative.text = LocalizedStrings.getString("eid_button_6_digits")
        }
        viewBinding.btnContinueAlternative.isVisible = true
    }

    override fun onDestroy() {
        super.onDestroy()
        eidManager.destroy()
    }

    override fun onResume() {
        super.onResume()
        eidManager.enableNfcDispatcher()
    }

    override fun onPause() {
        eidManager.disableNfcDispatcher()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?.let { tag ->
            launch {
                try {
                    log("handleIntent: $tag")
                    eidManager.updateNfcTag(tag)
                } catch (err: RemoteException) {
                    log("error handling nfc tag: $err")
                }
            }
        }
    }

    fun finishEidWithSuccess() {
        val customerFeedbackModule = getCaseResponse().modules.customerFeedback
        if (customerFeedbackModule != null) {
            showFragment(FeedbackFragment.newInstance(customerFeedbackModule))
        } else {
            finishSdkWithSuccess()
        }
    }

    override fun onBackPressed() {
        when {
            eidManager.attemptId.isNullOrEmpty() -> { // process description
                super.onBackPressed()
            }
            eidManager.currentStatus == EidManager.CurrentStatus.SUCCESS -> { // eID success screen
                finishEidWithSuccess()
            }
            eidManager.currentStatus == EidManager.CurrentStatus.ERROR -> { // eID error screen
                super.onBackPressed()
            }
            else -> {
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
        }
    }
}