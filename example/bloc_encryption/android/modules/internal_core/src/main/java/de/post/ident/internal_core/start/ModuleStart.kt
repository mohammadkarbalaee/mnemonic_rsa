package de.post.ident.internal_core.start

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.R
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.databinding.PiActivityBaseIdentBinding
import de.post.ident.internal_core.process_description.ProcessDescriptionFragment
import de.post.ident.internal_core.permission.PermissionFragment
import de.post.ident.internal_core.permission.PermissionFragmentParameter
import de.post.ident.internal_core.permission.PermissionUtil
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.util.*
import de.post.ident.internal_core.util.ui.MaterialButtonLoadingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

abstract class BaseModuleActivity: PiBaseActivity(), CoroutineScope {
    companion object {
        private val CASE_IDENTMETHOD_PARAMETER: ExtraParameter<IdentMethodInfoDTO> = ExtraParameter.moshi(CoreEmmiService.moshi, "CASE_IDENTMETHOD")
        val CASE_RESPONSE_PARAMETER: ExtraParameter<CaseResponseDTO> = ExtraParameter.moshi(CoreEmmiService.moshi, "CASE_RESPONSE")

        fun start(act: Activity, intent: Intent, caseResponse: CaseResponseDTO, identMethod: IdentMethodInfoDTO ) {
            intent.putParameter(CASE_RESPONSE_PARAMETER, caseResponse)
            intent.putParameter(CASE_IDENTMETHOD_PARAMETER, identMethod)
            act.startActivityForResult(intent, 0)
        }
    }

    override val coroutineContext = lifecycleScope.coroutineContext

    private val emmiService: CoreEmmiService = CoreEmmiService
    abstract val moduleMetaData: ModuleMetaData
    protected lateinit var viewBinding: PiActivityBaseIdentBinding

    private val buttons = mutableListOf<MaterialButton>()
    private var callbackUrl: CallbackUrlDTO? = null
    private lateinit var statusCode: StatusCodeDTO
    private lateinit var target: String

    abstract fun permissionsGranted()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = PiActivityBaseIdentBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        buttons.clear()

