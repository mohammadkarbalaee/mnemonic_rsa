package de.post.ident.internal_core.start

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import de.post.ident.internal_core.*
import de.post.ident.internal_core.databinding.PiBaseMainActivityBinding
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.EventStatus
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.start.IdentMethodClassMapping.Companion.toClassMapping
import de.post.ident.internal_core.util.*
import kotlinx.coroutines.ensureActive

fun ContactDataFragment.finished() {
    (activity as? StartActivity)?.loadCaseId()
}

fun MethodSelectionFragment.contactDataMissing(contactData: ContactDataDTO) {
    (activity as? StartActivity)?.showContactDataFragment(contactData)
}

class StartActivity : PiBaseActivity() {

    private val emmiService = CoreEmmiService
    private val emmiReporter = EmmiCoreReporter

    private lateinit var viewBinding: PiBaseMainActivityBinding

    companion object {
        fun startForResult(act: Activity, requestCode: Int, caseId: String) {
            Commons.caseId = caseId
            val intent = Intent(act, StartActivity::class.java)
            act.startActivityForResult(intent, requestCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CoreConfig.isInitialized.not()) {
            logResultCode(SdkResultCodes.RESULT_TECHNICAL_ERROR)
            setResult(SdkResultCodes.RESULT_TECHNICAL_ERROR.id)
            finish()
            return
        }
        viewBinding = PiBaseMainActivityBinding.inflate(layoutInflater)

        setContentView(viewBinding.root)
        viewBinding.piLoadingSpinnerSubtitle.text = LocalizedStrings.getString(R.string.init_sdk_loading)

        showBackButton(true) { finish() }

        Commons.showEidAccessRightsScreen = true

        loadCaseId()

        if (BuildConfig.DEBUG) {
            Commons.caseId?.let { checkForDebugResultCodes(it) }
            Toast.makeText(this, "You are using a debug version of the POSTIDENT SDK. Please do not release!", Toast.LENGTH_LONG).show()
        }
    }

    internal fun loadCaseId() {
        lifecycleScope.launchWhenStarted {
            try {
                showLoadingSpinner(true)
                val appConfig = emmiService.appConfig()
                log("Success: $appConfig")
                LocalizedStrings.init(checkNotNull(appConfig.texts))
                CoreConfig.appConfig = appConfig

                if (arePreconditionsMet(appConfig).not()) {
                    return@launchWhenStarted
                }

                AppUpdateService().resumeUpdate(applicationContext)

                val caseInfo = CoreEmmiService.getCaseInformationByCaseId(requireNotNull(Commons.caseId))
                BasicCaseManager.setCaseresponse(caseInfo)
                CoreEmmiService.sendUserStarts(requireNotNull(Commons.caseId))

                Commons.clientId = caseInfo.clientId

                val identMethods = caseInfo.modules.identMethodSelection?.identMethodInfos?.filter {
                    isIdentActivityAvailable(it.method.toClassMapping())
                }
                identMethods?.size.let {
                    if (it != null) Commons.identMethodsAvailable = it
                }
                if (identMethods?.size == 0) throw NoSuitableIdentMethodAvailable()
                val identMethod = identMethods?.first()

                // if there is only one method and this method is not disabled, jump to the method directly
                if (identMethod != null && identMethods.size == 1 && identMethod.enabled) {
                    if (identMethod.method == IdentMethodDTO.VIDEO && Build.VERSION.SDK_INT < 24 && CoreConfig.isSdk) throw VideochatRequirementsError()
                    if (identMethod.method == IdentMethodDTO.EID && Build.VERSION.SDK_INT < 24 && CoreConfig.isSdk) throw EidRequirementsError()
                    IdentMethodStart(this@StartActivity,
                            onContactDataMissing = { showContactDataFragment(it) },
                            onError = { finish() })
                            .startIdentMethod(identMethod, caseInfo)
                    Commons.skipMethodSelection = true
                } else {
                    Commons.skipMethodSelection = false
                    showMethodSelectionFragment(caseInfo)
                }
            } catch (err: Throwable) {
                log(err)
                ensureActive()
                when (err) {
                    is CaseResponseMissingData -> showContactDataFragment(err.contactData)
                    is CaseResponseError -> {
                        if (err.resultCode == SdkResultCodes.ERROR_CASE_DONE) {
                            BasicCaseManager.deleteOfflineData()
                        }
                        showErrorAlertAndFinish(err.userMessage, err.resultCode)
                    }
                    is SigningRedirectEvent -> {
                        if (CoreConfig.isSdk.not() && err.url.isBlank().not()) {
                            WebviewActivity.start(this@StartActivity, err.url, true)
                        } else {
                            generalErrorHandling(GeneralError(LocalizedStrings.getString(R.string.err_dialog_technical_error)))
                        }
                    }
                    is NoSuitableIdentMethodAvailable,
                    is VideochatRequirementsError -> showPostidentDeeplinkDialog(SdkResultCodes.RESULT_METHOD_NOT_AVAILABLE)
                    is EidRequirementsError -> showPostidentDeeplinkDialog(SdkResultCodes.RESULT_METHOD_NOT_AVAILABLE)
                    is GeneralError -> generalErrorHandling(err)
                    else -> generalErrorHandling(GeneralError(LocalizedStrings.getString(R.string.err_dialog_technical_error)))
                }
            } finally {
                ensureActive()
                showLoadingSpinner(false)
            }
        }
    }

    private fun arePreconditionsMet(appConfig: AppConfigDTO): Boolean {
        if (checkForRoot(applicationContext)) {
            showErrorAlertAndFinish(LocalizedStrings.getString("err_device_rooted"), SdkResultCodes.ERROR_ROOT_DETECTED)
            return false
        }

        val caseIdRegex = "[A-Z0-9]{12}".toRegex()
        val caseId = Commons.caseId
        if (caseId?.length != 12 || caseIdRegex.matches(caseId).not()) {
            showErrorAlertAndFinish(LocalizedStrings.getString(R.string.err_dialog_technical_error), SdkResultCodes.ERROR_CASE_INVALID)
            return false
        }

        if (appConfig.forceUpdateApp) {
            // start Postident app via deeplink in case App is not allowed to function
            showPostidentDeeplinkDialog(SdkResultCodes.ERROR_SDK_UPDATE)
            emmiReporter.send(LogEvent.CE_FORCE_UPDATE, eventContext = mapOf("isSdk" to "false"))
            return false
        }

        if (appConfig.forceUpdateSdk) {
            // start Postident app via deeplink in case SDK is not allowed to function
            showPostidentDeeplinkDialog(SdkResultCodes.ERROR_SDK_UPDATE)
            emmiReporter.send(LogEvent.CE_FORCE_UPDATE, eventContext = mapOf("isSdk" to "true"))
            return false
        }

        if (Build.VERSION.SDK_INT < appConfig.minimumSupportedOsVersion) {
            showErrorAlertAndFinish(LocalizedStrings.getString("err_unsupported_sdk_version"), SdkResultCodes.ERROR_OS_VERSION)
            emmiReporter.send(LogEvent.CE_FORCE_UPDATE_SDK)
            return false
        }

        return true
    }

    private fun generalErrorHandling(err: GeneralError) {
        when (err.type) {
            GeneralError.Type.NO_CONNECTION -> showErrorAlertAndFinish(err.userMessage, SdkResultCodes.ERROR_OFFLINE)
            GeneralError.Type.SERVER_ERROR -> showErrorAlertAndFinish(err.userMessage, SdkResultCodes.ERROR_SERVER_CONNECTION)
            GeneralError.Type.SSL_ERROR -> showPostidentDeeplinkDialog(SdkResultCodes.ERROR_SSL_PINNING)
            GeneralError.Type.INTERFACE_KEY -> showPostidentDeeplinkDialog(SdkResultCodes.ERROR_WRONG_MOBILE_SDK_API_KEY)
            else -> showErrorAlertAndFinish(err.userMessage, SdkResultCodes.RESULT_TECHNICAL_ERROR)
        }
    }

    fun showPostidentDeeplinkDialog(resultCode: SdkResultCodes, allowBackToMethodSelection: Boolean = false) {
        showErrorMessageDialog(LocalizedStrings.getString(
                if (CoreConfig.isSdk) R.string.err_new_version_available_sdk else R.string.err_new_version_available), resultCode, true, allowBackToMethodSelection)
    }

    internal fun showMethodForceUpdateDialog() {
        showDialogFragment(FragmentError.newInstance(
            FragmentError.ErrorMessageData(
                LocalizedStrings.getString("app_required"),
                LocalizedStrings.getString("err_module_update"),
                resultCode = SdkResultCodes.RESULT_METHOD_NOT_AVAILABLE,
                isDeeplink = true,
                allowBackToMethodSelection = true
            ),
            onBackClicked = { /* dismiss */ },
            onContinueClicked = {
                setResult(SdkResultCodes.RESULT_METHOD_NOT_AVAILABLE.id)
                logResultCode(SdkResultCodes.RESULT_METHOD_NOT_AVAILABLE)
                deeplinkPostidentApp()
            }
        ))
    }

    internal fun showAppUpdateDialog() {
        showDialogFragment(FragmentError.newInstance(
            FragmentError.ErrorMessageData(
                LocalizedStrings.getString("app_update_required"),
                LocalizedStrings.getString("err_inapp_update_available"),
                resultCode = SdkResultCodes.ERROR_SSL_PINNING,
                isDeeplink = false,
                allowBackToMethodSelection = true
            ),
            onBackClicked = {  },
            onContinueClicked = {
                logResultCode(SdkResultCodes.ERROR_SSL_PINNING)
                AppUpdateService().startAppUpdate()
            }
        ))
    }

    private fun deeplinkPostidentApp() {
        val piAppIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://postident.deutschepost.de/apps?v=${Commons.caseId}"))
        startActivity(piAppIntent)
        finish()
    }

    private fun showErrorAlertAndFinish(message: String, resultCode: SdkResultCodes) {
        showErrorMessageDialog(message, resultCode, false)
    }

    internal fun showContactDataFragment(contactData: ContactDataDTO) {
        val fragment = ContactDataFragment.newInstance(contactData)
        log("contact data: $contactData")
        emmiReporter.send(LogEvent.MR_CASE_CHECK_RESULT, eventStatus = EventStatus.FAILURE, message = "Missing contact data! Show user contact data form")
        showFragment(fragment)
    }

    private fun showMethodSelectionFragment(caseResponseDTO: CaseResponseDTO) {
        val fragment = MethodSelectionFragment.newInstance(caseResponseDTO)
        log("method selection: $caseResponseDTO")
        showFragment(fragment)
    }

    private fun showErrorMessageDialog(message: String, resultCode: SdkResultCodes, isDeeplink: Boolean, allowBackToMethodSelection: Boolean = false) {
        setResult(resultCode.id)

        showDialogFragment(FragmentError.newInstance(
                FragmentError.ErrorMessageData(
                        LocalizedStrings.getString(R.string.general_identification_not_possible),
                        message, resultCode, isDeeplink, allowBackToMethodSelection),
                onBackClicked = { if (allowBackToMethodSelection) { logResultCode(resultCode) } else finish() },
                onContinueClicked = { deeplinkPostidentApp() }
        ))
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit(true) {
            replace(viewBinding.piContent.id, fragment)
        }
    }

    private fun showDialogFragment(fragment: DialogFragment) {
        fragment.show(supportFragmentManager, fragment.tag)
    }

    private fun showLoadingSpinner(show: Boolean) {
        viewBinding.piContent.isVisible = show.not()
        viewBinding.piLoadingSpinnerContainer.isVisible = show
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        EmmiCoreReporter.flush()
        setResult(resultCode, data)

        if (resultCode == SdkResultCodes.RESULT_OK.id || Commons.skipMethodSelection) {
            finish()
        }
    }

    private fun logResultCode(resultCode: SdkResultCodes) {
        val eventContext = mapOf(
            "sdkResultCode" to resultCode.name,
            "sdkResultCodeId" to resultCode.id.toString()
        )
        emmiReporter.send(logEvent = LogEvent.SD_RESULT_CODE, eventContext = eventContext)
        emmiReporter.flush()
    }

    /**
     * This method can be used for testing the SDK result codes.
     * If the caseID is a valid SDK result code (Int, see enum class SdkResultCodes), the SDK exits immediately using this code as a result.
     * ---> DEBUG ONLY <----
     */
    private fun checkForDebugResultCodes(caseId: String) {
        if (SdkResultCodes.idMap.containsKey(caseId.toIntOrNull())) {
            log("Debug result code found: ${caseId.toInt()}. SDK is shutting down.")
            setResult(caseId.toInt())
            finish()
        }
    }
}