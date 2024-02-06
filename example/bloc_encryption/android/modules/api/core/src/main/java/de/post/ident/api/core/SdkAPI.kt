package de.post.ident.api.core

import android.app.Activity
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.Keep
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.SdkResultCodes
import de.post.ident.internal_core.start.StartActivity
import de.post.ident.internal_core.util.log

data class PinnedDomain(val domain: String, val certificateFingerPrint: String)

object PostidentSdk {
    fun start(activity: Activity,
              requestCode: Int,
              caseId: String,
              mobileSdkApiKey: String,
              middlewareUrl: String? = null,
              mlServerUrl: String? = null,
              pinnedDomains: List<PinnedDomain> = emptyList(),
              useTestEnvironment: Boolean? = false,
    ) {
        val appName = activity.packageName
        val corePinnedDomains = pinnedDomains.map { de.post.ident.internal_core.rest.PinnedDomain(it.domain, it.certificateFingerPrint) }
        var hostAppVersion: String? = null

        try {
            val pInfo: PackageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            hostAppVersion = pInfo.versionName
        } catch (err: PackageManager.NameNotFoundException) {
            log("host app version could not be parsed", err)
        }

        CoreConfig.init(
                context = activity,
                hostAppId = appName,
                emmiUrl = middlewareUrl,
                mlServerUrl = mlServerUrl,
                interfaceKey = mobileSdkApiKey,
                pinnedDomains = corePinnedDomains,
                hostAppVersion = hostAppVersion,
                isSdkOnly = appName.contains("de.deutschepost.postident").not(),
                useTestEnvironment = useTestEnvironment
        )

        StartActivity.startForResult(activity, requestCode, caseId)
    }

    fun checkForRoot(context: Context) = de.post.ident.internal_core.util.checkForRoot(context)

    fun getVersionName() : String = BuildConfig.MODULE_VERSION_NAME

    fun getVersionCode() : Int = BuildConfig.MODULE_VERSION_CODE

    class Builder(private val activity: Activity) {
        private var requestCode: Int? = null
        private var caseId: String? = null
        private var mobileSdkApiKey: String? = null
        private var middlewareUrl: String? = null
        private var mlServerUrl: String? = null
        private var pinnedDomains: List<PinnedDomain> = emptyList()

        fun requestCode(code: Int): Builder = apply { requestCode = code }
        fun caseId(id: String): Builder = apply { caseId = id }
        fun mobileSdkApiKey(key: String): Builder = apply { mobileSdkApiKey = key }
        fun middlewareUrl(url: String): Builder = apply { middlewareUrl = url }
        fun pinnedDomains(pinned: List<PinnedDomain>): Builder = apply { pinnedDomains = pinned }

        fun start() {
            start(
                    activity = activity,
                    requestCode = requireNotNull(requestCode),
                    caseId = requireNotNull(caseId),
                    mobileSdkApiKey = requireNotNull(mobileSdkApiKey),
                    middlewareUrl = middlewareUrl,
                    mlServerUrl = mlServerUrl,
                    pinnedDomains = pinnedDomains
            )
        }
    }

    //needs to be duplicated because access to the internal enum is restricted!
    @Keep
    enum class ResultCodes {
        RESULT_OK,
        RESULT_CANCELLED,
        RESULT_METHOD_NOT_AVAILABLE,
        RESULT_TECHNICAL_ERROR,
        ERROR_SERVER_CONNECTION,
        ERROR_SSL_PINNING,
        ERROR_WRONG_MOBILE_SDK_API_KEY,
        ERROR_SDK_UPDATE,
        ERROR_OS_VERSION,
        ERROR_OFFLINE,
        ERROR_ROOT_DETECTED,
        ERROR_CASE_DONE,
        ERROR_CASE_NOT_FOUND,
        ERROR_CASE_INVALID;
    }

    fun resolveResultCode(resultCode: Int): ResultCodes = when (SdkResultCodes.idMap[resultCode]) {
        SdkResultCodes.RESULT_OK -> ResultCodes.RESULT_OK
        SdkResultCodes.RESULT_CANCELLED -> ResultCodes.RESULT_CANCELLED
        SdkResultCodes.RESULT_METHOD_NOT_AVAILABLE -> ResultCodes.RESULT_METHOD_NOT_AVAILABLE
        SdkResultCodes.RESULT_TECHNICAL_ERROR -> ResultCodes.RESULT_TECHNICAL_ERROR
        SdkResultCodes.ERROR_SERVER_CONNECTION -> ResultCodes.ERROR_SERVER_CONNECTION
        SdkResultCodes.ERROR_SSL_PINNING -> ResultCodes.ERROR_SSL_PINNING
        SdkResultCodes.ERROR_WRONG_MOBILE_SDK_API_KEY -> ResultCodes.ERROR_WRONG_MOBILE_SDK_API_KEY
        SdkResultCodes.ERROR_SDK_UPDATE -> ResultCodes.ERROR_SDK_UPDATE
        SdkResultCodes.ERROR_OS_VERSION -> ResultCodes.ERROR_OS_VERSION
        SdkResultCodes.ERROR_OFFLINE -> ResultCodes.ERROR_OFFLINE
        SdkResultCodes.ERROR_ROOT_DETECTED -> ResultCodes.ERROR_ROOT_DETECTED
        SdkResultCodes.ERROR_CASE_DONE -> ResultCodes.ERROR_CASE_DONE
        SdkResultCodes.ERROR_CASE_NOT_FOUND -> ResultCodes.ERROR_CASE_NOT_FOUND
        SdkResultCodes.ERROR_CASE_INVALID -> ResultCodes.ERROR_CASE_INVALID
        null -> ResultCodes.RESULT_TECHNICAL_ERROR
    }
}