        if (CoreConfig.isInitialized) {
            try {
                moduleMetaData.processDescriptionData?.let {
                    val fragment = ProcessDescriptionFragment.newInstance(it.caseResponse, it.pageDataList, it.method)
                    log("method selection: ${it.caseResponse}")
                    showFragment(fragment)
                }
            } catch (e: Throwable) {
                log("could not start process description")
                Toast.makeText(CoreConfig.appContext, LocalizedStrings.getString(R.string.err_dialog_technical_error), Toast.LENGTH_SHORT).show()
                finishWithTechnicalError()
            }
        } else {
            log("core config not initialized!")
            finishWithTechnicalError()
        }
    }

    fun finishSdkWithSuccess() {
        Commons.caseId = null
        val resultIntent = Intent()
        resultIntent.putExtra("appUrl", callbackUrl?.appUrl)
        resultIntent.putExtra("webUrl", callbackUrl?.webUrl)
        setResult(SdkResultCodes.RESULT_OK.id, resultIntent)
        finish()
    }

    fun finishSdkWithCancelledByUser() {
        val resultIntent = Intent()
        setResult(SdkResultCodes.RESULT_CANCELLED.id, resultIntent)
        finish()
    }

    private fun finishWithTechnicalError() {
        setResult(SdkResultCodes.RESULT_TECHNICAL_ERROR.id)
        finish()
    }

    fun getProcessDescriptionData(): CaseResponseDTO = checkNotNull(intent.getParameter(CASE_RESPONSE_PARAMETER))
    fun getCaseResponse(): CaseResponseDTO = BasicCaseManager.getCaseresponse() ?: checkNotNull(intent.getParameter(CASE_RESPONSE_PARAMETER))
    fun setCallbackUrl(callbackUrl: CallbackUrlDTO?) { this.callbackUrl = callbackUrl }

    protected fun initContinueButton(materialButton: MaterialButton, onClicked: suspend () -> Unit) {
        buttons.add(materialButton)
        materialButton.let { continueBtn ->
            continueBtn.text = getCaseResponse().modules.processDescription?.continueButton?.text
            val legalInfo = getCaseResponse().modules.processDescription?.legalInfo

            val continueButtonController = MaterialButtonLoadingController(this, materialButton)
            continueBtn.setOnClickListener {
                continueButtonController.loadingAnimation(true)
                buttons.forEach { it.isEnabled = false }
                lifecycleScope.launch {
                    try {
                        target = checkNotNull(legalInfo?.acceptTerms?.target)

                        val target = intent.getParameter(CASE_IDENTMETHOD_PARAMETER)?.continueButton?.target
                        if (target != null) {
                            val caseResponse = emmiService.getCaseInformationByPath(checkNotNull(intent.getParameter(CASE_IDENTMETHOD_PARAMETER)?.continueButton?.target))
                            BasicCaseManager.setCaseresponse(caseResponse)
                            caseResponse.modules.identStatus?.callbackUrl.let {
                                if (it != null) {
                                    callbackUrl = it
                                }
                            }
                            caseResponse.caseStatus?.statusCode.let {
                                if (it != null) {
                                    statusCode = it
                                }
                            }
                        }

                        if (PermissionUtil.hasPermissions(
                                this@BaseModuleActivity,
                                moduleMetaData.permissionParameters.permissionList.map { it.androidPermission })
                        ) {
                            // needed for AutoId - terms later accepted with data consent dialog
                            if (!intent.getParameter(CASE_IDENTMETHOD_PARAMETER)?.method?.name.equals(IdentMethodDTO.AUTOID.toString(),true)) {
                                sendTermsAccepted()
                            }
                            onClicked()
                            hideContinueButtons()
                        } else {
                            showPermissionFragment()
                        }
                    } catch(e: Throwable) {
                        log("error getting case information and contiuing to ident")
                        setResult(SdkResultCodes.RESULT_TECHNICAL_ERROR.id)
                        finish()
                    } finally {
                        ensureActive()
                        continueButtonController.loadingAnimation(false)
                        buttons.forEach { it.isEnabled = true }
                    }
                }
            }
        }
    }

    protected fun loadingContinueButton(materialButton: MaterialButton) {
        buttons.add(materialButton)
        materialButton.let {
            val continueButtonController = MaterialButtonLoadingController(this, materialButton)
            continueButtonController.loadingAnimation(true)
        }
    }

    protected fun hideContinueButtons(continueBtnVisibility: Int = View.GONE, continueAlternativeVisibility: Int = View.GONE) {
        viewBinding.btnContinueStandard.visibility = continueBtnVisibility
        viewBinding.btnContinueAlternative.visibility = continueAlternativeVisibility
    }

    private fun showPermissionFragment() {
        PermissionFragment.newInstance(
                moduleMetaData.permissionParameters.identMethod,
                moduleMetaData.permissionParameters.permissionList,
                moduleMetaData.permissionParameters.helpData
        ).show(supportFragmentManager, "PermissionFragment")
    }

    protected suspend fun sendTermsAccepted() {
        try {
            when (intent.getParameter(CASE_IDENTMETHOD_PARAMETER)?.method?.name) {
                IdentMethodDTO.PHOTO.toString() -> {
                    statusCode.let {
                        if (it == StatusCodeDTO.NEU) {
                            emmiService.sendTermsAccepted(target)
                        }
                    }
                }
                else -> {
                    emmiService.sendTermsAccepted(target)
                }
            }
        } catch (err: Throwable) {
            showAlertDialog(this, err.getUserMessage())
        }
    }

    protected fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit(true) {
            replace(viewBinding.piContent.id, fragment)
        }
    }
}

data class ModuleMetaData(
        val permissionParameters: PermissionFragmentParameter,
        val processDescriptionData: ProcessDescriptionFragment.ProcessDescriptionData?
)
@Keep
enum class IdentMethodClassMapping(val className: String, val identMethod: IdentMethodDTO) {
    VIDEO("de.post.ident.internal_video.ui.VideoIdentActivity", IdentMethodDTO.VIDEO),
    BASIC("de.post.ident.internal_basic.BasicIdentActivity", IdentMethodDTO.BASIC),
    EID("de.post.ident.internal_eid.EidIdentActivity", IdentMethodDTO.EID),
    PHOTO("de.post.ident.internal_photo.PhotoIdentActivity", IdentMethodDTO.PHOTO),
    AUTOID("de.post.ident.internal_autoid.AutoIdentActivity", IdentMethodDTO.AUTOID);

    companion object {
        private val dtoToClassMap = values().associateBy { it.identMethod }
        fun IdentMethodDTO.toClassMapping(): IdentMethodClassMapping = checkNotNull(dtoToClassMap[this])
    }
}

internal fun Activity.startIdentActivity(method: IdentMethodClassMapping, caseResponse: CaseResponseDTO, identMethod: IdentMethodInfoDTO) {
    val intent = Intent()
    intent.setClassName(this, method.className)
    if (intent.resolveActivityInfo(packageManager, 0) != null) {
        log("Start activity: ${method.className} with data: $caseResponse")

        BaseModuleActivity.start(this, intent, caseResponse, identMethod)
    } else {
        log("No activity found, should deep link it")
    }
}

internal fun Context.isIdentActivityAvailable(method: IdentMethodClassMapping): Boolean {
    val intent = Intent()
    intent.setClassName(applicationContext, method.className)
    return intent.resolveActivityInfo(packageManager, 0) != null
}